#include "encryption.h"
#include "aes.h"

/**
 * Byte Substitution AES layer, utilizes sBox
 *
 * @param state[in, out] Current AES state to be modified.
 * @return void
 */
void byteSub(int** state) {
    int x;
    int y;
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            y = state[i][j] & 0xF;
            x = (state[i][j] >> 4) & 0xF;
            state[i][j] = (int)sBox[x][y];
        }
    }
}


/**
 * Shift Rows AES layer.
 *
 * @param state[in, out] Current AES state to be modified.
 * @return void
 */
void shiftRows(int** state) {
    // Create a copy of the current state
    int** copyOfState = (int**)malloc(4 * sizeof(int*));
    for (int i = 0; i < 4; i++) {
        copyOfState[i] = (int*)malloc(4 * sizeof(int));
        memcpy_s(copyOfState[i], (rsize_t)(4 * sizeof(int)), state[i], (rsize_t)(4 * sizeof(int)));
    }


    state[0][0] = copyOfState[0][0];
    state[0][1] = copyOfState[1][1];
    state[0][2] = copyOfState[2][2];
    state[0][3] = copyOfState[3][3];

    state[1][0] = copyOfState[1][0];
    state[1][1] = copyOfState[2][1];
    state[1][2] = copyOfState[3][2];
    state[1][3] = copyOfState[0][3];

    state[2][0] = copyOfState[2][0];
    state[2][1] = copyOfState[3][1];
    state[2][2] = copyOfState[0][2];
    state[2][3] = copyOfState[1][3];

    state[3][0] = copyOfState[3][0];
    state[3][1] = copyOfState[0][1];
    state[3][2] = copyOfState[1][2];
    state[3][3] = copyOfState[2][3];

    // Free copy
    for (int i = 0; i < 4; i++) {
        free(copyOfState[i]);
    }
    free(copyOfState);
}

/**
 * Helper function to do matrix multiplication of a single column of the state by the constant matrix M
 *
 * @param column[in, out] - the column to be matrix multiplied by M
 * @return void
 */
void mixSingleColumn(int* column) {
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
 * Performs the mix column layer on the current state. Each column is matrix multiplied by a constant matrix M^-1. Multiplication is done within GF(2^8)
 *
 * @param state[in, out] - the current AES state
 * @return void
 */
void mixColumns(int** state) {
    mixSingleColumn(state[0]);
    mixSingleColumn(state[1]);
    mixSingleColumn(state[2]);
    mixSingleColumn(state[3]);
}

/**
 * Encrypts the AES state with the round keys
 *
 * @param state[in, out] - the current AES state. This variable will hold the encrypted state after the function ends
 * @param roundKeys[in] - 11, 13, or 15 round keys used for encryption
 * @param numSubKeys[in] - 11, 13, or 15, based on the number of roundKeys passed in the roundKeys parameter
 * @return void
 */
void encrypt(int** state, uint8_t* roundKeys, int numSubKeys) {
    uint8_t* currentRoundKey = roundKeys;
    xorWithSubkey(state, currentRoundKey);
    for (int round = 1; round < numSubKeys - 1; round++) {
        byteSub(state);
        shiftRows(state);
        mixColumns(state);

        currentRoundKey = roundKeys + (round * 16);
        xorWithSubkey(state, currentRoundKey);
    }
    byteSub(state);
    shiftRows(state);

    currentRoundKey = roundKeys + ((numSubKeys - 1) * 16);
    xorWithSubkey(state, currentRoundKey);
}