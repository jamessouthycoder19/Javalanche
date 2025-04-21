package Servers.encryption.rsa;

import java.math.BigInteger;
import java.util.Random;

public class rsa {
    private BigInteger n;
    private BigInteger e;
    private BigInteger d;
    private Random random;

    public rsa(int keySize){
        this.random = new Random();
        generateKeys(keySize);
    };

    public rsa(String n, String e){
        this.n = new BigInteger(n);
        this.e = new BigInteger(e);
        this.d = null;
        this.random = null;
    };

    /**
     * Function used to generate public key (n,e) and private key (d) for RSA encryption and Decryption
     * 
     * @param keySize - Either 1024 bits or 2048 bits
     */
    private void generateKeys(int keySize){
        BigInteger p = BigInteger.probablePrime(keySize / 2, random);
        BigInteger q = BigInteger.probablePrime(keySize / 2, random);
        // First part of public key, n
        BigInteger n = p.multiply(q);

        BigInteger phi_n = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE));

        // Second part of public key, e
        BigInteger e;
        while(true){
            e = new BigInteger(1024, random);
            // e must exist in { 1, 2, ... phi(n) - 1}
            if(e.compareTo(phi_n.subtract(BigInteger.ONE)) == -1 && e.compareTo(BigInteger.ONE) == 1){
                // GCD of e and phi(n) must be 1
                if(e.gcd(phi_n).equals(BigInteger.ONE)){
                    break;
                }
            }
        }

        // Private key, d
        BigInteger d = e.modInverse(phi_n);

        this.n = n;
        this.d = d;
        this.e = e;
    }

    /**
     * Returns part of public key, n
     * @return n
     */
    public String getN(){
        return n.toString();
    }
    
    /**
     * Returns part of public key, e
     * @return
     */
    public String getE(){
        return e.toString();
    }
    

    /**
     * Performs RSA encryption on the plain text with the public key.
     * 
     * @param plainText - plain text to be encrypted
     * @return cipher text
     */
    public String encrypt(String plainText){
        BigInteger plainTextAsBigInt = new BigInteger(plainText, 16);
        BigInteger encrypted = plainTextAsBigInt.modPow(e, n);
        String encryptedAsString = encrypted.toString(16);
        return encryptedAsString;
    }

    /**
     * Performs RSA decryption on the plain text with the private key
     * 
     * @param cipherText - cipher text to be decrypted
     * @return plain text
     */
    public String decrypt(String cipherText){
        BigInteger cipherTextAsBigInt = new BigInteger(cipherText, 16);
        BigInteger decrypted = cipherTextAsBigInt.modPow(d, n);
        String decryptedAsString = decrypted.toString(16);
        return decryptedAsString;
    }

    public static void main(String[] args){
        // String startingplainText = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        // String ciphertext = rsa.encrypt(startingplainText);
        // System.out.println(ciphertext.length());
        // rsa newRsa = new rsa(rsa.getN(), rsa.getE());
        // String cipherText = newRsa.encrypt(startingplainText);
        // System.out.println(cipherText);
        // String plainText = rsa.decrypt(cipherText);
        // System.out.println(plainText);
    }
}
