// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;
import java.util.List;
public final class PlaylistTimeline {
    public record Position(int index,long millis,boolean ended){}
    public static Position resolve(List<PlaylistTrack> tracks,int index,long elapsed,boolean loop){
        if(tracks.isEmpty())return new Position(0,0,true);
        index=Math.clamp(index,0,tracks.size()-1);elapsed=Math.max(0,elapsed);
        if(loop){long total=tracks.stream().mapToLong(t->t.seconds()*1000L).sum();elapsed%=total;}
        for(int n=0;n<=tracks.size();n++){
            long duration=tracks.get(index).seconds()*1000L;
            if(elapsed<duration)return new Position(index,elapsed,false);
            elapsed-=duration;
            if(++index>=tracks.size()){if(!loop)return new Position(tracks.size()-1,duration,true);index=0;}
        }
        throw new IllegalStateException("Invalid playlist timeline");
    }
}
