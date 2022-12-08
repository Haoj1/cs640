package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;

import java.nio.ByteBuffer;
/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */

import java.util.List;
public class Router extends Device
{	
	/**
	 *  final static field
	 */
	private final static int ICMP_PADDING = 4;
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;

	/** ARP cache for the router */
	private ArpQueue arpQueue;
	
	/**  Dynamic Route */
	private boolean isDynamicRoute;

	/** the table to store the request recieved */
	private RipRequestTable ripRequestTable;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		this.arpQueue = new ArpQueue(this);
		this.isDynamicRoute = false;
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }

	/**
	 * 
	 * @return  arpCache
	 */
	public ArpCache getArpCache() {
		return this.arpCache;
	}
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */

		// check if ipv4 packet, if not, just drop
		switch(etherPacket.getEtherType())
		{
			case Ethernet.TYPE_IPv4:
				this.handleIpPacket(etherPacket, inIface);
				break;
			// handle ARP packet
			case Ethernet.TYPE_ARP:
				handleARPPacket(etherPacket, inIface);
				break;
		}
		
		// System.out.println("TTL = " + ipPacket.getTtl());
		/********************************************************************/
	}

	/**
	 * handle the ip packet
	 * @param etherPacket the ethernet packet
	 * @param inIface the port come in 
	 */
	public void handleIpPacket(Ethernet etherPacket, Iface inIface) {
		// check if ipv4 packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			System.out.println("Drop the packet, not ipv4");
			return;
		}
		// get the ipv4 packet 
		IPv4 ipPacket =(IPv4) etherPacket.getPayload();
		// do check sum
		short receivedChecksum = ipPacket.getChecksum();
		//reset checksum to zero before calculate check sum
		ipPacket.resetChecksum();
		// calculate checksum and store in a byte array
		byte[] serialized = ipPacket.serialize();
		ipPacket.deserialize(serialized, 0, serialized.length);
		if (receivedChecksum != ipPacket.getChecksum()) {
			System.out.println("Drop the packet, checksum");
			return;
		}
		// decrement ttl and then check whether greater than 0
		// ipPacket.setTtl((byte)(1));
		ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
		if (ipPacket.getTtl() <= 0) {
			System.out.println("Drop the packet, ttl=0");
			forwardICMPPacket(ipPacket, inIface, (byte)11, (byte)0);
			return;
		}
		// reset the check sum after ttl decrement
		ipPacket.resetChecksum();

		// if the the packet if Rip
		// System.out.println(isDynamicRoute);
		if (this.isDynamicRoute && ipPacket.getProtocol() == IPv4.PROTOCOL_UDP && 
			ipPacket.getDestinationAddress() == IPv4.toIPv4Address("224.0.0.9")) {
			UDP udpPacket = (UDP) ipPacket.getPayload();
			// check which port it is from
			if (udpPacket.getDestinationPort() == UDP.RIP_PORT) {
				System.out.println("revcieve a RIP packet");
				RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();
				// check which rip packet
				switch(ripPacket.getCommand()) {
					case RIPv2.COMMAND_REQUEST: {
						handleRipReq(ipPacket.getSourceAddress(), etherPacket.getSourceMACAddress(), inIface);
						return;
					}
					case RIPv2.COMMAND_RESPONSE: {
						handleRipResponse(ipPacket.getSourceAddress(), ripPacket, inIface);
						return;
					}
				}
			}
		}
		
		
		// cheak whther dstIP mathces any interface in this device
		for (Iface  iface : this.interfaces.values()) {
			if (ipPacket.getDestinationAddress() == iface.getIpAddress()) {
				System.out.println("Drop the packet, local interface");
				// if it is a TCP or UDP packet, just send Destination port unreachable
				if(ipPacket.getProtocol() == IPv4.PROTOCOL_TCP ||
						ipPacket.getProtocol() == IPv4.PROTOCOL_UDP){
					System.out.println("Destination port unreachable");
					forwardICMPPacket(ipPacket, inIface, (byte)3, (byte)3);
				} else if (ipPacket.getProtocol() == IPv4.PROTOCOL_ICMP) {
					// if it is an Echo request, send back 
					ICMP icmpPacket = (ICMP) ipPacket.getPayload();
					if (icmpPacket.getIcmpType() == ICMP.TYPE_ECHO_REQUEST) {
						System.out.println("Echo reply");
						forwardICMPPacket(ipPacket, inIface, (byte)0, (byte)0);
					}
				}
				return;
			}
		}
		forwardIpPacket(etherPacket, inIface);
	}

	public void forwardIpPacket (Ethernet etherPacket, Iface inIface) {
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			System.out.println("Drop the packet, not ipv4");
			return;
		}
		IPv4 ipPacket =(IPv4) etherPacket.getPayload();
		// find the match entry
		RouteEntry match = routeTable.lookup(ipPacket.getDestinationAddress());
		// if there is no match, or the next interface is the come in interface, drop
		if (match == null ) {
			// System.out.println(IpPacket.getDestinationAddress());
			System.out.println("Drop the packet, no match");
			//Destination net unreachable
			forwardICMPPacket(ipPacket, inIface, (byte)3, (byte)0);
			return;
		}
		// Make sure we don't sent a packet back out the interface it came in
		if (match.getInterface().equals(inIface)) {
			return;
		}
		//get the gateway ip adress
		int gateway = match.getGatewayAddress(); 
		// if no gateway
		if (gateway == 0) {
			System.out.println("no other gateway, use destination");
			gateway = ipPacket.getDestinationAddress();
			// set the src mac address
			// etherPacket.setSourceMACAddress(match.getInterface().getMacAddress().toBytes());
		} 
		
		// System.out.println(match.getInterface().getMacAddress());
		etherPacket.setSourceMACAddress(match.getInterface().getMacAddress().toBytes());
		// get the dst mac address
		ArpEntry dstArpEntry = arpCache.lookup(gateway);
		// if not match, return
		if (dstArpEntry == null) {
			System.out.println("Drop the packet, no mac address");
			// Destination host unreachable
			
			// do not send ICMP immediately
			// forwardICMPPacket(ipPacket, inIface, (byte)3, (byte)1);
			genARPRequest(gateway);
			this.arpQueue.storePacket(etherPacket, gateway, inIface);
			return;
		}
		
		etherPacket.setDestinationMACAddress(dstArpEntry.getMac().toBytes());
		
		sendPacket(etherPacket, match.getInterface());

	}	

	/**
	 * forward an ICMP packet
	 * @param ipPacket
	 * @param inIface
	 * @param type the type num
	 * @param code the code num
	 */
	public void forwardICMPPacket(IPv4 ipPacket, Iface inIface, byte type, byte code) {
		Ethernet ether = new Ethernet(); 
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data(); 
		ether.setPayload(ip); 
		ip.setPayload(icmp); 
		icmp.setPayload(data);
		// set EtherType
		ether.setEtherType(Ethernet.TYPE_IPv4);
		// set ip header
		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_ICMP);

		// set source and destination address
		if (type == 0) {
			// if it is echo request, set address to Destination Address;
			ip.setSourceAddress(ipPacket.getDestinationAddress());
		} else {
			// choose the come in interface as the sourse address
			ip.setSourceAddress(inIface.getIpAddress());
		}
		// send back to SourceAddress
		ip.setDestinationAddress(ipPacket.getSourceAddress());

		// set the header
		icmp.setIcmpType(type);
		icmp.setIcmpCode(code);

		// set the payload
		if (type == 0) {
			// when is echo reple message
			ICMP ICMPPacket = (ICMP) ipPacket.getPayload();
			byte[] ICMPpayload = ICMPPacket.getPayload().serialize();
			data.setData(ICMPpayload);
		} else {
			// when other message
			System.out.println("ICMP error message");
			int payload_size = ICMP_PADDING+ipPacket.getHeaderLength()*4+8;
			byte[] ICMPpayload = new byte[payload_size];
			byte[] serialized = ipPacket.serialize();
			for (int i = 0; i < payload_size-ICMP_PADDING; i++) {
				ICMPpayload[i+ICMP_PADDING] = serialized[i];
			}
			data.setData(ICMPpayload);
		}
		System.out.println("ICMP packet forwarded");
		forwardIpPacket(ether, null);
	}

	/********************************************************************************
	 * ARP method field
	 * ******************************************************************************/

	/**
	 * handle ARP packet
	 * @param etherPacket
	 * @param inIface
	 */
	public void handleARPPacket(Ethernet etherPacket, Iface inIface) {
		// if not arp packet, drop
		if (etherPacket.getEtherType() != Ethernet.TYPE_ARP) {
			return;
		}
		//get patload
		ARP arpPacket = (ARP)etherPacket.getPayload();
		if (arpPacket.getOpCode() == ARP.OP_REQUEST) {
			System.out.println("recieve APR request, generate ARP reply");
			genARPReply(etherPacket, inIface);
		} else if (arpPacket.getOpCode() == ARP.OP_REPLY){
			// handle APR reply
			System.out.println("recieve APR reply, update arp table");
			int sourceIP = ByteBuffer.wrap(arpPacket.getSenderProtocolAddress()).getInt();
			byte[] senderMacAddr = arpPacket.getSenderHardwareAddress();
			arpCache.insert(new MACAddress(senderMacAddr), sourceIP);
			
			this.arpQueue.handleAPRreply(sourceIP, inIface, senderMacAddr);
			System.out.println(this.arpCache);
		}
		

	}

	/**
	 * generate APR reply
	 * @param arpReq
	 * @param inIface
	 */
	public void genARPReply(Ethernet etherPacket, Iface inIface) {
		ARP arpReq = (ARP)etherPacket.getPayload();
		// obtain targetip
		int targetIp = ByteBuffer.wrap(arpReq.getTargetProtocolAddress()).getInt();
		// drop if target ip if not match
		if (targetIp != inIface.getIpAddress()) {
			System.out.println("APR request not equal to interface");
			return;
		}
		Ethernet etherHeader = new Ethernet();
		ARP arpHeader=new ARP();
		//set ethernet header
		etherHeader.setEtherType(Ethernet.TYPE_ARP);
		etherHeader.setSourceMACAddress(inIface.getMacAddress().toBytes());
		etherHeader.setDestinationMACAddress(etherPacket.getSourceMACAddress());
		//set arpheader
		arpHeader.setHardwareType(ARP.HW_TYPE_ETHERNET);
		arpHeader.setProtocolType(ARP.PROTO_TYPE_IP);
		arpHeader.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
		arpHeader.setProtocolAddressLength((byte)4);
		arpHeader.setOpCode(ARP.OP_REPLY);
		arpHeader.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
		arpHeader.setSenderProtocolAddress(inIface.getIpAddress());
		arpHeader.setTargetHardwareAddress(arpReq.getSenderHardwareAddress());
		arpHeader.setTargetProtocolAddress(arpHeader.getSenderProtocolAddress());
		// set the payload of ethernet
		etherHeader.setPayload(arpHeader);
		// send the packet to the original interface
		sendPacket(etherHeader, inIface);
	}



	/**
	 * generate ARP request based on the ip
	 * @param etherPacket
	 * @param inIface
	 */
	public void genARPRequest(int targetIp) {
		String broadcastMacAddr = "FF:FF:FF:FF:FF:FF";
		byte[] TargetHardwareAddress = {0,0,0,0,0,0};
		Ethernet etherHeader = new Ethernet();
		ARP arpHeader=new ARP();
		// get the route table entry
		RouteEntry bestMatch = this.routeTable.lookup(targetIp);
		if (bestMatch == null) {
			System.out.println("genARPRequest: Fail to get RouteEntry");
		}
		Iface sendIface = bestMatch.getInterface();
		//set ethernet header
		etherHeader.setEtherType(Ethernet.TYPE_ARP);
		etherHeader.setSourceMACAddress(sendIface.getMacAddress().toBytes());
		etherHeader.setDestinationMACAddress(broadcastMacAddr);
		//set arpheader
		arpHeader.setHardwareType(ARP.HW_TYPE_ETHERNET);
		arpHeader.setProtocolType(ARP.PROTO_TYPE_IP);
		arpHeader.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
		arpHeader.setProtocolAddressLength((byte)4);
		arpHeader.setOpCode(ARP.OP_REQUEST);
		arpHeader.setSenderHardwareAddress(sendIface.getMacAddress().toBytes());
		arpHeader.setSenderProtocolAddress(sendIface.getIpAddress());
		arpHeader.setTargetHardwareAddress(TargetHardwareAddress);
		arpHeader.setTargetProtocolAddress(targetIp);
		// set the payload of ethernet
		etherHeader.setPayload(arpHeader);
		System.out.println("genARPRequest: send ARP request");
		sendPacket(etherHeader, sendIface);
	}



	/********************************************************************************
	 * Rip method field
	 * ******************************************************************************/

	/**
	 * initiate the dynamic route table
	 */
	public void initRIP() {
		RipRequestTable reqTable = new RipRequestTable();
		this.isDynamicRoute = true;
		// load subnet into routetable
		for (Iface iface : this.interfaces.values()) {
			int dstIP = iface.getIpAddress() & iface.getSubnetMask();
			this.routeTable.insert(dstIP, 0, iface.getSubnetMask(), iface, 0);
		}
		// send request
		for (Iface iface : this.interfaces.values()) {
			genRipReq(iface);
		}
		// initiate the request list
		// this.ripRequestTable = new RipRequestTable();

		// initiate a thread to time out and broadcast

		Thread timeManage = new Thread(new tableManage());
		timeManage.start();

		System.out.println("dynamic route table initiate");
		System.out.println("*********************************************************");
		System.out.print(this.routeTable.toString());
		System.out.println("*********************************************************");

	}

	/**
	 * generate rip packet using different ip and mac
	 * @param dstIP
	 * @param srcIP
	 * @param dstMac
	 * @param srcMac
	 * @return
	 */
	public Ethernet genRipPacket(int dstIP, int srcIP, byte[] dstMac, byte[] srcMac, byte command) {
		Ethernet ether = new Ethernet(); 
		IPv4 ip = new IPv4();
		UDP udpPacket = new UDP();
		RIPv2 ripv2Packet = new  RIPv2();
		//set payload for each packet
		ether.setPayload(ip); 
		ip.setPayload(udpPacket); 
		udpPacket.setPayload(ripv2Packet);
		ripv2Packet.setCommand(command);

		// if it is a response, add each route entry to the rip entry
		if (command == RIPv2.COMMAND_RESPONSE) {
			for (RouteEntry routeEntry : this.routeTable.getRouteEntries()) {
				RIPv2Entry newRipEntry = new RIPv2Entry(routeEntry.getDestinationAddress(),routeEntry.getMaskAddress(),
					routeEntry.getDistance());
				newRipEntry.setNextHopAddress(routeEntry.getGatewayAddress());
				ripv2Packet.addEntry(newRipEntry);
			}
		}
		// set udp port
		udpPacket.setSourcePort(UDP.RIP_PORT);
		udpPacket.setDestinationPort(UDP.RIP_PORT);

		//set ip src and dst ip
		ip.setSourceAddress(srcIP);
		ip.setDestinationAddress(dstIP);
		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_UDP);
		
		// set ethernet mac address
		ether.setDestinationMACAddress(dstMac);
		ether.setSourceMACAddress(srcMac);
		ether.setEtherType(Ethernet.TYPE_IPv4);

		// reset checksum
		ip.resetChecksum();
		ether.resetChecksum();
		udpPacket.resetChecksum();
		return ether;
	}

	/**
	 * generate request
	 * @param sendiface
	 */
	public void genRipReq(Iface sendiface) {
		byte[] dstMac = Ethernet.toMACAddress("FF:FF:FF:FF:FF:FF");
		int dstIP = IPv4.toIPv4Address("224.0.0.9");
		Ethernet ether = genRipPacket(dstIP, sendiface.getIpAddress(), 
			dstMac, sendiface.getMacAddress().toBytes(), RIPv2.COMMAND_REQUEST);
		this.sendPacket(ether, sendiface);
	}


	/**
	 * handle a RIP request
	 * @param srcIP
	 * @param srcMac
	 * @param inIface
	 */
	public void handleRipReq(int srcIP, byte[] srcMac, Iface inIface) {
		// make sure route table is dynamic
		if (!this.isDynamicRoute) {
			System.out.println("handleRipReq: dynamic route table no initiate");
			return;
		}
		// send a response back to the source
		genRipResponse(srcIP, inIface.getIpAddress(), srcMac, inIface.getMacAddress().toBytes(), inIface);
	}

	/**
	 * handle RIP response
	 * @param srcIP
	 * @param ripv2
	 * @param inIface
	 */
	public synchronized void handleRipResponse(int srcIP, RIPv2 ripv2, Iface inIface) {
		// make sure route table is dynamic
		if (!this.isDynamicRoute) {
			System.out.println("handleRipResponse: dynamic route table no initiate");
			return;
		}
		// a tag to record if updated
		boolean isUpdated = false;
		List<RIPv2Entry> riPv2Entries = ripv2.getEntries();
		for (RIPv2Entry ripv2Entry : riPv2Entries) {
			RouteEntry routeEntry = this.routeTable.lookup(ripv2Entry.getAddress());
			// if no entry, insert a new entry in routetable
			if (routeEntry == null) {
				this.routeTable.insert(ripv2Entry.getAddress(), srcIP, 
					ripv2Entry.getSubnetMask(), inIface, ripv2Entry.getMetric()+1);
				isUpdated = true;
			} else {
				// if the new cost is lower, update
				if (routeEntry.getDistance() > ripv2Entry.getMetric()+1) {
					this.routeTable.update(ripv2Entry.getAddress(), srcIP, ripv2Entry.getSubnetMask(),
						inIface, ripv2Entry.getMetric()+1);
					isUpdated = true;
				} else {
					// just update the time
				    routeEntry.setTimeStamp(System.currentTimeMillis());
			    }
				
			}

		}
		// broadcast if updated
		if (isUpdated) {
			System.out.println("handleRipResponse: update the routetable, broadcast");
			broadcastRipRsp();
		}
	}


	/**
	 * generate a rip response
	 * @param dstIP
	 * @param srcIP
	 * @param dstMac
	 * @param srcMac
	 * @param sendIface
	 */
	public void genRipResponse(int dstIP, int srcIP, byte[] dstMac, byte[] srcMac, Iface sendIface) {
		// make sure route table is dynamic
		if (!this.isDynamicRoute) {
			System.out.println("genRipResponse: dynamic route table no initiate");
			return;
		}
		Ethernet ether = genRipPacket(dstIP, srcIP, dstMac, srcMac, RIPv2.COMMAND_RESPONSE);
		sendPacket(ether, sendIface);
	}

	// broadcast a response to all interface
	public synchronized void broadcastRipRsp() {
		// make sure route table is dynamic
		if (!this.isDynamicRoute) {
			System.out.println("genRipResponse: dynamic route table no initiate");
			return;
		}
		byte[] dstMac = Ethernet.toMACAddress("FF:FF:FF:FF:FF:FF");
		int dstIP = IPv4.toIPv4Address("224.0.0.9");
		for (Iface iface : this.interfaces.values()) {
			int srcIP = iface.getIpAddress();
			byte[] srcMac = iface.getMacAddress().toBytes();
			genRipResponse(dstIP, srcIP, dstMac, srcMac, iface);
		}
	}

	/**
	 * a thread to handle time out
	 */
	class tableManage implements Runnable {

		// static field for broadcast and time out
		long timeStampBroadcast;
		final static int TIME_OUT = 30*1000;
		final static int BROADCAST = 10*1000;

		/**
		 * constructor
		 */
		public tableManage() {
			timeStampBroadcast = System.currentTimeMillis();
		}

		@Override
		public void run() {
			while(true) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				// clear time out entry
				synchronized(routeTable) {
					for (RouteEntry routeEntry : routeTable.getRouteEntries()) {
						if ((System.currentTimeMillis() - routeEntry.getTimeStamp() > TIME_OUT) && 
							routeEntry.getDistance() > 0) {
							routeTable.remove(routeEntry.getDestinationAddress(), routeEntry.getMaskAddress());
						}
					}
				}
				// broad cast every 10 seconds
				if (System.currentTimeMillis() - timeStampBroadcast > BROADCAST) {
					broadcastRipRsp();
					timeStampBroadcast = System.currentTimeMillis();
				}
			}
		}
	}


}


