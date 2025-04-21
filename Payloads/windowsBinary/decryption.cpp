#include "decryption.h"
#include "aes.h"

/**
 * Inverse Shift Rows AES layer.
 *
 * @param state[in, out] Current AES state to be modified.
 * @return void
 */
void invShiftRows(int** state) {
    // Create a copy of the current state
    int** copyOfState = (int**)malloc(4 * sizeof(int*));
    for (int i = 0; i < 4; i++) {
        copyOfState[i] = (int*)malloc(4 * sizeof(int));
        memcpy_s(copyOfState[i], (rsize_t)(4 * sizeof(int)), state[i], (rsize_t)(4 * sizeof(int)));
    }

    state[0][0] = copyOfState[0][0];
    state[0][1] = copyOfState[3][1];
    state[0][2] = copyOfState[2][2];
    state[0][3] = copyOfState[1][3];

    state[1][0] = copyOfState[1][0];
    state[1][1] = copyOfState[0][1];
    state[1][2] = copyOfState[3][2];
    state[1][3] = copyOfState[2][3];

    state[2][0] = copyOfState[2][0];
    state[2][1] = copyOfState[1][1];
    state[2][2] = copyOfState[0][2];
    state[2][3] = copyOfState[3][3];

    state[3][0] = copyOfState[3][0];
    state[3][1] = copyOfState[2][1];
    state[3][2] = copyOfState[1][2];
    state[3][3] = copyOfState[0][3];

    // Free copy
    for (int i = 0; i < 4; i++) {
        free(copyOfState[i]);
    }
    free(copyOfState);
}

/**
 * Inverse Byte Substitution AES layer, utilizes inverseSBox
 *
 * @param state[in, out] Current AES state to be modified.
 * @return void
 */
void invByteSub(int** state) {
    int x;
    int y;
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            y = state[i][j] & 0xF;
            x = (state[i][j] >> 4) & 0xF;
            state[i][j] = inverseSBox[x][y];
        }
    }
}


/**
 * Helper function used by many AES functions to do value * 9 within GF(2^8). 9 in GF(2^8) = x^3 + 1
 *
 * @param value[in] - value, where value exists in GF(2^8). value will be multiplied by 9
 * @return value * x modulo AES irreducible polynomial
 */
int mulByNine(int value) {
    return xTimes(xTimes(xTimes(value))) ^ value;
}


/**
 * Helper function used by many AES functions to do value * 11 within GF(2^8). 11 in GF(2^8) = x^3 + X + 1
 *
 * @param value[in] - value, where value exists in GF(2^8). value will be multiplied by 11
 * @return value * x modulo AES irreducible polynomial
 */
int mulByEleven(int value) {
    return xTimes(xTimes(xTimes(value)) ^ value) ^ value;
}

/**
 * Helper function used by many AES functions to do value * 13 within GF(2^8). 13 in GF(2^8) = x^3 + X^2 + 1
 *
 * @param value[in] - value, where value exists in GF(2^8). value will be multiplied by 12
 * @return value * x modulo AES irreducible polynomial
 */
int mulByThirteen(int value) {
    return xTimes(xTimes(xTimes(value) ^ value)) ^ value;
}

/**
 * Helper function used by many AES functions to do value * 14 within GF(2^8). 14 in GF(2^8) = x^3 + X^2 + X
 *
 * @param value[in] - value, where value exists in GF(2^8). value will be multiplied by 14
 * @return value * x modulo AES irreducible polynomial
 */
int mulByFourteen(int value) {
    return xTimes(xTimes(xTimes(value) ^ value) ^ value);
}

/**
 * Helper function to do matrix multiplication of a single column of the state
 * by the constant matrix M^-1
 *
 * M^-1
 * 
 * 14 11 13 09
 * 09 14 11 13
 * 13 09 14 11
 * 11 13 09 14
 * 
 * @param column[in, out] - the column to be matrix multiplied by M
 * @return void
 */
void invMixSingleColumn(int* column) {
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
 * @param state[in, out] - the current AES state
 * @return void
 */
void invMixColumns(int** state) {
    invMixSingleColumn(state[0]);
    invMixSingleColumn(state[1]);
    invMixSingleColumn(state[2]);
    invMixSingleColumn(state[3]);
}

/**
 * Decrypts the AES state with the round keys
 *
 * @param state[in, out] - the current AES state. This variable will hold the decrypted state after the function ends
 * @param roundKeys[in] - 11, 13, or 15 round keys used for decryption
 * @param numSubKeys[in] - 11, 13, or 15, based on the number of roundKeys passed in the roundKeys parameter
 * @return void
 */
void decrypt(int** state, uint8_t* roundKeys, int numSubKeys) {
    uint8_t* currentRoundKey = roundKeys;

    currentRoundKey = roundKeys + ((numSubKeys - 1) * 16);
    xorWithSubkey(state, currentRoundKey);

    invShiftRows(state);
    invByteSub(state);

    for (int round = numSubKeys - 2; round > 0; round--) {
        currentRoundKey = roundKeys + ((round) * 16);
        xorWithSubkey(state, currentRoundKey);

        invMixColumns(state);
        invShiftRows(state);
        invByteSub(state);
    }
    currentRoundKey = roundKeys;
    xorWithSubkey(state, currentRoundKey);
}