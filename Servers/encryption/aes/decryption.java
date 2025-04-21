package Servers.encryption.aes;

import java.util.Arrays;

public class decryption {
    private int[][] inverseSBox = {
        {82, 9, 106, 213, 48, 54, 165, 56, 191, 64, 163, 158, 129, 243, 215, 251},
        {124, 227, 57, 130, 155, 47, 255, 135, 52, 142, 67, 68, 196, 222, 233, 203},
        {84, 123, 148, 50, 166, 194, 35, 61, 238, 76, 149, 11, 66, 250, 195, 78},
        {8, 46, 161, 102, 40, 217, 36, 178, 118, 91, 162, 73, 109, 139, 209, 37},
        {114, 248, 246, 100, 134, 104, 152, 22, 212, 164, 92, 204, 93, 101, 182, 146},
        {108, 112, 72, 80, 253, 237, 185, 218, 94, 21, 70, 87, 167, 141, 157, 132},
        {144, 216, 171, 0, 140, 188, 211, 10, 247, 228, 88, 5, 184, 179, 69, 6},
        {208, 44, 30, 143, 202, 63, 15, 2, 193, 175, 189, 3, 1, 19, 138, 107},
        {58, 145, 17, 65, 79, 103, 220, 234, 151, 242, 207, 206, 240, 180, 230, 115},
        {150, 172, 116, 34, 231, 173, 53, 133, 226, 249, 55, 232, 28, 117, 223, 110},
        {71, 241, 26, 113, 29, 41, 197, 137, 111, 183, 98, 14, 170, 24, 190, 27},
        {252, 86, 62, 75, 198, 210, 121, 32, 154, 219, 192, 254, 120, 205, 90, 244},
        {31, 221, 168, 51, 136, 7, 199, 49, 177, 18, 16, 89, 39, 128, 236, 95},
        {96, 81, 127, 169, 25, 181, 74, 13, 45, 229, 122, 159, 147, 201, 156, 239},
        {160, 224, 59, 77, 174, 42, 245, 176, 200, 235, 187, 60, 131, 83, 153, 97},
        {23, 43, 4, 126, 186, 119, 214, 38, 225, 105, 20, 99, 85, 33, 12, 125}
    };

    private String[] roundKeys;

    protected decryption(String[] roundKeys){
        this.roundKeys = roundKeys;
    }

