package Servers.C2;

import java.util.Scanner;

public class C2ServerUserHandler implements Runnable{
    // Pointer back to the C2 Server
    private C2Server C2server;

    // Scanner for the user to enter input
    private Scanner userInputScanner;

    /**
     * Class to handle input from the user controlling the C2
     * and send it back to the C2 Server.
     * 
     * @param server Pointer to the C2 Server
     */
    public C2ServerUserHandler(C2Server server){
        this.C2server = server;
        this.userInputScanner = new Scanner(System.in);
    }

    /**
     * Used by the C2 Server to output a message to the CLI for the Threat Actor to View
     * 
     * @param message Message to be displayed
     */
    protected void outputToCLI(String message){
        System.out.println(message);
    }

    @Override
    public void run() {
        while(true){
            // Give the user a bunch of optiions
            System.out.println("Choices: TODO");
            String command = userInputScanner.next();
            // Send the command back to the C2 Server
            C2server.broadcast(command);
            // TODO CLI for the user to use
        }
    }
}