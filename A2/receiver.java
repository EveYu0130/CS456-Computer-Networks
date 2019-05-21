import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class receiver {

    private static String emulatorAddr;
    private static int sendToPort;
    private static int receivePort;
    private static String filename;
    private static DatagramSocket receiverSocket;

    // send packet through the socket from receiver to emulator
    public static void sendPacket(packet pkt) throws Exception {
        InetAddress IPAddress = InetAddress.getByName(emulatorAddr);
        byte[] sendData = pkt.getUDPdata();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, sendToPort);
        receiverSocket.send(sendPacket);
    }

    public static void main(String args[]) throws Exception {

        // check validity of arguments, assign argument variables
        if (args.length != 4) {
            System.out.println("Invalid number of receiver arguments");
            System.exit(1);
        }
        emulatorAddr = args[0];
        sendToPort = Integer.parseInt(args[1]);
        receivePort = Integer.parseInt(args[2]);
        filename = args[3];

        // create log files
        PrintWriter arrivalWriter = new PrintWriter("arrival.log", "UTF-8");
        PrintWriter outputWriter = new PrintWriter(filename, "UTF-8");

        int expectedSeqNum = 0;
        int recentAckNum = 0;
        boolean has_acked = false;

        // set up the socket on the receiver side to receiver/send packet from/to emulator
        receiverSocket = new DatagramSocket(receivePort);

        // start receiving packet
        while (true) {

            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            receiverSocket.receive(receivePacket);
            packet pkt = packet.parseUDPdata(receivePacket.getData());

            int type = pkt.getType();
            if (type == 1) { // receive the normal packet with data

//                System.out.println("Receive packet with seq num: "+ pkt.getSeqNum());

                arrivalWriter.println(pkt.getSeqNum());

                if (pkt.getSeqNum() == expectedSeqNum) { // received the expected packet

                    outputWriter.print(new String(pkt.getData()));

                    packet ack = packet.createACK(expectedSeqNum);

                    // send the ack packet
                    sendPacket(ack);
                    has_acked = true;
                    recentAckNum = expectedSeqNum;
                    expectedSeqNum = (expectedSeqNum + 1) % 32;

                } else { // received an unexpected packet

                    // do not send ack if receiver has not sent any acks before
                    if (has_acked) {
                        packet ack = packet.createACK(recentAckNum);
                        sendPacket(ack);
                    }
                }
            } else if (type == 2) { // receive the EOT packet

                packet EOTPacket = packet.createEOT(expectedSeqNum);

                // send the EOT packet to sender
                sendPacket(EOTPacket);

                receiverSocket.close();
                arrivalWriter.close();
                outputWriter.close();
                break;
            }
        }
    }

}
