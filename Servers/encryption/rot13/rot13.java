package Servers.encryption.rot13;

public class rot13 {
    public rot13() {}
    
    /**
     * Encrypts/Decrypts the plain text with a simple rot13 cipher - Shifts each letter by 13 spots.
     * (ex. A --> B, E --> R, Y --> L)
     * 
     * Because each letter is just shifted by 13 characters, encrypting/decrypting are the same algorithm
     * 
     * @param plaintext - The text to be shifted
     * @return - The new encrypted/decrypted text
     */
    public String encrypt(String plaintext) {
        StringBuilder encryptedText = new StringBuilder();

        for (char character : plaintext.toCharArray()) {
            if (character >= 'a' && character <= 'z') {
                encryptedText.append((char) ('a' + (character - 'a' + 13) % 26));
            } else if (character >= 'A' && character <= 'Z') {
                encryptedText.append((char) ('A' + (character - 'A' + 13) % 26));
            } else {
                encryptedText.append(character);
            }
        }
        return encryptedText.toString();
    }
}