    /**
     * xor's the current state with the 128-bit subkey
     * 
     * @param state - Current state
     * @param key - 128-bit round subkey
     */
    private void xor(int[][] state, String key){
        for(int i = 0; i < 4; i++){
            for(int j = 0; j < 4; j++){
                state[i][j] = (state[i][j] ^ Integer.parseInt(key.substring((i*32 + j*8), (i*32 + j*8 + 8)), 2));
            }
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
     * Helper function to multiply a value by 9 within the GF(2^8)
     * 9 in GF(2^8) = x^3 + 1
     * 
     * @param value - value to be multiplied by 9
     */
    private int mulByNine(int value){
        return xTimes(xTimes(xTimes(value))) ^ value;
    }

    /**
     * Helper function to multiply a value by 11 within the GF(2^8)
     * 11 in GF(2^8) = x^3 + x + 1
     * 
     * @param value - value to be multiplied by 11
     */
    private int mulByEleven(int value){
        return xTimes(xTimes(xTimes(value)) ^ value) ^ value;
    }

    /**
     * Helper function to multiply a value by 13 within the GF(2^8)
     * 13 in GF(2^8) = x^3 + x^2 + 1
     * 
     * @param value - value to be multiplied by 13
     */
    private int mulByThirteen(int value){
        return xTimes(xTimes(xTimes(value) ^ value)) ^ value;
    }

    /**
     * Helper function to multiply a value by 14 within the GF(2^8)
     * 14 in GF(2^8) = x^3 + x^2 + x
     * 
     * @param value - value to be multiplied by 14
     */
    private int mulByFourteen(int value){
        return xTimes(xTimes(xTimes(value) ^ value) ^ value);
    }

    /**
     * Helper function to do matrix multiplication of a single column of the state
     * by the constant matrix M^-1
     * 
     * M^-1
     * 14 11 13 09
     * 09 14 11 13
     * 13 09 14 11
     * 11 13 09 14
     * 
     * @param column - the column to be matrix multiplied by M
     */
    private void invMixSingleColumn(int[] column){
        int w0 = mulByFourteen(column[0]) ^ mulByEleven(column[1]) ^ mulByThirteen(column[2]) ^ mulByNine(column[3]);
        int w1 = mulByNine(column[0]) ^ mulByFourteen(column[1]) ^ mulByEleven(column[2]) ^ mulByThirteen(column[3]);
        int w2 = mulByThirteen(column[0]) ^ mulByNine(column[1]) ^ mulByFourteen(column[2]) ^ mulByEleven(column[3]);
        int w3 = mulByEleven(column[0]) ^ mulByThirteen(column[1]) ^ mulByNine(column[2]) ^ mulByFourteen(column[3]);

        column[0] = w0;
        column[1] = w1;
        column[2] = w2;
        column[3] = w3;
    }

    /**
     * Performs the inverse of the mix column layer on the current state 
     * Each column is matrix multiplied by a constant matrix M^-1
     * Multiplication is done within GF(2^8)
     * 
     * @param state - the current state
     */
    private void invMixColumns(int[][] state){
        invMixSingleColumn(state[0]);
        invMixSingleColumn(state[1]);
        invMixSingleColumn(state[2]);
        invMixSingleColumn(state[3]);
    }

    
    /**
     * Performs the inverse Byte Substitution layer of AES on the current state
     * 
     * @param state - the current state
     */
    private void invByteSub(int[][] state){
        for(int i = 0; i < state.length; i++){
            for(int j = 0; j < state[0].length; j++){
                String num = Integer.toBinaryString(state[i][j]);
                while(num.length() < 8){
                    num = "0" + num;
                }
                int x = Integer.parseInt(num.substring(0,4), 2);
                int y = Integer.parseInt(num.substring(4), 2);
                state[i][j] = inverseSBox[x][y];
            }
        }
    }

    /**
     * Takes the current state, and shifts the rows accrodingly. 
     * Row 0 - not shifted
     * Row 1 - Shifted 1 to the right
     * Row 2 - Shifted 2 to the right
     * Row 3 - Shifted 3 to the right
     * 
     * @param state - current state
     */
    private void invShiftRows(int[][] state){
        
        int[] columnOne = new int[4];
        columnOne[0] = state[0][0];
        columnOne[1] = state[3][1];
        columnOne[2] = state[2][2];
        columnOne[3] = state[1][3];

        int[] columnTwo = new int[4];
        columnTwo[0] = state[1][0];
        columnTwo[1] = state[0][1];
        columnTwo[2] = state[3][2];
        columnTwo[3] = state[2][3];

        int[] columnThree = new int[4];
        columnThree[0] = state[2][0];
        columnThree[1] = state[1][1];
        columnThree[2] = state[0][2];
        columnThree[3] = state[3][3];

        int[] columnFour = new int[4];
        columnFour[0] = state[3][0];
        columnFour[1] = state[2][1];
        columnFour[2] = state[1][2];
        columnFour[3] = state[0][3];

        state[0] = columnOne;
        state[1] = columnTwo;
        state[2] = columnThree;
        state[3] = columnFour;
    }

    public void printState(int[][] state){
        for(int i = 0; i < 4; i++){
            System.out.print(Arrays.toString(state[i]));
            System.out.print(", ");
        }
        System.out.println();
    }
    
    /**
     * Runs through all of the rounds of aes
     * 
     * @param state - 128-bit aes block to be decrypted
     */
    protected void decrypt(int[][] state){
        xor(state, roundKeys[10]);
        invShiftRows(state);
        invByteSub(state);
        for(int i = 1; i < 10; i++){
            xor(state, roundKeys[10-i]);
            invMixColumns(state);
            invShiftRows(state);
            invByteSub(state);         
        }
        xor(state, roundKeys[0]);
    }
}
