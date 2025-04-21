/*
 * This a Duplexer class, that will be used by all of the different servers for simple networking calls
 */
package Servers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Duplexer {
    private final Socket socket;
    private BufferedReader bufferedReader;
    private PrintWriter printWriter;

    public Duplexer(Socket socket) throws IOException{
        this.socket = socket;
        this.socket.setKeepAlive(true);
        this.socket.setSoTimeout(0);
        this.printWriter = new PrintWriter(socket.getOutputStream());
        this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public void send(String message){
        printWriter.println(message);
        printWriter.flush();
    }

    public void send(String message, boolean encrypted){
        if(encrypted){
            printWriter.println(Integer.toString(message.length()));
            printWriter.println(message);
            printWriter.flush();
        }
        
    }

    public String receive() throws IOException {
        String message = bufferedReader.readLine();
        return message;
    }

    public String receive(boolean encrypted) throws IOException {
        String firstNum = bufferedReader.readLine();
        int numChars = Integer.parseInt(firstNum);
        
        String message = "";
        for(int i = 0; i < numChars + 1; i++){
            int character = bufferedReader.read();
            if(character != -1){
                message += (char)character;
            } else {
                break;
            }
        }
        return message;
    }

    public void close() throws IOException{
        bufferedReader.close();
        printWriter.close();
        socket.close();
    }
}
