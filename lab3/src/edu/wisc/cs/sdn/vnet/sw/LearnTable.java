package edu.wisc.cs.sdn.vnet.sw;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.sound.midi.VoiceStatus;

/*
 *  The entry to store the interface and the time packet comes in
 */
class TableEntry {
    // private field, the interface and time come in 
    private Iface       inIface;
    // time in mill seconds
    private long            time_init;

    /**
     * init a new entry
     * @param iface, the interface that packet come in 
     */
    public TableEntry(Iface inIface) {
        this.inIface = inIface;
        time_init = System.currentTimeMillis();
    }

    /*
     * set and get methods
     */

     /**
      * @param time new time
      */
    public void setTime(long time) {
        this.time_init = time;
    }

    /**
     * 
     * @return last updated time 
     */
    public long getTime() {
        return this.time_init;
    }

    /**
     * 
     * @param inIface new interface come in 
     */
    public void setIface(Iface inIface) {
        this.inIface = inIface;
    }

    /**
     * 
     * @return current interface
     */
    public Iface getIface() {
        return this.inIface;
    }
}


/**
 *  Learning table to store and update the max address, port and time out
 *  
 */

public class LearnTable implements Runnable {
    // static fields, fixed table size and timeout
    public static final int MAX_SIZE = 1024;
    public static final int TIME_OUT = 15;

    // public fields, a hashmap to store the mac address and related interface
    Map<MACAddress, TableEntry> macMap;
    Thread                          time_check;
    Runnable                        Tim_out;
    /**
     * init LearnTable
     */
    public LearnTable()  {
        macMap = new ConcurrentHashMap<MACAddress, TableEntry>();

        time_check = new Thread(this);
        time_check.start();
    }

    /**
     * add new mac address and interface to an entry
     * @param address the source mac address
     * @param intface the interface that source come in 
     */
    public void updateEntry(MACAddress address, Iface intface) {
        // when thre is no related mac address, created new entry
        if (!macMap.containsKey(address)) {
            macMap.put(address, new TableEntry(intface));
        } else {
        // update the interface and time in entry vice versa
            TableEntry entry = macMap.get(address);
            entry.setIface(intface);
            entry.setTime(System.currentTimeMillis());
        }
        // macMap.put(address, new TableEntry(intface));
    }

    /**
     * get the interface related to mac address
     * @param address the source mac address
     * @return the interface, if time out return null
     */
    public Iface getInterface(MACAddress address) {
        // check if in the table
        if (macMap.containsKey(address)) {
            // check whether time out
            if (Math.abs(macMap.get(address).getTime() - System.currentTimeMillis()) < TIME_OUT*1000) {
                return macMap.get(address).getIface();
            }
        }
        // if time out, return null and remove
        macMap.remove(address);
        return null;
    }

    /**
     * keep run every second to time out the entry, in a thread
     */
    @Override
    public void run() {
        while (true) {
            try{
                // the thread run every 500 seconds
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
            // travel and expire the time out entry
            for (MACAddress address : macMap.keySet()) {
                if (System.currentTimeMillis() - macMap.get(address).getTime() > TIME_OUT * 1000) {
                    macMap.remove(address);
                }
            }
        }
    }
    
}
