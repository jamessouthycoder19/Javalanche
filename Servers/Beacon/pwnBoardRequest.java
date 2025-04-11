package Servers.Beacon;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class pwnBoardRequest implements Runnable{

    // Variables used to send HTTP Requests to pwnboard. pwnboard is used to keep track of what machines red team still has access to.
    private URL pwnboardUrl = null;
    private HttpURLConnection pwnboardConnection = null;
    private String pwnboardData;
    private Object pwnBoardLock; 


    public pwnBoardRequest(URL pwnboardUrl, HttpURLConnection pwnboardConnection, String pwnboardData, Object pwnBoardLock) {
        this.pwnboardUrl = pwnboardUrl;
        this.pwnboardConnection = pwnboardConnection;
        this.pwnboardData = pwnboardData;
        this.pwnBoardLock = pwnBoardLock;
    }

    @Override
    public void run() {
        while(true){
            synchronized(pwnBoardLock){
                try{
                    pwnBoardLock.wait();
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
                try {
                    // Connect to pwnboard
                    pwnboardConnection = (HttpURLConnection)pwnboardUrl.openConnection();
                    pwnboardConnection.setDoOutput(true);
                    pwnboardConnection.connect();

                    // Send api request
                    DataOutputStream writer = new DataOutputStream(pwnboardConnection.getOutputStream());

                    writer.writeBytes(pwnboardData);
                    writer.close();

                    // Debug lines
                    InputStream in = pwnboardConnection.getInputStream();
                    Scanner scanner = new Scanner(in);

                    while(scanner.hasNext()){
                        scanner.nextLine();
                    }
                    scanner.close();

                    pwnboardConnection.disconnect();
                } catch (IOException e){
                    System.out.println("when sending " + pwnboardData);
                    e.printStackTrace();
                }   
            }
        }
    }
    
}
