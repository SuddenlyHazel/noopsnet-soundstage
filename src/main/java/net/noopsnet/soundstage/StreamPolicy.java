// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;
import java.net.URI;
import java.util.Collection;
public final class StreamPolicy {
    private StreamPolicy(){}
    public static boolean allowed(String text,Collection<? extends String> hosts){
        if(text.isBlank())return true;
        if(text.length()>2048)return false;
        try{var u=URI.create(text);return ("https".equals(u.getScheme())||"http".equals(u.getScheme()))&&u.getHost()!=null&&u.getUserInfo()==null&&u.getFragment()==null&&hosts.stream().anyMatch(h->h.equalsIgnoreCase(u.getHost()));}
        catch(IllegalArgumentException e){return false;}
    }
    public static double gain(double distance,int range){return Math.clamp(1-distance/range,0,1);}
}
