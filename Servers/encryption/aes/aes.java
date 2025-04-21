package Servers.encryption.aes;

// Main Class used to create an AES class, used for encrypting and decrypting text

import java.util.Arrays;

public class aes {
    // Object used to turn master key into 11, 13, or 15 round keys
    private keyExpansion keyExpansion;
    // Round keys used in each AES round
    private String[] roundKeys;
    // Class used to encrypt plain text to cipher text using AES
    private encryption encryption;
    // Class used to decrypt cipher text to plain text using AES
    private decryption decryption;
    // Mode of operation to be used in AES.
    private modes mode;

    /**
     * Class to create a new AES object to encrypt and decrypt text.
     * The Master key is either a 128, 192, or 256-bit aes key. Whoever
     * the recepient is must all have this master key. The mode determines
     * the mode of operation for AES. Currently, only ECB is supported
     * 
     * @param masterKey - 128, 192, or 256-bit master key to be used
     * @param mode - mode of operation that AES will be used in
     */
    public aes(String masterKey, modes mode){
        this.keyExpansion = new keyExpansion(masterKey);
        this.roundKeys = keyExpansion.generateRoundKeys();
        this.encryption = new encryption(roundKeys);
        this.decryption = new decryption(roundKeys);
        this.mode = mode;
    }

    /**
     * Takes a String plain text, and encodes it into ascii text. The return value is an array, of 2d arrays. 
     * Each of these 2d arrays is a 128-bit block for aes to encrypt
     * 
     * @param input - text to be encoded
     * @param decrypt - true if decryption is about to occur, false if encryption is about to occur
     * @return array of 128-bit blocks, organized for aes
     */
    private int[][][] stringToIntArray(String input, boolean decrypt) {
        // Determine how many arrays we will have
        // If the number of bytes is perfectly equal to fit the number of arrays, we have to add an extra,
        // because the end of the message is signified by the last bit, and then all 0's, i.e. 128 0 0 0
        
        int numArrays = (input.length() / 16);
        if(!decrypt){numArrays++;}

        int[][][] asciiArray = new int[numArrays][4][4];
        
        // Iterate through and populate the new arrays
        int index = 0;
        for(int numArray = 0; numArray < numArrays; numArray++){
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    if (index < input.length()) {
                        // Enter normal converted byte
                        asciiArray[numArray][i][j] = (int) input.charAt(index);
                    } else if (index == input.length()){
                        // The first byte after the normal bytes are done being entered is 128, aka 10000000
                        // This final 1 signifies that all the 0's after (and the final 1), are not a part of
                        // the decoded text
                        asciiArray[numArray][i][j] = 128;
                    } else {
                        asciiArray[numArray][i][j] = 0;
                    }
                    index++;
                }
            }
        }
        return asciiArray;
    }

    /**
     * Takes 128-bit state from aes, and decodes the bytes into ASCII text
     * 
     * @param asciiArray - 128-bit aes state
     * @return deconded text
     */
    private String intArrayToString(int[][] asciiArray) {
        StringBuilder result = new StringBuilder();
    
        // Iterate through the 2d array
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                result.append((char) asciiArray[i][j]);
            }
        }
    
        return result.toString();
    }

    /**
     * Takes multiple 128-bit aes states, and decodes them all into ascii text
     * 
     * @param asciiArray - multiple 128-bit aes states
     * @return decoded text
     */
    private String intArrayToString(int[][][] asciiArray){
        StringBuilder result = new StringBuilder();

        // First, iterate backwards through this 3d array, to find the final bit (it will look something like 128 0 0 0 0)
        // This final bit signifies the place to stop decoding text, as the rest of it is just filler
        int totalChars = asciiArray.length * 16;
        int count = 0;

        // outerloop bascially just tells the break statement to break out of all 3 for loops when the correct value is found
        outerloop:
        for(int i = asciiArray.length - 1; i > -1; i--){
            for(int j = 3; j > -1; j--){
                for(int k = 3; k > -1; k--){
                    count++;
                    if(asciiArray[i][j][k] == 128){
                        break outerloop;
                    }
                }
            }
        }

        // Subtract the total number of bytes + the 128 byte that we found
        totalChars -= count;
    
        // Iterate through the arrays, and append characters until the final 1 is found (determined earlier)
        count = 0;

        secondOuterLoop:
        for (int i = 0; i < asciiArray.length; i++) {
            for (int j = 0; j < 4; j++) {
                for (int k = 0; k < 4; k ++){   
                    result.append((char) asciiArray[i][j][k]);
                    count++;
                    if(count == totalChars){
                        break secondOuterLoop;
                    }
                }
            }
        }
    
        return result.toString();
    }

    /**
     * Takes plain text, and encrypts with AES
     * 
     * @param plainText - Plain text to be encrypted
     * @return Cipher text
     */
    public String encrypt(String plainText){
        int[][][] bytes = stringToIntArray(plainText, false);
        String cipherText = "";
        for(int[][] state : bytes){
            if(mode == modes.ECB){
                encryption.encrypt(state);
            }
            cipherText += intArrayToString(state);
        }
        return cipherText;
    }

    /**
     * Takes cipher text, and decrypts with AES
     * 
     * @param cipherText - Cipher text to be decrypted
     * @return Plain text
     */
    public String decrypt(String cipherText){
        int[][][] states = stringToIntArray(cipherText, true);
        
        // Decrypt all of the states contained in the cipher text
        for(int[][] state : states){
            if(mode == modes.ECB){
                decryption.decrypt(state);
            }
        }
        
        // Now decode all of the states
        String plainText = intArrayToString(states);
        return plainText;
    }
    
    public void printState(int[][] state){
        for(int i = 0; i < 4; i++){
            System.out.print(Arrays.toString(state[i]));
            System.out.print(", ");
        }
        System.out.println();
    }

    public static void main(String[] args){
        String key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        aes aes = new aes(key, modes.ECB);
        
        String plainText = "Hello World";
        System.out.println(plainText);
        String cipherText = aes.encrypt(plainText);
        System.out.println(cipherText);
        String finaltext = aes.decrypt(cipherText);
        System.out.println(finaltext);
    }
}