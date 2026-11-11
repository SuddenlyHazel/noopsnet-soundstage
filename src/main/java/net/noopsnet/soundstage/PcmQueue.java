// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import java.util.ArrayDeque;

/** Bounded by PCM duration, not the decoder's arbitrary callback packet size. */
public final class PcmQueue {
    public record Packet(byte[] samples,long pts,int generation){}
    private final ArrayDeque<Packet> packets=new ArrayDeque<>();
    private final int capacityBytes;
    private int bytes;
    public PcmQueue(int capacityBytes){if(capacityBytes<4)throw new IllegalArgumentException();this.capacityBytes=capacityBytes;}
    /** Returns discarded packets. Normal decoder lookahead must fit without discards. */
    public synchronized int offer(Packet packet){
        if(packet.samples.length==0||packet.samples.length%4!=0||packet.samples.length>capacityBytes)throw new IllegalArgumentException("Invalid stereo PCM packet");
        int dropped=0;
        while(bytes+packet.samples.length>capacityBytes){bytes-=packets.removeFirst().samples.length;dropped++;}
        packets.addLast(packet);bytes+=packet.samples.length;notifyAll();return dropped;
    }
    public synchronized Packet poll(long timeoutMillis)throws InterruptedException{
        if(packets.isEmpty())wait(Math.max(1,timeoutMillis));
        var packet=packets.pollFirst();if(packet!=null)bytes-=packet.samples.length;return packet;
    }
    public synchronized void clear(){packets.clear();bytes=0;notifyAll();}
    public synchronized int bytes(){return bytes;}
}
