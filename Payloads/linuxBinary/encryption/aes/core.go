/*
Javalanche Linux Payload - AES functions
Author: James Southcott
*/

package aes

import (
	"fmt"
	"strconv"
	"strings"
)

func XTimes(b int) int {
	if (b & 0x80) == 0 {
		return b << 1
	} else {
		return ((b<<1)&0xFF ^ 0x1B)
	}
}

var sBox = [][]int{
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
	{140, 161, 137, 13, 191, 230, 66, 104, 65, 153, 45, 15, 176, 84, 187, 22},
}

var inverseSBox = [][]int{
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
	{23, 43, 4, 126, 186, 119, 214, 38, 225, 105, 20, 99, 85, 33, 12, 125},
}

// xor's current AES state with the subkey
//
// Parameters:
//
// - state: current 128-bit AES state
//
// - subKey: 128-bit subkey
//
// Returns:
//
// - string: xor of state and subKey
func XOR(state [][]int, subKey string) {
	for i := 0; i < 4; i++ {
		for j := 0; j < 4; j++ {
			keyvalue, _ := strconv.ParseInt(subKey[(i*8+j*2):((i*8+j*2)+2)], 16, 9)
			state[i][j] = state[i][j] ^ int(keyvalue)
		}
	}
}

// Decode's a string from UTF-8
//
// Parameters:
//
// - input: string to be decoded
//
// Returns:
//
// - string: Decoded UTF-8 string
func DecodeUTF8String(input string) string {
	output := make([]byte, len(input)) // Allocate space for the output
	outputIndex := 0

	for i := 0; i < len(input); i++ {
		if (input[i] & 0x80) == 0 {
			// Single-byte UTF-8 (ASCII)
			output[outputIndex] = input[i]
			outputIndex++
		} else if (input[i] & 0xE0) == 0xC0 {
			// Multi-byte UTF-8
			firstByte := input[i]
			secondByte := input[i+1]
			decodedByte := ((firstByte & 0x1F) << 6) | (secondByte & 0x3F)
			output[outputIndex] = decodedByte
			outputIndex++
			i++ // Skip the continuation byte
		}
	}

	// Convert the output slice to a string
	return string(output[:outputIndex])
}

// Takes a string, and returns a 3d int slice. Each slice is a 2d 4x4 slice, aka a 128-bit AES state
//
// Parameters:
//
// - input: string to be turned into a 3d int slice
//
// - decrypt: true if decrypting, false if encrypting
//
// Returns:
//
// - [][][]int: 3d int slice containing the values from input
func StringToIntSlice(input string, decrypt bool) [][][]int {
	// Determine number of slices
	numArrays := len(input) / 16
	if !decrypt {
		numArrays += 1
	}

	// Initialize the 3d slice
	asciiArray := make([][][]int, numArrays)
	for i := range asciiArray {
		asciiArray[i] = make([][]int, 4)
		for j := range asciiArray[i] {
			asciiArray[i][j] = make([]int, 4)
		}
	}

	// Populate the arrays
	index := 0
	for i := 0; i < numArrays; i++ {
		for j := 0; j < 4; j++ {
			for k := 0; k < 4; k++ {
				if index < len(input) {
					asciiArray[i][j][k] = int(input[index])
				} else if index == len(input) {
					asciiArray[i][j][k] = 128
				} else {
					asciiArray[i][j][k] = 0
				}
				index += 1
			}
		}
	}

	return asciiArray
}

// Takes a 3d int slice, and turns it into a string
//
// Parameters:
//
// - asciiArray: 3d int slice (slice of 128-bit AES states) to be turned into a string
//
// - decrypt: true if decrypting, false if encrypting
//
// Returns:
//
// - string: string that contains all of the character values from the asciiArray param
func IntArrayToString(asciiArray [][][]int, decrypt bool) string {
	var output strings.Builder
	totalChars := len(asciiArray) * 16
	count := 0
	if decrypt {
		for i := len(asciiArray) - 1; i > -1; i-- {
			for j := 3; j > -1; j-- {
				for k := 3; k > -1; k-- {
					count++
					if asciiArray[i][j][k] == 128 {
						i = -1
						j = -1
						k = -1
					}
				}
			}
		}
	}
	totalChars -= count

	count = 0

	for i := 0; i < len(asciiArray); i++ {
		for j := 0; j < 4; j++ {
			for k := 0; k < 4; k++ {
				output.WriteString(string(rune(asciiArray[i][j][k])))
				count++
				if count == totalChars {
					i = len(asciiArray)
					j = 4
					k = 4
				}
			}
		}
	}

	return output.String()
}

// Performs AES Encryption on the plain text.
//
// Parameters:
//
// - plainText: string to be encrypted
//
// - subKeys: AES subKeys used in encryption. These can be generated using GenerateRoundKeys(masterKey string)
//
// Returns:
//
// - string: cipher text
func AESEncrypt(plainText string, subkeys []string) string {
	states := StringToIntSlice(plainText, false)

	for i := 0; i < len(states); i++ {
		Encrypt(states[i], subkeys)
	}

	cipherText := IntArrayToString(states, false)

	return cipherText
}

// Performs AES Decryption on the cipher text
//
// Parameters:
//
// - cipherText: string to be decrypted
//
// - subKeys: AES subKeys used in decryption. These can be generated using GenerateRoundKeys(masterKey string)
//
// Returns:
//
// - string: plain text
func AESDecrypt(cipherText string, subkeys []string) string {
	states := StringToIntSlice(DecodeUTF8String(cipherText), true)

	for i := 0; i < len(states); i++ {
		Decrypt(states[i], subkeys)
	}

	plainText := IntArrayToString(states, true)

	return plainText
}

// Prints the AES state passed as a parameter, used for debugging
//
// Parameters:
//
// - state: 2d int slice (128-bit AES state) to be printed
func PrintState(state [][]int) {
	for i := 0; i < 4; i++ {
		fmt.Printf("[%d, %d, %d, %d], ", state[i][0], state[i][1], state[i][2], state[i][3])
	}
	fmt.Println()
}
