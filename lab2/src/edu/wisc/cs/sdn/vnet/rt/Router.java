package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.ICMP;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
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
			return;
		}
		// reset the check sum after ttl decrement
		ipPacket.resetChecksum();
		// for (Iface  iface : this.interfaces.values()) {
		// 	if (ipPacket.getDestinationAddress() == iface.getIpAddress()) {
		// 		return;
		// 	}
		// }
		// cheak whther dstIP mathces any interface in this device
		for (Iface  iface : this.interfaces.values()) {
			if (ipPacket.getDestinationAddress() == iface.getIpAddress()) {
				System.out.println("Drop the packet, local interface");
				return;
			}
		}
		forwardPacket(etherPacket, inIface);
		// System.out.println("TTL = " + ipPacket.getTtl());
		/********************************************************************/
	}

	public void forwardPacket (Ethernet etherPacket, Iface inIface) {
		IPv4 IpPacket = (IPv4) etherPacket.getPayload();
		// find the match entry
		RouteEntry match = routeTable.lookup(IpPacket.getDestinationAddress());
		// if there is no match, or the next interface is the come in interface, drop
		if (match == null || match.getInterface().equals(inIface)) {
			// System.out.println(IpPacket.getDestinationAddress());
			System.out.println("Drop the packet, no match");
			return;
		}
		//get the gateway ip adress
		int gateway = match.getGatewayAddress(); 
		// if no gateway
		if (gateway == 0) {
			System.out.println("no other gateway, use destination");
			gateway = IpPacket.getDestinationAddress();
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
			return;
		}
		etherPacket.setDestinationMACAddress(dstArpEntry.getMac().toBytes());
		
		sendPacket(etherPacket, match.getInterface());

	}
}
