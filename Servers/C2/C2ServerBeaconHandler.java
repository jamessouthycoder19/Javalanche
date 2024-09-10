package Servers.C2;

import java.io.IOException;
import Servers.Duplexer;

public class C2ServerBeaconHandler implements Runnable{
    // IP address of the Long Range Becaon that this thread is handling
    public String IP;

    // Duplexer send and receive from the Long Range Beacon
    private Duplexer duplexer;

    // Pointer to the C2 Server
    private C2Server C2server;

    /**
     * Creates a new thread to handle each Long Range Beacon
     * 
     * @param duplexer Duplexer to send and receive from the long range beacon
     * @param IP IP address of the long range Beacon that this thread is handling
     * @param server Pointer to the C2 Server
     */
    public C2ServerBeaconHandler(Duplexer duplexer, String IP, C2Server server){
        this.duplexer = duplexer;
        this.IP = IP;
        this.C2server = server;
    }

    /**
     * Sends a message to the Long Range Beacon Server from the C2 Server
     * 
     * @param message
     */
    protected void sendToBeacon(String message){
        duplexer.send(message);
    }

    @Override
    public void run() {
        try {
            boolean sentinel = true;
            while(sentinel){
                String response = duplexer.receive();

                // TODO add logic to determine what should be send and in what format
                C2server.outputToUserHandler(response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
