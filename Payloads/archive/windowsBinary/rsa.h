#pragma once

#define _CRT_SECURE_NO_WARNINGS
#include <stdio.h>
#include <string.h>
#include <openssl/bn.h>

void rsaEncrypt(char** plaintext_hex, const char* n_str, const char* e_str);