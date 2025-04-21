package Servers.encryption.aes;

public class encryption {
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

    private String[] roundKeys;

    protected encryption(String[] roundKeys){
        this.roundKeys = roundKeys;
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
     * Performs the Byte Substitution layyer of AES on the current state
     * 
     * @param state - the current state
     */
    private void byteSub(int[][] state){
        for(int i = 0; i < 4; i++){
            for(int j = 0; j < 4; j++){
                String num = Integer.toBinaryString(state[i][j]);
                while(num.length() < 8){
                    num = "0" + num;
                }
                int x = Integer.parseInt(num.substring(0,4), 2);
                int y = Integer.parseInt(num.substring(4), 2);
                state[i][j] = sBox[x][y];
            }
        }
    } 

    /**
     * Helper function to do matrix multiplication of a single column of the state
     * by the constant matrix M
     * 
     * M
     * 02 03 01 01
     * 01 02 03 01
     * 01 01 02 03
     * 03 01 01 02
     * 
     * @param column - the column to be matrix multiplied by M
     */
    private void mixSingleColumn(int[] column){
        int w0 = xTimes(column[0] ^ column[1]) ^ column[1] ^ column[2] ^ column[3];
        int w1 = column[0] ^ xTimes(column[1] ^ column[2]) ^ column[2] ^ column[3];
        int w2 = column[0] ^ column[1] ^ xTimes(column[2] ^ column[3]) ^ column[3];
        int w3 = column[0] ^ column[1] ^ column[2] ^ xTimes(column[0] ^ column[3]);

        column[0] = w0;
        column[1] = w1;
        column[2] = w2;
        column[3] = w3;
    }

    /**
     * Performs the mix column layer on the current state 
     * Each column is matrix multiplied by a constant matrix M
     * Multiplication is done within GF(2^8)
     * 
     * @param state - the current state
     */
    private void mixColumns(int[][] state){
        mixSingleColumn(state[0]);
        mixSingleColumn(state[1]);
        mixSingleColumn(state[2]);
        mixSingleColumn(state[3]);
    }


    /**
     * Takes the current state, and shifts the rows accrodingly. 
     * Row 0 - not shifted
     * Row 1 - Shifted 1 to the left
     * Row 2 - Shifted 2 to the left
     * Row 3 - Shifted 3 to the left
     * 
     * @param state - current state
     */
    private void shiftRows(int[][] state){
        
        int[] columnOne = new int[4];
        columnOne[0] = state[0][0];
        columnOne[1] = state[1][1];
        columnOne[2] = state[2][2];
        columnOne[3] = state[3][3];

        int[] columnTwo = new int[4];
        columnTwo[0] = state[1][0];
        columnTwo[1] = state[2][1];
        columnTwo[2] = state[3][2];
        columnTwo[3] = state[0][3];

        int[] columnThree = new int[4];
        columnThree[0] = state[2][0];
        columnThree[1] = state[3][1];
        columnThree[2] = state[0][2];
        columnThree[3] = state[1][3];

        int[] columnFour = new int[4];
        columnFour[0] = state[3][0];
        columnFour[1] = state[0][1];
        columnFour[2] = state[1][2];
        columnFour[3] = state[2][3];

        state[0] = columnOne;
        state[1] = columnTwo;
        state[2] = columnThree;
        state[3] = columnFour;
    }


    /**
     * xor's the current state with the 128-bit subkey
     * 
     * @param state - Current state
     * @param key - 128-bit round subkey
     */
    private void xor(int[][] state, String key){
        //System.out.println(key);
        for(int i = 0; i < 4; i++){
            for(int j = 0; j < 4; j++){
                //System.out.println(Integer.parseInt(key.substring((i*32 + j*8), (i*32 + j*8 + 8)), 2));
                state[i][j] = (state[i][j] ^ Integer.parseInt(key.substring((i*32 + j*8), (i*32 + j*8 + 8)), 2));
            }
        }
    }

    /**
     * Runs through all of the rounds of aes
     * 
     * @param state - 128-bit aes block to be encrypted
     */
    public void encrypt(int[][] state){
        xor(state, roundKeys[0]);
        for(int i = 0; i < 9; i++){
            byteSub(state);
            shiftRows(state);
            mixColumns(state);
            xor(state, roundKeys[i+1]);
        }
        byteSub(state);
        shiftRows(state);
        xor(state, roundKeys[10]);
    }
}