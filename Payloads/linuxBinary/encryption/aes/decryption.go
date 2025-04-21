/*
Javalanche Linux Payload - AES Key Expansion
Author: James Southcott
*/

package aes

import (
	"strconv"
)

// Multiplies an integer * 9 within GF(2^8). 9 in GF(2^8) = x^3 + 1
//
// Parameters:
//
// - value: integer to be multiplied by 9
//
// Returns:
//
// - int: value * 9
func mulByNine(value int) int {
	return XTimes(XTimes(XTimes(value))) ^ value
}

// Multiplies an integer * 11 within GF(2^8). 11 in GF(2^8) = x^3 + x + 1
//
// Parameters:
//
// - value: integer to be multiplied by 11
//
// Returns:
//
// - int: value * 11
func mulByEleven(value int) int {
	return XTimes(XTimes(XTimes(value))^value) ^ value
}

// Multiplies an integer * 13 within GF(2^8). 13 in GF(2^8) = x^3 + x^2 + 1
//
// Parameters:
//
// - value: integer to be multiplied by 13
//
// Returns:
//
// - int: value * 13
func mulByThirteen(value int) int {
	return XTimes(XTimes(XTimes(value)^value)) ^ value
}

// Multiplies an integer * 14 within GF(2^8). 14 in GF(2^8) = x^3 + x^2 + x
//
// Parameters:
//
// - value: integer to be multiplied by 14
//
// Returns:
//
// - int: value * 14
func mulByFourteen(value int) int {
	return XTimes(XTimes(XTimes(value)^value) ^ value)
}

// Performs inverse shift rows AES layer on the state
//
// Parameters:
//
// - state: AES state to perform the layer on
func invShiftRows(state [][]int) {
	// Shift right by 1
	tmp := state[3][1]
	state[3][1] = state[2][1]
	state[2][1] = state[1][1]
	state[1][1] = state[0][1]
	state[0][1] = tmp

	// Shift right by 2
	tmp1 := state[3][2]
	tmp2 := state[2][2]
	state[3][2] = state[1][2]
	state[2][2] = state[0][2]
	state[1][2] = tmp1
	state[0][2] = tmp2

	// Shift right by 3
	tmp = state[1][3]
	state[1][3] = state[2][3]
	state[2][3] = state[3][3]
	state[3][3] = state[0][3]
	state[0][3] = tmp
}

// Performs inverse byte substitution AES layer on the state
//
// Parameters:
//
// - state: AES state to perform the layer on
func invByteSub(state [][]int) {
	for i := 0; i < 4; i++ {
		for j := 0; j < 4; j++ {
			binary := strconv.FormatInt(int64(state[i][j]), 2)
			for len(binary) < 8 {
				binary = "0" + binary
			}
			x, _ := strconv.ParseInt(binary[0:4], 2, 64)
			y, _ := strconv.ParseInt(binary[4:8], 2, 64)

			state[i][j] = inverseSBox[x][y]
		}
	}
}

// Helper function called by invMixColumns to do matrix multiplication on a column of an AES state
// Each column is multiplied by the matrix M^-1:
//
// 14 11 13 09
//
// 09 14 11 13
//
// 13 09 14 11
//
// 11 13 09 14
//
// Parameters:
//
// - column: column to perform matrix multiplication on
func invMixSingleColumn(column []int) {
	w0 := mulByFourteen(column[0]) ^ mulByEleven(column[1]) ^ mulByThirteen(column[2]) ^ mulByNine(column[3])
	w1 := mulByNine(column[0]) ^ mulByFourteen(column[1]) ^ mulByEleven(column[2]) ^ mulByThirteen(column[3])
	w2 := mulByThirteen(column[0]) ^ mulByNine(column[1]) ^ mulByFourteen(column[2]) ^ mulByEleven(column[3])
	w3 := mulByEleven(column[0]) ^ mulByThirteen(column[1]) ^ mulByNine(column[2]) ^ mulByFourteen(column[3])

	column[0] = w0
	column[1] = w1
	column[2] = w2
	column[3] = w3
}

// Performs inverse mix columns AES layer on the state
//
// Parameters:
//
// - state: AES state to perform the layer on
func invMixColumns(state [][]int) {
	invMixSingleColumn(state[0])
	invMixSingleColumn(state[1])
	invMixSingleColumn(state[2])
	invMixSingleColumn(state[3])
}

// Performs AES Decryption on 1 128-bit state (block)
//
// Parameters:
//
// - state: 128-bit block to perform decryption on
//
// - subKeys: AES subkeys used for decryption. can be generated with GenerateRoundKeys(masterKey string)
func Decrypt(state [][]int, subkeys []string) {
	XOR(state, subkeys[10])
	invShiftRows(state)
	invByteSub(state)
	for i := 1; i < 10; i++ {
		XOR(state, subkeys[10-i])
		invMixColumns(state)
		invShiftRows(state)
		invByteSub(state)
	}
	XOR(state, subkeys[0])
}
