package Servers;
import java.util.Random;
import Servers.encryption.aes.*;
import Servers.encryption.rot13.*;

public class keepAlive implements Runnable{
    // The Socket used to send the KEEP_ALIVE Message
    private Duplexer duplexer;
    
    // Should the KEEP_ALIVE Message be encrypted with a 13 Character Caesar Cipher
    private boolean encrypt;

    // Should the Message be nested within a HTTP header
    private boolean http;

    // As long as sentinel is true, then this thread will send KEEP_ALIVE every 30 seconds
    private boolean sentinel;

    // sendLock is a synchronization lock that is used to control who can send messages to the client and when.
    private Object sendLock;

    // RNG to randomize time of keep alive messages sent
    private Random randomTimeGenerator;

    private rot13 rot13;
    private aes aes;

    /**
     * Use this constructor to create a new Keep Alive Thread, to ensure that a Socket is kept alive
     * 
     * @param duplexer The socket that is used to send the KEEP_ALIVE Message
     * @param encrypt Is the KEEP_ALIVE Message encrypted using a 13 Character Caesar Cipher
     * @param http Is the Message nested in a HTTP Header
     */
    public keepAlive(Duplexer duplexer, Object sendLock, boolean encrypt, boolean http){
        this.duplexer = duplexer;
        this.encrypt = encrypt;
        this.http = http;
        this.sentinel = true;
        this.sendLock = sendLock;
        this.randomTimeGenerator = new Random();
        this.rot13 = new rot13();
        this.aes = null;
    }

    /**
     * Use this constructor to create a new Keep Alive Thread, to ensure that a Socket is kept alive
     * 
     * @param duplexer The socket that is used to send the KEEP_ALIVE Message
     * @param encrypt Is the KEEP_ALIVE Message encrypted using a 13 Character Caesar Cipher
     * @param http Is the Message nested in a HTTP Header
     */
    public keepAlive(Duplexer duplexer, Object sendLock, boolean encrypt, boolean http, aes aes){
        this.duplexer = duplexer;
        this.encrypt = encrypt;
        this.http = http;
        this.sentinel = true;
        this.sendLock = sendLock;
        this.randomTimeGenerator = new Random();
        this.aes = aes;
        this.rot13 = null;
    }

    public void stopKeepAlive(){
        sentinel = false;
    }

    @Override
    public void run() {
        while(sentinel){
            try{
                double randomTime = 30000.0 + 60000.0 * randomTimeGenerator.nextDouble(0, 1);
                Thread.sleep((long)randomTime);
            } catch (InterruptedException e){
                e.printStackTrace();
            }
            String message = "KEEP_ALIVE";
            if(encrypt){
                if(aes == null){
                    message = rot13.encrypt(message);
                } else {
                    message = aes.encrypt(message);
                }
            }
            if(http){
                message = "HTTP/1.1 200 OK\r\n" +  "Content-Length: " + message.length() + "\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n" + message;
            }
            synchronized(sendLock){
                duplexer.send(message, true);
            }            
        }
    }
    
}
