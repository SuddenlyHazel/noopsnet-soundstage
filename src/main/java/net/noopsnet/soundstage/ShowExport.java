// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import java.util.*;
import java.nio.file.*;
import java.io.IOException;

/** Portable QLC+ fixture definitions + workspace; no stream URLs or credentials exported. */
public final class ShowExport {
    private static String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    private static String tag(String name,String s){return "<"+name+">"+xml(s)+"</"+name+">";}
    private static String creator(){return "<Creator><Name>Q Light Controller Plus</Name><Version>4.14.0</Version><Author>Soundstage</Author></Creator>";}
    public static Map<String,String> files(List<StageNetwork.Patch> patches){
        var files=new LinkedHashMap<String,String>();
        for(var profile:FixtureProfile.values()){
            var q=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE FixtureDefinition><FixtureDefinition xmlns=\"http://www.qlcplus.org/FixtureDefinition\">").append(creator()).append("<Manufacturer>NoopsNet</Manufacturer>").append(tag("Model",profile.title)).append(tag("Type",profile==FixtureProfile.LASER?"Moving Head":profile==FixtureProfile.FOG?"Smoke":"Color Changer"));
            for(String c:profile.channels){String group=c.equals("Pan")?"Pan":c.equals("Tilt")?"Tilt":c.equals("Spread")?"Effect":"Intensity";
                q.append("<Channel Name=\"").append(c).append("\"><Group Byte=\"0\">").append(group).append("</Group>");
                if(Set.of("Red","Green","Blue").contains(c))q.append(tag("Colour",c));
                q.append("<Capability Min=\"0\" Max=\"255\">").append(c).append("</Capability></Channel>");}
            q.append("<Mode Name=\"Standard\">");for(int i=0;i<profile.channels.length;i++)q.append("<Channel Number=\"").append(i).append("\">").append(profile.channels[i]).append("</Channel>");
            q.append("</Mode></FixtureDefinition>");files.put("NoopsNet-"+profile.name()+".qxf",q.toString());
        }
        var workspace=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE Workspace><Workspace xmlns=\"http://www.qlcplus.org/Workspace\" CurrentWindow=\"FixtureManager\">").append(creator()).append("<Engine>");
        var csv=new StringBuilder("name,group,profile,universe,address,channels,x,y,z\n");int id=0;
        for(var p:patches){String name=p.name().isBlank()?p.group()+" "+(++id):p.name();if(!p.name().isBlank())id++;
            workspace.append("<Fixture><Manufacturer>NoopsNet</Manufacturer>").append(tag("Model",p.profile().title)).append("<Mode>Standard</Mode>").append(tag("ID",""+(id-1))).append(tag("Name",name)).append("<Universe>0</Universe>").append(tag("Address",""+(p.address()-1))).append(tag("Channels",""+p.profile().footprint())).append("</Fixture>");
            csv.append('"').append(name.replace("\"","\"\"")).append("\",\"").append(p.group().replace("\"","\"\"")).append("\",").append(p.profile()).append(",1,").append(p.address()).append(',').append(p.profile().footprint()).append(',').append(p.pos().getX()).append(',').append(p.pos().getY()).append(',').append(p.pos().getZ()).append('\n');
        }
        workspace.append("</Engine></Workspace>");files.put("venue.qxw",workspace.toString());files.put("patch.csv",csv.toString());
        files.put("README.txt","""
            Soundstage / QLC+ venue export
            1. Import the four NoopsNet .qxf definitions into QLC+'s user fixture library
               using the Fixture Definition Editor. Restart QLC+ after installation.
            2. Open venue.qxw. Fixtures are already named and patched in Universe 1.
            3. Set Universe 1 output to Art-Net, targeting HNode on your chosen interface.
               Match HNode's first decoded universe to this QLC+ output.
            4. Program Scenes, Chasers, RGB Matrices or Shows using QLC+.
            5. Send HNode's horizontal-top monochrome grid at 1920x1080 with audio
               through OBS to the existing Soundstage relay. Do not crop/resize the grid.
            DMX addresses in patch.csv are 1-based; QLC+ workspace values are 0-based.
            Laser channels: dimmer,R,G,B,pan,tilt. Pan 0..255 = -180..180 degrees
            relative to the placed block facing; tilt 0=up, 127/128=level, 255=down.
            Fog: output,spread. Particles: output,R,G,B. RGB blocks: dimmer,R,G,B.
            Named groups/pixels and world coordinates are in patch.csv. Re-export after
            re-patching. Export contains no media URL, credentials or recorded show.
            Speakers are acoustic emitters, not DMX fixtures; position them in Minecraft.
            Soundstage currently receives one 512-channel universe (up to 64 fixtures).
            """);return files;
    }
    public static void write(Path directory,List<StageNetwork.Patch> patches)throws IOException{
        Files.createDirectories(directory);for(var e:files(patches).entrySet())Files.writeString(directory.resolve(e.getKey()),e.getValue());
    }
}
