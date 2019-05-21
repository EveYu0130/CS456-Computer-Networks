// common packet class used by both SENDER and RECEIVER

import java.nio.*;

public class packet {

	// data members
	private int type;
	private int sender;
	private int router_id;
	private int link_id;
	private int cost;
	private int via;


	//////////////////////// CONSTRUCTORS //////////////////////////////////////////

	// hidden constructor to prevent creation of invalid packets
	private packet(int Type, int Sender, int Router_id, int Link_id, int Cost, int Via) throws Exception {

		type = Type; // INIT = 1, HELLO = 0, LSPDU = 2
		sender = Sender;
		router_id = Router_id;
		link_id = Link_id;
		cost = Cost;
		via = Via;
	}

  // special packet constructors to be used in place of hidden constructor
	public static packet createINIT(int router_id) throws Exception {
		return new packet(0, 0, router_id, 0, 0, 0);
	}

	public static packet createHELLO(int router_id, int link_id) throws Exception {
		return new packet(1, 0, router_id, link_id, 0, 0);
	}

	public static packet createLSPDU(int sender, int router_id, int link_id, int cost, int via) throws Exception {
		return new packet(2, sender, router_id, link_id, cost, via);
	}

	///////////////////////// PACKET DATA //////////////////////////////////////////

	public int getType() {
		return type;
	}

	public int getRouterId() {
		return router_id;
	}

	public int getLinkId() {
		return link_id;
	}

	public int getSender() {
		return sender;
	}

	public int getCost() {
		return cost;
	}

	public int getVia() {
		return via;
	}

	//////////////////////////// UDP HELPERS ///////////////////////////////////////

	public byte[] getUDPdata() {
		ByteBuffer buffer = ByteBuffer.allocate(4);
		if (type == 0) {
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			buffer.putInt(router_id);
		} else if (type == 1) {
			buffer = ByteBuffer.allocate(2*4);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			buffer.putInt(router_id);
			buffer.putInt(link_id);
		} else { // type == 2
			buffer = ByteBuffer.allocate(5*4);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			buffer.putInt(sender);
			buffer.putInt(router_id);
			buffer.putInt(link_id);
			buffer.putInt(cost);
			buffer.putInt(via);
		}
		return buffer.array();
	}

	public static packet parseUDPdata_HELLO(byte[] UDPdata) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		int router_id = buffer.getInt();
		int link_id = buffer.getInt();
		return packet.createHELLO(router_id, link_id);
	}

	public static packet parseUDPdata_LSPDU(byte[] UDPdata) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		int sender = buffer.getInt();
		int router_id = buffer.getInt();
		int link_id = buffer.getInt();
		int cost = buffer.getInt();
		int via = buffer.getInt();
		return packet.createLSPDU(sender, router_id, link_id, cost, via);
	}
}
