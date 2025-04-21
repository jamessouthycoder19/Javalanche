/*
Javalanche Linux Payload - AES Key Expansion
Author: James Southcott
*/

package aes

import (
	"fmt"
	"strconv"
)

// Performs AES key expansion. masterKey can be a 16-byte (128-bit), 24-byte (192-bit), or 32 byte (256-bit) string. This string will be used to generate 11, 13, or 15 16-byte (128-bit) keys, based on the length of the masterKey
//
// Parameters:
//
// - masterKey: AES key used to generate round keys
//
// Returns:
//
// - []string: slice of strings, each string is a 16 byte (128-bit) aes sub key, used in a round of encryption.
func GenerateRoundKeys(masterKey string) []string {
	var words []string
	var subkeys []string
	var numSubkeys int
	var Nk int

	// Determine Nk and numSubkeys based on the masterKey length
	if len(masterKey) == 32 { // 128 bits (32 hex characters)
		numSubkeys = 11
		Nk = 4
	} else if len(masterKey) == 48 { // 192 bits (48 hex characters)
		numSubkeys = 13
		Nk = 6
	} else if len(masterKey) == 64 { // 256 bits (64 hex characters)
		numSubkeys = 15
		Nk = 8
	} else {
		panic("Invalid master key length")
	}

	words = make([]string, numSubkeys*4)
	subkeys = make([]string, numSubkeys)

	// Load the first Nk words into the list of words (each word is 8 hex characters, 32 bits)
	for i := 0; i < Nk; i++ {
		words[i] = masterKey[i*8 : (i+1)*8]
	}

	i := Nk
	var temp string

	// Generate remaining words
	for i < len(words) {
		if i%Nk == 0 {
			// Rotate the word
			rotatedWord := words[i-1][2:8] + words[i-1][0:2]

			// Substitute each byte using the sBox
			substitutedWord := ""
			for j := 0; j < 4; j++ {
				byteHex := rotatedWord[j*2 : (j+1)*2]
				byteValue, _ := strconv.ParseUint(byteHex, 16, 8)
				x := byteValue >> 4  // High nibble
				y := byteValue & 0xF // Low nibble
				substitutedByte := sBox[x][y]
				substitutedWord += fmt.Sprintf("%02X", substitutedByte)
			}

			// XOR the first byte with the round coefficient
			roundCoefficient := 1
			for j := 1; j < (i / Nk); j++ {
				roundCoefficient = XTimes(roundCoefficient)
			}
			firstByte, _ := strconv.ParseUint(substitutedWord[:2], 16, 8)
			firstByte ^= uint64(roundCoefficient)
			temp = fmt.Sprintf("%02X", firstByte) + substitutedWord[2:]
		} else if Nk == 8 && i%4 == 0 {
			// Substitute each byte using the sBox
			substitutedWord := ""
			for j := 0; j < 4; j++ {
				byteHex := words[i-1][j*2 : (j+1)*2]
				byteValue, _ := strconv.ParseUint(byteHex, 16, 8)
				x := byteValue >> 4  // High nibble
				y := byteValue & 0xF // Low nibble
				substitutedByte := sBox[x][y]
				substitutedWord += fmt.Sprintf("%02X", substitutedByte)
			}
			temp = substitutedWord
		} else {
			temp = words[i-1]
		}

		// XOR with the word from the previous cycle
		wordPrev, _ := strconv.ParseUint(words[i-Nk], 16, 32)
		wordCurr, _ := strconv.ParseUint(temp, 16, 32)
		newWord := fmt.Sprintf("%08X", wordPrev^wordCurr)
		words[i] = newWord
		i++
	}

	// Turn words into subkeys
	for n := 0; n < numSubkeys; n++ {
		subkeys[n] = words[n*4] + words[n*4+1] + words[n*4+2] + words[n*4+3]
	}

	return subkeys
}

// Prints AES round keys, used for debugging
//
// Parameters:
//
// - subKeys: keys to print
func PrintSubKeys(subKeys []string) {
	for i := 0; i < len(subKeys); i++ {
		fmt.Println(subKeys[i])
	}
}
