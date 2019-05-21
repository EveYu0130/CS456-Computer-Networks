from socket import *
import sys

def UDP_Transaction(serverUDPSocket):
    # receives the string and sends the reversed string back to the client
    msg, client_address = serverUDPSocket.recvfrom(2048)
    reversed_msg = msg[::-1]
    serverUDPSocket.sendto(reversed_msg.encode(), client_address)

def TCP_Negotiation(req_code):
    # create TCP welcoming socket, bind TCP socket,
    # begins listeining for incoming TCP requests
    serverTCPSocket = socket(AF_INET, SOCK_STREAM)
    serverTCPSocket.bind(('',0))
    serverTCPSocket.listen(1)

    n_port = serverTCPSocket.getsockname()[1];
    print('SERVER_PORT=' + str(n_port))

    # loop forever
    while True:
        # server waits on accept() for incoming request, new socket created on return
        connectionSocket, client_address = serverTCPSocket.accept()
        # reads request code in bytes from socket
        code = connectionSocket.recv(1024).decode()

        if code != req_code:
            # the server closes the TCP connection since the client fails to
            # send the intended <req_code>
            print('Invalid request code')
            connectionSocket.close()
        else:
            # the server verifies the request code, and replies back to client
            # with a random port number <r_port> where it will be listening for
            # the actual request.
            serverUDPSocket = socket(AF_INET, SOCK_DGRAM)
            serverUDPSocket.bind(('',0))
            r_port = serverUDPSocket.getsockname()[1]
            connectionSocket.send(str(r_port).encode())

            # Stage 2. Transaction using UDP sockets
            UDP_Transaction(serverUDPSocket)


def main():
    # check for arguments validation
    if len(sys.argv) != 2:
        print('Incorrect number of arguments')
        exit(-1)
    try:
        req_code = sys.argv[1]
    except ValueError:
        print('Invalid argument')
        exit(-1)

    # Stage 1. Negotiation using TCP sockets
    TCP_Negotiation(req_code)

main()
