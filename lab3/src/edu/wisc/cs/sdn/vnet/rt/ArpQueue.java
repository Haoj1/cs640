package edu.wisc.cs.sdn.vnet.rt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;


class ArpPacketEntry {
    int count;
    Iface iface;
    long timeStamp;
    List<Ethernet> waitingPackets;

    public ArpPacketEntry() {

    }
    public ArpPacketEntry(int count, Iface iface,long timeStamp) {
        this.count = count;
        this.iface = iface;
        this.timeStamp = timeStamp;
        waitingPackets = new LinkedList<Ethernet>();
    }

}

/**
 * A data stucture to store the waiting packet
 */
public class ArpQueue implements Runnable {

    /**
     * instance field
     */
    // the corresponding router
    Router router;
    // hashtable to store the count and packet
    Map<Integer, Byte> countMap;
    // the hashmap to store the packet
    // Map<Integer, List<Ethernet>> packetMap;
    Map<Integer, Map<Ethernet, Iface>> packetMap;
    // the hashmap to store the each ip time stamp
    Map<Integer, Long> timeStMap;

    // a map to store the ip and entry
    Map<Integer, ArpPacketEntry> packetEntries;
    // Thread to resend request
    Thread  genAPRThread;

    /**
     * @param rt the router corresponding to the queue
     */
    public ArpQueue(Router rt) {
        this.router = rt;
        countMap = new ConcurrentHashMap<Integer, Byte>();
        packetMap = new ConcurrentHashMap<Integer, Map<Ethernet, Iface>>();
        timeStMap = new ConcurrentHashMap<Integer, Long>();
        packetEntries = new ConcurrentHashMap<Integer, ArpPacketEntry>();
        genAPRThread = new Thread(this);
        genAPRThread.start();
    }

    /**
     * 
     * @param ip
     * @return if ip in the map
     */
    public boolean containsIP(int ip) {
        return packetEntries.containsKey(ip);
    }

    /**
     * store new packet corresponding its ip into map
     * @param etherPacket
     * @param ip
     */
    public  void storePacket(Ethernet etherPacket, int ip, Iface inIface) {
        
        synchronized(this.packetEntries) {
            // when ip not in map, initiate a new entry
            if (!containsIP(ip)){
                // countMap.put(ip, (byte)1);
                // Map<Ethernet, Iface> packetList = new ConcurrentHashMap<Ethernet, Iface>();
                // packetList.put(etherPacket, inIface);
                // packetMap.put(ip, packetList);
                // timeStMap.put(ip, System.currentTimeMillis());
                ArpPacketEntry arpPacketEntry = new ArpPacketEntry(1, inIface, System.currentTimeMillis());
                arpPacketEntry.waitingPackets.add(etherPacket);
                this.packetEntries.put(ip, arpPacketEntry);
                // router.genARPRequest(ip);
            } else {
                // if contains the ip, just simple add to the map
                ArpPacketEntry arpPacketEntry = packetEntries.get(ip);
                arpPacketEntry.waitingPackets.add(etherPacket);
            }
        }
     
    }

    /**
     * handle the ARP reply message 
     * @param ip the target ip
     * @param intIface the message come in
     * @param macAddr  the target mac addr
     */
    public void handleAPRreply(int ip, Iface intIface, byte[] macAddr) {
        synchronized(this.packetEntries) {
             // if not contain target ip just return
            if (!containsIP(ip)) {
                System.out.println("handleAPRreply: no packet need to be forwarded");
                return;
            }
            ArpPacketEntry arpPacketEntry= packetEntries.remove(ip);
            // send waiting packet to the destination
            for (Ethernet etherPacket : arpPacketEntry.waitingPackets) {
                etherPacket.setDestinationMACAddress(macAddr);
                router.sendPacket(etherPacket, intIface);
            }
        }
    }

    /**
     *  keep sending request every second
     */
    @Override
    public void run() {
        while(true) {
            // do every second
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                
                System.out.println(e.getMessage());
            }
            synchronized(this.packetEntries) {
                for (int ip : this.packetEntries.keySet()) {
                    ArpPacketEntry arpPacketEntry = packetEntries.get(ip);
                    // when time out
                    if ((System.currentTimeMillis() - arpPacketEntry.timeStamp) >= 1000) {
                        if (arpPacketEntry.count >= 3) {
                            System.out.println("ARPThread: time out drop the packet");
                            // send ICMP back to the each source
                            for (Ethernet ethernetPacket : arpPacketEntry.waitingPackets) {
                                
                                IPv4 ipv4Packet = (IPv4) ethernetPacket.getPayload();
                                // if (arpPacketEntry.iface == null) {
                                //     System.out.println("arpPacketEntry null");
                                //     continue;
                                // }
                                router.forwardICMPPacket(ipv4Packet, arpPacketEntry.iface, (byte)3, (byte)1);
                            }
                            // clear all the packet
                            packetEntries.remove(ip);
                        } else {
                            System.out.println("ARPThread: resend request");
                            // send request again and increment count
                            arpPacketEntry.count++;
                            arpPacketEntry.timeStamp = System.currentTimeMillis();
                            router.genARPRequest(ip);
                            // byte count = countMap.get(ip);
                            // timeStMap.put(ip, System.currentTimeMillis());
                            // countMap.put(ip, (byte)(count+1));
                            // router.genARPRequest(ip);
                        }
                    
                    }
                }
            }
            

        }
        
    } 

}
