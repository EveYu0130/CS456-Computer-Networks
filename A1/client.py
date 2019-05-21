from socket import *
import sys

def TCP_Negotiation(server_address, n_port, req_code):
    # create TCP socket for server
    clientTCPSocket = socket(AF_INET, SOCK_STREAM)
    # creates TCP connection with the server
    clientTCPSocket.connect((server_address, int(n_port)))
    # sends the request code to server
    clientTCPSocket.send(req_code.encode())

    # read the random port number replied back from server
    r_port = clientTCPSocket.recv(1024).decode()
    # the client closes the TCP connection with server
    clientTCPSocket.close()

    return r_port

def UDP_Transaction(server_address, r_port, msg):
    # create UDP socket to the server in r_port and sends the message
    clientUDPSocket = socket(AF_INET, SOCK_DGRAM)
    clientUDPSocket.sendto(msg.encode(), (server_address,int(r_port)))

    # receives the message, prints out the reversed string and exits
    reversed_msg, serverAddress = clientUDPSocket.recvfrom(2048)
    print('Reversed Message: ' + str(reversed_msg.decode()))
    clientUDPSocket.close()

def main():
    # check for arguments validation
    if len(sys.argv) != 5:
        print('Invalid number of arguments')
        exit(-1)
    try:
        server_address = sys.argv[1]
        n_port = sys.argv[2]
        req_code = sys.argv[3]
        msg = sys.argv[4]
    except:
        print('Invalid arguments format')
        exit(-1)

    # Stage 1. Negotiation using TCP sockets
    r_port = TCP_Negotiation(server_address, n_port, req_code)
    # Stage 2. Transaction using UDP sockets
    UDP_Transaction(server_address, r_port, msg)

main()
