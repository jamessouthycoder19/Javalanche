package Servers.encryption.aes;

public class keyExpansion {
    private String masterKey;

    private int[][] sBox = {
        {99, 124, 119, 123, 242, 107, 111, 197, 48, 1, 103, 43, 254, 215, 171, 118},
        {202, 130, 201, 125, 250, 89, 71, 240, 173, 212, 162, 175, 156, 164, 114, 192},
        {183, 253, 147, 38, 54, 63, 247, 204, 52, 165, 229, 241, 113, 216, 49, 21},
        {4, 199, 35, 195, 24, 150, 5, 154, 7, 18, 128, 226, 235, 39, 178, 117},
        {9, 131, 44, 26, 27, 110, 90, 160, 82, 59, 214, 179, 41, 227, 47, 132},
        {83, 209, 0, 237, 32, 252, 177, 91, 106, 203, 190, 57, 74, 76, 88, 207},
        {208, 239, 170, 251, 67, 77, 51, 133, 69, 249, 2, 127, 80, 60, 159, 168},
        {81, 163, 64, 143, 146, 157, 56, 245, 188, 182, 218, 33, 16, 255, 243, 210},
        {205, 12, 19, 236, 95, 151, 68, 23, 196, 167, 126, 61, 100, 93, 25, 115},
        {96, 129, 79, 220, 34, 42, 144, 136, 70, 238, 184, 20, 222, 94, 11, 219},
        {224, 50, 58, 10, 73, 6, 36, 92, 194, 211, 172, 98, 145, 149, 228, 121},
        {231, 200, 55, 109, 141, 213, 78, 169, 108, 86, 244, 234, 101, 122, 174, 8},
        {186, 120, 37, 46, 28, 166, 180, 198, 232, 221, 116, 31, 75, 189, 139, 138},
        {112, 62, 181, 102, 72, 3, 246, 14, 97, 53, 87, 185, 134, 193, 29, 158},
        {225, 248, 152, 17, 105, 217, 142, 148, 155, 30, 135, 233, 206, 85, 40, 223},
        {140, 161, 137, 13, 191, 230, 66, 104, 65, 153, 45, 15, 176, 84, 187, 22}
    };

    protected keyExpansion(String masterKey){
        this.masterKey = "";
        String temp;
        for(int i = 0; i < masterKey.length(); i += 4){ 
            temp = Integer.toBinaryString(Integer.parseInt(masterKey.substring(i, i+4), 16));
            while(temp.length() < 16){
                temp = "0" + temp;
            }
            this.masterKey += temp;
        }
    }

    /**
     * Helper function to do GF(x^8) multiplication for something * x
     * 
     * Whenever whenever x * b > 256, then the result is reduced by x^8 + x^4 + x^3 + x + 1
     * @param b - GF(2^8) element that will be multiplied by x
     * @return b * x
     */
    private int xTimes(int b){
        if((b & 0x80) == 0){
            return b << 1;
        } else {
            return ((b << 1) & 0xFF ^ 0x1B);
        }
    }

