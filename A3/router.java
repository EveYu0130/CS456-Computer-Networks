import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;

public class router {

  // constants
  private static final int NBR_ROUTER = 5;

  // data members
  private static int router_id;
  private static String nse_host;
  private static int nse_port;
  private static int router_port;

  private static Map<Integer, Map<Integer, Integer>> neighbours = new HashMap<>();
  private static Map<Integer, Map<Integer, Integer>> topology_DB = new HashMap<>();


  private static DatagramSocket socket;
  private static PrintWriter log;

  // send packet through the socket to emulator
  public static void sendPacket(packet pkt) throws Exception {
      InetAddress IPAddress = InetAddress.getByName(nse_host);
      byte[] sendData = pkt.getUDPdata();
      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, nse_port);
      socket.send(sendPacket);
  }


  public static void main(String args[]) throws Exception {

    // check the validity of arguments, assign argument variables
    if (args.length != 4) {
        System.out.println("Invalid number of sender arguments");
        System.exit(1);
    }

    router_id = Integer.parseInt(args[0]);
    nse_host = args[1];
    nse_port = Integer.parseInt(args[2]);
    router_port = Integer.parseInt(args[3]);

    // Set up the socket to receive/send packets
    socket = new DatagramSocket(router_port);

    // Create log file
    log = new PrintWriter("router" + router_id + ".log", "UTF-8");

    // Send INIT packet to the Network State Emulator
    packet pkt_INIT = packet.createINIT(router_id);
    sendPacket(pkt_INIT);
    writeToLog("R" + router_id + " sends the INIT packet: router_id " + router_id);

    // Receive the circuit database sent by the Network State Emulator
    byte[] receiveData = new byte[1024];
    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
    socket.receive(receivePacket);
    writeToLog("R" + router_id + " receives the circuit DB");

    // Put the link cost sets for this router to topology DB
    if (!topology_DB.containsKey(router_id)) {
      topology_DB.put(router_id, new HashMap<>());
    }
    ByteBuffer buffer = ByteBuffer.wrap(receivePacket.getData());
		buffer.order(ByteOrder.LITTLE_ENDIAN);
    int nbr_link = buffer.getInt();
    for (int i = 0; i < nbr_link; i++) {
      int link = buffer.getInt();
      int cost = buffer.getInt();
      topology_DB.get(router_id).put(link, cost);
    }
    printTopologyDB();
    DijkstraAlg();

    // Start a new thread to receive packet from other router
    Thread Receive = new Thread(new Runnable() {
      public void run() {
        receive();
      }
    });
    Receive.start();

    // send HELLO packet to all its neighbors
    for (Integer link_id : topology_DB.get(router_id).keySet()) {
      packet pkt_HELLO = packet.createHELLO(router_id, link_id);
      sendPacket(pkt_HELLO);
      writeToLog("R" + router_id + " sends a HELLO packet: router_id " + router_id + ", link_id " + link_id);
    }

  }

  private static void receive() {
    while (true) {
      try {
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);

        // Receives a HELLO packet
        if (receivePacket.getLength() == 2*4) {
          packet pkt = packet.parseUDPdata_HELLO(receivePacket.getData());

          int routerId = pkt.getRouterId();
          int linkId = pkt.getLinkId();

          writeToLog("R" + router_id + " receives a HELLO packet: router_id " + routerId + ", link_id " + linkId);

          // Update neighbours DB
          if (!neighbours.containsKey(router_id)) {
            neighbours.put(router_id, new HashMap<>());
          }
          neighbours.get(router_id).put(routerId, linkId);

          if (!neighbours.containsKey(routerId)) {
            neighbours.put(routerId, new HashMap<>());
          }
          neighbours.get(routerId).put(router_id, linkId);

          // Respond to each HELLO packet by a set of LS PDUs containing its circuit database
          for (Map.Entry<Integer, Integer> linkcost : topology_DB.get(router_id).entrySet()) {
            packet pkt_LSPDU = packet.createLSPDU(router_id, router_id, linkcost.getKey(), linkcost.getValue(), linkId);
            sendPacket(pkt_LSPDU);
            writeToLog("R" + router_id + " sends the LSPDU packet: sender " + router_id + ", router_id " + router_id + ", link_id " + linkcost.getKey() + ", cost " + linkcost.getValue() + ", via " + linkId);
          }
        }

        // Receives a LSPDU packet
        if (receivePacket.getLength() == 5*4) {
          packet pkt = packet.parseUDPdata_LSPDU(receivePacket.getData());

          int sender = pkt.getSender();
          int routerId = pkt.getRouterId();
          int linkId = pkt.getLinkId();
          int cost = pkt.getCost();
          int via = pkt.getVia();

          writeToLog("R" + router_id + " receives a LSPDU packet: sender " + pkt.getSender() + ", router_id " + routerId + ", link_id " + linkId + ", cost " + cost + ", via " + via);

          // Update neighbours DB
          for (int router : topology_DB.keySet()) {
            if (router != routerId && topology_DB.get(router).containsKey(linkId)) {
              if (!neighbours.containsKey(router)) {
                neighbours.put(router, new HashMap<>());
                neighbours.get(router).put(routerId, linkId);
              } else if (!neighbours.get(router).containsKey(linkId)) {
                neighbours.get(router).put(routerId, linkId);
              }

              if (!neighbours.containsKey(routerId)) {
                neighbours.put(routerId, new HashMap<>());
                neighbours.get(routerId).put(router, linkId);
              } else if (!neighbours.get(routerId).containsKey(linkId)) {
                neighbours.get(routerId).put(router, linkId);
              }
            }
          }

          // Update the topology database
          if (topology_DB.containsKey(routerId)) {
            if (topology_DB.get(routerId).containsKey(linkId)) {
              continue;
            } else {
              topology_DB.get(routerId).put(linkId, cost);
            }
          } else {
            topology_DB.put(routerId, new HashMap<>());
            topology_DB.get(routerId).put(linkId, cost);
          }

          // send to all its neighbors the LS PDU with expections
          for (int router : neighbours.get(router_id).keySet()) {
            int link = neighbours.get(router_id).get(router);
            if (link != via) {
              packet pkt_LSPDU = packet.createLSPDU(router_id, routerId, linkId, cost, link);
              sendPacket(pkt_LSPDU);
              writeToLog("R" + router_id + " sends the LSPDU packet: sender " + router_id + ", router_id " + routerId + ", link_id " + linkId + ", cost " + cost + ", via " + link);
            }
          }
          printTopologyDB();
          DijkstraAlg();

        }
      }  catch (Exception e) {}
    }
  }

  // the Dijkstra algorithm using the topology DB to determine the shortest path cost to each destination R
  private static void DijkstraAlg() {
    // printNeighbours();

    ArrayList<Integer> N = new ArrayList<>(5);
    N.add(router_id);
    Map<Integer, Integer> D = new HashMap<>();
    Map<Integer, Integer> p = new HashMap<>();

    // initialize
    for (int routerId = 1; routerId <= NBR_ROUTER; routerId++) {
      D.put(routerId, Integer.MAX_VALUE);
      p.put(routerId, -1);
      if (neighbours.containsKey(router_id) && neighbours.get(router_id).containsKey(routerId)) {
        int linkId = neighbours.get(router_id).get(routerId);
        int cost = topology_DB.get(router_id).get(linkId);
        D.put(routerId, cost);
        p.put(routerId, routerId);
      }
    }

    // loop
    while (N.size() < NBR_ROUTER) {

      int w = 0;
      int min_cost = Integer.MAX_VALUE;

      // Find w not in N such that D(w) is a minimum
      for (int routerId = 1; routerId <= NBR_ROUTER; routerId++) {
        if (!N.contains(routerId) && D.get(routerId) < min_cost) {
          w = routerId;
          min_cost = D.get(routerId);
        }
      }
      N.add(w);

      // Update D(v) for all v adjacent to w and not in N
      if (neighbours.containsKey(w)) {
        for (Integer v : neighbours.get(w).keySet()) {
          int link = neighbours.get(w).get(v);
          if (!N.contains(v)) {
            int cost_wv = topology_DB.get(w).get(link);
            if (D.get(v) > D.get(w) + cost_wv) {
              D.put(v, D.get(w) + cost_wv);
              p.put(v, p.get(w));
            }
          }
        }
      }
    }

    printRIB(D, p);
  }

  private static void writeToLog(String s) {
    log.println(s);
    log.flush();
  }

  private static void printTopologyDB() {
    writeToLog("# Topology Database");
    for (Integer routerId : topology_DB.keySet()) {
      int nbr_link = topology_DB.get(routerId).values().size();
      if (nbr_link != 0) {
        writeToLog("R" + router_id + " -> R" + routerId + " nbr link " + nbr_link);
      }
      for (Map.Entry<Integer, Integer> linkcost : topology_DB.get(routerId).entrySet()) {
        writeToLog("R" + router_id + " -> R" + routerId + " link " + linkcost.getKey() + " cost " + linkcost.getValue());

      }
    }
  }

  private static void printNeighbours() {
    writeToLog("# Neighbours");
    for (Integer routerId : neighbours.keySet()) {
      writeToLog("R" + routerId + " neighbours: ");
      for (Integer router : neighbours.get(routerId).keySet()) {
        writeToLog("        R" + router + " , link " + neighbours.get(routerId).get(router));
      }
    }
  }

  private static void printRIB(Map<Integer, Integer> D, Map<Integer, Integer> p) {
    writeToLog("# RIB");
    for (int routerId = 1; routerId <= NBR_ROUTER; routerId++) {
      if (routerId == router_id) {
        writeToLog("R" + router_id + " -> R" + router_id + " -> Local, 0");
      } else {
        if (D.get(routerId) == Integer.MAX_VALUE) {
          writeToLog("R" + router_id + " -> R" + routerId + " -> INF, INF");
        } else {
          writeToLog("R" + router_id + " -> R" + routerId + " -> R" + p.get(routerId) + ", " + D.get(routerId));
        }

      }
    }
  }

}
