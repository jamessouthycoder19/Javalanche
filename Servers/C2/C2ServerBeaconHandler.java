package Servers.C2;

import java.io.IOException;
import Servers.Duplexer;
import Servers.keepAlive;

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
    

    /**
     * Creates a new thread to handle each Long Range Beacon
     * 
     * @param duplexer Duplexer to send and receive from the long range beacon
     * @param IP IP address of the long range Beacon that this thread is handling
     * @param server Pointer to the C2 Server
     */
    protected C2ServerBeaconHandler(Duplexer duplexer, String IP, C2Server server){
        this.duplexer = duplexer;
        this.IP = IP;
        this.C2server = server;
        this.sendLock = new Object();
        this.keepAliveClass = new keepAlive(duplexer, sendLock, false, false);
        this.keepAliveThread = new Thread(keepAliveClass);
    }

    /**
     * Sends a message to the Long Range Beacon Server from the C2 Server
     * 
     * @param message
     */
    protected void sendToBeacon(String message){
        synchronized(sendLock){
            duplexer.send(message);
        }
    }

    @Override
    public void run() {
        try {
            boolean sentinel = true;
            keepAliveThread.start();
            while(sentinel){
                String response = duplexer.receive();
                if(!(response.equals("KEEP_ALIVE"))){
                    C2server.outputToUserHandler(response);
                }   
            }
        } catch (IOException e) {
            System.out.println("Lost Beacon Server at " + IP);
            keepAliveClass.stopKeepAlive();
        }
    }
}
