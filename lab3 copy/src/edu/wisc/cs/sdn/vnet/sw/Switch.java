package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{	
	// instane field, the learning table for a switch
	private LearnTable learnTable;

	long time;

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
		learnTable = new LearnTable();
		// time = 0;
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
		//get the mac address of source and dest
		MACAddress source = etherPacket.getSourceMAC();
		MACAddress destination = etherPacket.getDestinationMAC();
		
		//update the learning table when any packet arrive
		learnTable.updateEntry(source, inIface);
		Iface port;
		// System.out.println((System.currentTimeMillis()-time)/1000);
		// time = System.currentTimeMillis();

		//if no matched entry in the table, then broadcast
		if ((port = learnTable.getInterface(destination)) == null) {
			broadcast(etherPacket, inIface);
			System.out.println("This packet is broadcast");
		} else {
			// send directly
			sendPacket(etherPacket, port);
			System.out.println("This packet is sent directly");
		}


		/********************************************************************/
	}

	/**
	 * broadcast on every interface if no related entry in table 
	 * @param ethernetPacket the packet
	 * @param inTIface the source interface
	 */
	public void broadcast(Ethernet ethernetPacket, Iface inTIface) {
		// broadcast at all interfaces except the one packet come in
		for (Iface intface : this.interfaces.values()) {
			if (inTIface != intface) {
				sendPacket(ethernetPacket, intface);
			}
		}
	}
}
