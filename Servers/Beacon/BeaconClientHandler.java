package Servers.Beacon;

import Servers.Duplexer;
import java.io.IOException;

public class BeaconClientHandler implements Runnable{
    private String IPAddress;
    private Duplexer duplexer;
    private BeaconServer beaconServer;

    /**
     * Use this Class to create a new thread to handle each victim connection
     * 
     * @param IPAddress IP address of the client (victim)
     * @param duplexer Pointer to the duplexer so that this thread can receive messages from the victim.
     */
    public BeaconClientHandler(String IPAddress, Duplexer duplexer, BeaconServer beaconServer){
        this.IPAddress = IPAddress;
        this.duplexer = duplexer;
        this.beaconServer = beaconServer;
    }

    protected void quit(String reason) throws IOException{
        duplexer.close();
    }

    /**
     * Use this function to have the thread send a message to the client
     * 
     * @param message Message to be sent
     */
    protected void sendToClient(String message){
        duplexer.send(message);
    }

    @Override
    public void run(){
        while(true){
            try{
                String response = duplexer.receive();
                beaconServer.addDataToResponsesDictionaries(IPAddress, response);

            } catch (IOException e){
                e.printStackTrace();
            }
            
        }
    }
}
