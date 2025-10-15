#pragma once

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

void keyExpansion(const uint8_t* masterKey, uint8_t* roundKeys, int numSubkeys);

void printRoundKeys(const uint8_t* roundKeys, int numSubkeys);