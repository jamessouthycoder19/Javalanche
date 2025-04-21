#include "keyExpansion.h"
#include "aes.h"

/**
 * Performs AES Key Expansion algorithm
 *
 * @param masterKey[in] - 16 byte (128-bit), 24 byte (192-bit), or 32 byte (256-bit) int array, containing the AES key
 * @param roundKeys[out] - 11, 13, or 15 128-bit round keys used for AES. 
 * @param numSubKeys[in] - 11, 13 or 15, depending on the length of the master key
 * @return void
 */
void keyExpansion(const uint8_t* masterKey, uint8_t* roundKeys, int numSubkeys) {
    int Nk, rounds, masterKeyLength;

    // Determine the number of subkeys, Nk, and masterKeyLength
    if (numSubkeys == 11) { // 128-bit key
        masterKeyLength = 16;
        rounds = 11;
        Nk = 4;
    }
    else if (numSubkeys == 13) { // 192-bit key
        masterKeyLength = 24;
        rounds = 13;
        Nk = 6;
    }
    else if (numSubkeys == 15) { // 256-bit key
        masterKeyLength = 32;
        rounds = 15;
        Nk = 8;
    }
    else {
        printf("Invalid key length.\n");
        exit(EXIT_FAILURE);
    }

    // Copy the master key into the first Nk words
    memcpy(roundKeys, masterKey, masterKeyLength);

    uint32_t temp;
    int i = Nk;

    while (i < rounds * 4) {
        temp = ((uint32_t*)roundKeys)[i - 1];

        if (i % Nk == 0) {
            // Rotate bytes
            temp = (temp << 24) | (temp >> 8);

            // Substitution with S-box
            uint8_t* tempBytes = (uint8_t*)&temp;
            for (int j = 0; j < 4; j++) {
                tempBytes[j] = sBox[tempBytes[j] >> 4][tempBytes[j] & 0x0F];
            }
            // XOR with round constant
            int roundCoefficient = 1;
            for (int j = 1; j < (i / Nk); j++) {
                roundCoefficient = xTimes(roundCoefficient);
            }
            tempBytes[0] ^= roundCoefficient;
        }
        else if (Nk == 8 && (i % Nk == 4)) {
            // Apply S-box
            uint8_t* tempBytes = (uint8_t*)&temp;
            for (int j = 0; j < 4; j++) {
                tempBytes[j] = sBox[tempBytes[j] >> 4][tempBytes[j] & 0x0F];
            }
        }

        ((uint32_t*)roundKeys)[i] = ((uint32_t*)roundKeys)[i - Nk] ^ temp;
        i++;
    }
}

/**
 * Prints the AES Round keys, used for debugging
 *
 * @param roundKeys[in] - AES round keys
 * @param numSubKeys[out] - 11, 13, or 15
 * @return void
 */
void printRoundKeys(const uint8_t* roundKeys, int numSubkeys) {
    for (int i = 0; i < numSubkeys; i++) {
        for (int j = 0; j < 16; j++) {
            printf("0x%02X ", roundKeys[i * 16 + j]);
        }
        printf("\n");
    }
}