/*
 * This a Duplexer class, that will be used by all of the different servers for simple networking calls
 */
package Servers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

public class Duplexer {
    private final Socket socket;
    private final BufferedReader bufferedReader;
    private final PrintWriter printWriter;

    public Duplexer(Socket socket) throws IOException{
        this.socket = socket;
        this.socket.setKeepAlive(true);
        this.socket.setSoTimeout(0);
        
        OutputStream out = socket.getOutputStream();
        this.printWriter = new PrintWriter(out);

        InputStream in = socket.getInputStream();
        InputStreamReader ir = new InputStreamReader(in);
        this.bufferedReader = new BufferedReader(ir);
    }

    public void send(String message){
        // System.out.println(">> " + message);
        printWriter.println(message);
        printWriter.flush();
    }

    public String receive()throws IOException{
        String message = bufferedReader.readLine();
        // System.out.println("<< " + message);
        return message;
    }

    public void close() throws IOException{
        bufferedReader.close();
        printWriter.close();
        socket.close();
    }
}
