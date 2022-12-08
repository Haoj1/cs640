package edu.wisc.cs.sdn.vnet.rt;

import java.util.LinkedList;
import java.util.List;

import edu.wisc.cs.sdn.vnet.Iface;

/**
 * store each Rip request 
 */
class RipRequestEntry {
    /**
     * public field
     */
    int requestIP;
    byte[] requestMacAddress;
    Iface iface;

    /**
     * 
     */
    public RipRequestEntry() {

    }

    /**
     * 
     * @param requestIP
     * @param requestMacAddress
     * @param iface
     */
    public RipRequestEntry (int requestIP, byte[] requestMacAddress, Iface iface) {
        this.requestIP = requestIP;
        this.requestMacAddress = requestMacAddress;
        this.iface = iface;
    }
}

public class RipRequestTable {
    
    /**
     * public field, a list to store all rip requests
     */
    List<RipRequestEntry> reqList;

    /**
     * init a new table
     */
    public RipRequestTable() {
        reqList = new LinkedList<RipRequestEntry>();
    }

    /**
     * insert a new request
     * @param requestIP
     * @param requestMacAddress
     * @param iface
     */
    public void insertRipReq(int requestIP, byte[] requestMacAddress, Iface iface) {
        RipRequestEntry newEntry = new RipRequestEntry(requestIP, requestMacAddress, iface);
        synchronized(this.reqList) {
            this.reqList.add(newEntry);
        }   
    }
}
