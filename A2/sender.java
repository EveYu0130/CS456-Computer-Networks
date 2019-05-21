import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

class sender {

    private static String emulatorAddr;
    private static int sendToPort;
    private static int receivePort;
    private static String filename;
    private static ArrayList<packet> packets = new ArrayList<>();
    private static DatagramSocket senderSocket;

    // read file into packets, add to the packets list
    public static void readFile() throws Exception{
        BufferedReader bufferReader = new BufferedReader(new FileReader(filename));
        File file = new File(filename);
//        System.out.println("file length: "+ file.length());

        int packetsSize = (int) Math.ceil((double) file.length() / (double) 500);
        int lastPacketSize = (int) file.length() % 500;

        for (int i = 0; i < packetsSize-1; i++) {
            char[] buffer = new char[500];
            bufferReader.read(buffer, 0, 500);
            packets.add(packet.createPacket(i, new String(buffer)));
        }

        if (lastPacketSize != 0) {
            char[] buffer = new char[lastPacketSize];
            bufferReader.read(buffer, 0, lastPacketSize);
            packets.add(packet.createPacket(packetsSize-1, new String(buffer)));
        }


    }

    // send the EOT packet to receiver
    public static void sendEOT() throws Exception {
        InetAddress IPAddress = InetAddress.getByName(emulatorAddr);
        packet EOTPacket = packet.createEOT(packets.size());
        byte[] sendData = EOTPacket.getUDPdata();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, sendToPort);
        senderSocket.send(sendPacket);
    }


    public static void main(String args[]) throws Exception {

        // check the validity of arguments, assign argument variables
        if (args.length != 4) {
            System.out.println("Invalid number of sender arguments");
            System.exit(1);
        }
        emulatorAddr = args[0];
        sendToPort = Integer.parseInt(args[1]);
        receivePort = Integer.parseInt(args[2]);
        filename = args[3];

        // create log files
        PrintWriter seqnumWriter = new PrintWriter("seqnum.log", "UTF-8");
        PrintWriter ackWriter = new PrintWriter("ack.log", "UTF-8");

        // read the input file to packets
        readFile();

        int baseIdx = 0;
        int nextIdx = 0;

        // set up the socket on the sender side to receive/send packets from/to emulator
        senderSocket = new DatagramSocket(receivePort);

//        System.out.println("Packets size: "+ packets.size());

        while (baseIdx < packets.size()) {

            // start sending packets within the window
            while ((nextIdx < baseIdx + 10) && (nextIdx < packets.size())) {

                // send packet
                InetAddress IPAddress = InetAddress.getByName(emulatorAddr);
//                System.out.println("Send packet " + nextIdx + " with seq number: " + packets.get(nextIdx).getSeqNum());
                byte[] sendData = packets.get(nextIdx).getUDPdata();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, sendToPort);
                senderSocket.send(sendPacket);

                // start timer
                if (baseIdx == nextIdx) {
                    senderSocket.setSoTimeout(500);
                }

                // log sent packet seq number
                seqnumWriter.println(packets.get(nextIdx).getSeqNum());

                nextIdx++;
            }

            // start receiving acks
            while (true) {

                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                try {
                    // recieve packet
                    senderSocket.receive(receivePacket);
                    int acknum = packet.parseUDPdata(receivePacket.getData()).getSeqNum();

//                    System.out.println("acked num " + acknum);

                    // log received ack
                    ackWriter.println(acknum);


                    int baseMod = baseIdx % 32;

                    if (acknum - baseMod >= 0 && acknum - baseMod <= 10) {
                        baseIdx += acknum - baseMod + 1;
                    } else if (acknum + 32 - baseMod >= 0 && acknum + 32 - baseMod <= 10) {
                        baseIdx += acknum + 32 - baseMod + 1;
                    } else if (acknum < baseIdx) {
                        continue;
                    }

                    if (baseIdx == nextIdx) {
                        // stop timer
                        senderSocket.setSoTimeout(0);
                        break;
                    } else {
                        // start timer
                        senderSocket.setSoTimeout(500);
                        break;
                    }
                } catch (SocketTimeoutException e) { // Timeout
//                    System.out.println("Timeout! Resend from packet " + baseIdx);
                    // restart timer
                    senderSocket.setSoTimeout(500);
                    nextIdx = baseIdx;
                    break;
                }
            }
        }

        // send EOT packet to receiver
        sendEOT();

        while (true) {
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            senderSocket.receive(receivePacket);
            int type = packet.parseUDPdata(receivePacket.getData()).getType();

            if (type == 2) { // received the EOT packet from receiver
                senderSocket.close();
                seqnumWriter.close();
                ackWriter.close();
                break;
            }
        }

    }

}
