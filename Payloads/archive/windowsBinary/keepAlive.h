#pragma once

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <time.h>
#include <stdint.h>

typedef struct {
    SOCKET* clientSocket;
    uint8_t* masterKey;
    int numRounds;
} KeepAliveParams;

unsigned __stdcall sendKeepAlive(void* arg);