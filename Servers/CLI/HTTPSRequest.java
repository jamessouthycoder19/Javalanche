package Servers.CLI;

import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class HTTPSRequest {

    // Method to disable SSL verification
    private static void disableSSLVerification() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Disable hostname verification
        HostnameVerifier allHostsValid = (hostname, session) -> true;
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }

    // Method to send request with optional SSL verification and authentication handling
    public static String sendRequest(String urlString, String data, String token, Boolean disableSSL, C2ServerCLI cli) {
        String serverResponse = "";
        if (disableSSL) {
            try {
                disableSSLVerification();
            } catch (Exception e) {
                e.printStackTrace();
                return "Failed to disable SSL verification";
            }
        }

        try {
            URL url = (new URI(urlString)).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (data != null && !data.isEmpty()) {
                // Send POST request
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(data.getBytes());
                    os.flush();
                }
            } else {
                // Send GET request
                connection.setRequestMethod("GET");
            }

            // Read response
            int responseCode = connection.getResponseCode();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode >= 200 && responseCode < 300 ?
                            connection.getInputStream() :
                            connection.getErrorStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                serverResponse = response.toString();
            } finally {
                connection.disconnect();
            }

            // If the token is expired, get a new token
            if (responseCode == 401) {
                token = cli.reAuthenticate();
                serverResponse = sendRequest(urlString, data, token, disableSSL, cli);
            } else if(responseCode == 403){
                token = cli.reAuthenticateNewCreds();
                serverResponse = sendRequest(urlString, data, token, disableSSL, cli);
            }

        } catch (URISyntaxException e){
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return serverResponse;
    }

    // Overloaded method for Basic Authentication
    public static String sendAuthRequest(String urlString, String username, String password, Boolean disableSSL) {
        if (disableSSL) {
            try {
                disableSSLVerification();
            } catch (Exception e) {
                e.printStackTrace();
                return "Failed to disable SSL verification";
            }
        }

        try {
            URL url = (new URI(urlString)).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Add Basic Authentication header
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            connection.setRequestProperty("Authorization", "Basic " + encodedAuth);

            // Send GET request
            connection.setRequestMethod("GET");

            // Read response
            int responseCode = connection.getResponseCode();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode >= 200 && responseCode < 300 ?
                            connection.getInputStream() :
                            connection.getErrorStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            } finally {
                connection.disconnect();
            }

        } catch (URISyntaxException e){
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
}