    /**
     * Takes in a 128-bit, 192-bit, or 256-bit master key, and generates
     * either 11, 13, or 15 subkeys
     * 
     * @param key - 128, 192, or 256 bit master key
     * @return 11, 13, or 15 subkeys
     */
    protected String[] generateRoundKeys(){
        String[] words;
        String[] subkeys;
        int numSubkeys;
        // Nk is either 4, 6, or 8, depending on the number of words (32-bits) present in the original key
        int Nk;
        
        if(masterKey.length() == 128){
            numSubkeys = 11;
            Nk = 4;
        } else if(masterKey.length() == 192){
            numSubkeys = 13;
            Nk = 6;
        } else {
            numSubkeys = 15;
            Nk = 8;
        }
        words = new String[numSubkeys * 4];
        subkeys = new String[numSubkeys];

        // Load the first Nk words into the list of words
        for(int i = 0; i < Nk; i++){
            words[i] = masterKey.substring(i*32, i*32 + 32);
        }

        int i = Nk;
        String temp;
        while(i < words.length){

            if(i % Nk == 0) {
                // Rotate the 4 bytes within the word
                String rotatedWord = words[i-1].substring(8, 16) + words[i-1].substring(16, 24) + words[i-1].substring(24, 32) + words[i-1].substring(0, 8);
                // Perform s-box substitution on each byte
                String substitutedWord = "";
                for(int j = 0; j < 4; j++){
                    int x = Integer.parseInt(rotatedWord.substring(j*8,j*8 + 4), 2);
                    int y = Integer.parseInt(rotatedWord.substring(j*8 + 4, j*8 + 8), 2);
                    String tempByte = Integer.toBinaryString(sBox[x][y]);
                    while(tempByte.length() < 8){
                        tempByte = "0" + tempByte;
                    }
                    substitutedWord += tempByte;
                }
                // xor the first byte of the word with the round coefficient
                // where the round coefficient is x ^ (i // Nk)
                String restOfWord = substitutedWord.substring(8,32);
                String firstByte = substitutedWord.substring(0,8);
                int roundCoefficient = 1;
                for(int j = 1; j < (i / Nk); j ++){
                    roundCoefficient = xTimes(roundCoefficient);
                }
                firstByte = Integer.toBinaryString(Integer.parseInt(firstByte, 2) ^ roundCoefficient);
                while(firstByte.length() < 8){
                    firstByte = "0" + firstByte;
                }
                temp = (firstByte + restOfWord);
            } else if(Nk == 8 && i % 4 == 0){
                String substitutedWord = "";
                for(int j = 0; j < 4; j++){
                    int x = Integer.parseInt(words[i-1].substring(j*8,j*8 + 4), 2);
                    int y = Integer.parseInt(words[i-1].substring(j*8 + 4, j*8 + 8), 2);
                    String tempByte = Integer.toBinaryString(sBox[x][y]);
                    while(tempByte.length() < 8){
                        tempByte = "0" + tempByte;
                    }
                    substitutedWord += tempByte;
                }

                temp = substitutedWord;
            } else {
                temp = words[i-1];
            }
            // xor previous word (words[i-1]) with word form previous cycle (words[i-nk])
            // split into first and second half so that the integer doesn't reach the max allowed by java
            String firstHalf = Integer.toBinaryString(Integer.parseInt(temp.substring(0,16), 2) ^ Integer.parseInt(words[i - Nk].substring(0,16), 2));
            while(firstHalf.length() < 16){
                firstHalf = "0" + firstHalf;
            }
            String secondHalf = Integer.toBinaryString(Integer.parseInt(temp.substring(16,32), 2) ^ Integer.parseInt(words[i - Nk].substring(16,32), 2));
            while(secondHalf.length() < 16){
                secondHalf = "0" + secondHalf;
            }
            words[i] = (firstHalf + secondHalf);
            i++;
        }

        // Turn the words into subkeys
        for(int n = 0; n < numSubkeys; n++){
            subkeys[n] = words[n*4] +  words[n*4 + 1] + words[n*4 + 2] + words[n*4 + 3];
        }

        return subkeys;
    }

    public void printSubkeys(String[] subkeys){
        for(int i = 0; i < 11; i++){
            System.out.print("0x");
            System.out.print(String.format("%X", Integer.parseInt(subkeys[i].toString().substring(0,16), 2)));
            System.out.print(" 0x");
            System.out.print(String.format("%X", Integer.parseInt(subkeys[i].toString().substring(16,32), 2)));
            System.out.print(" 0x");
            System.out.print(String.format("%X", Integer.parseInt(subkeys[i].toString().substring(32,48), 2)));
            System.out.print(" 0x");
            System.out.print(String.format("%X", Integer.parseInt(subkeys[i].toString().substring(48,64), 2)));
            System.out.print(" 0x");
            System.out.print(String.format("%X", Integer.parseInt(subkeys[i].toString().substring(64,80), 2)));
            System.out.print(" 0x");
            System.out.print(String.format("%X", Integer.parseInt(subkeys[i].toString().substring(80,96), 2)));
            System.out.print(" 0x");
            System.out.print(String.format("%X", Integer.parseInt(subkeys[i].toString().substring(96,112), 2)));
            System.out.print(" 0x");
            System.out.println(String.format("%X", Integer.parseInt(subkeys[i].toString().substring(112,128), 2)));
        }
    }

    public static void main(String[] args){
        String key = "AAAAAAAAAAAAAAAA";
        key = key + key;
        keyExpansion keygen = new keyExpansion(key);
        String[] subkeys = keygen.generateRoundKeys();
        keygen.printSubkeys(subkeys);
    }
}
