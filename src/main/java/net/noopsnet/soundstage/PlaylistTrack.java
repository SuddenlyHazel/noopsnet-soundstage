// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;
public record PlaylistTrack(String title,String url,int seconds){
    public PlaylistTrack {if(title==null||title.length()>80||url==null||url.length()>2048||seconds<1||seconds>86400)throw new IllegalArgumentException("Invalid playlist track");}
}
