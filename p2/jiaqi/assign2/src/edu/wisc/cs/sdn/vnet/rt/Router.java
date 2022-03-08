package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device {
  /** Routing table for the router */
  private RouteTable routeTable;

  /** ARP cache for the router */
  private ArpCache arpCache;

  /**
   * Creates a router for a specific host.
   * 
   * @param host hostname for the router
   */
  public Router(String host, DumpFile logfile) {
    super(host, logfile);
    this.routeTable = new RouteTable();
    this.arpCache = new ArpCache();
  }

  /**
   * @return routing table for the router
   */
  public RouteTable getRouteTable() {
    return this.routeTable;
  }

  /**
   * Load a new routing table from a file.
   * 
   * @param routeTableFile the name of the file containing the routing table
   */
  public void loadRouteTable(String routeTableFile) {
    if (!routeTable.load(routeTableFile, this)) {
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
   * 
   * @param arpCacheFile the name of the file containing the ARP cache
   */
  public void loadArpCache(String arpCacheFile) {
    if (!arpCache.load(arpCacheFile)) {
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
   * 
   * @param etherPacket the Ethernet packet that was received
   * @param inIface     the interface on which the packet was received
   */
  public void handlePacket(Ethernet etherPacket, Iface inIface) {
    System.out.println("*** -> Received packet: " +
        etherPacket.toString().replace("\n", "\n\t"));

    /********************************************************************/
    /* Handle packets */
    
    // System.out.println("***1");
    // System.out.println(etherPacket.getEtherType());
    // System.out.println(Ethernet.TYPE_IPv4);

    if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) return;  // drop packet if not IPv4
    

    IPv4 packet = (IPv4) etherPacket.getPayload();
    IPv4 originalPacket = packet;
    // short checksum = packet.getChecksum();

    // packet = packet.setChecksum((short)0);
    // byte[] data = packet.serialize();
    // packet = (IPv4) packet.deserialize(data, 0, data.length);
    // if (checksum != packet.getChecksum()) return;  // drop packet if checksum incorrect

    originalPacket = originalPacket.setTtl((byte) (packet.getTtl() - 1));
    if (originalPacket.getTtl() <= (byte) 0) return;  // drop packet if decremented TTL is 0

    byte[] newData = originalPacket.serialize();
    originalPacket = (IPv4) originalPacket.deserialize(newData, 0, newData.length);
    etherPacket = (Ethernet) etherPacket.setPayload(originalPacket);

    for (Iface iface : interfaces.values()) {
      if (iface.getIpAddress() == originalPacket.getDestinationAddress()) return;  // drop packet if dest IP address matches one of the interfaces'
    }

    // Forwarding packet

    int nextHopIpAddress = originalPacket.getDestinationAddress();
    RouteEntry resultEntry = routeTable.lookup(nextHopIpAddress);
    if (resultEntry == null) return;  // drop packet if no entry in router table matches 

    // System.out.println("nextHopIpAddress: "+nextHopIpAddress);
    ArpEntry arpEntry = arpCache.lookup(nextHopIpAddress);

    // System.out.println("arpEntry: " + arpEntry.toString());
    if (arpEntry == null) return;  // drop packet if no entry in ARP table

    MACAddress nextHopMACAddress = arpEntry.getMac();
    MACAddress sourceMACAddress = resultEntry.getInterface().getMacAddress();

    // System.out.println(sourceMACAddress);
    // System.out.println("sourceMACAddress: " + sourceMACAddress.toString());
    // System.out.println("nextHopaddr: " + nextHopMACAddress.toString());

    etherPacket.setSourceMACAddress(sourceMACAddress.toBytes());
    etherPacket.setDestinationMACAddress(nextHopMACAddress.toBytes());

    sendPacket(etherPacket, resultEntry.getInterface());
    /********************************************************************/
  }
}
