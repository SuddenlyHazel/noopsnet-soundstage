// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Server-side atomic venue operations. Never load chunks or edit denied claims. */
public final class VenueTools {
    public static boolean action(Player p,StageEntity d,String action){
        if(!Set.of("autopatch","strip_x","strip_y","strip_z","reverse","uniform","collect").contains(action))return false;
        var targets=new ArrayList<>(d.lights.keySet().stream().filter(pos->action.equals("autopatch")||d.groups.getOrDefault(pos,"Main").equals(d.group)).toList());
        if(targets.isEmpty()){say(p,"Link a fixture into the selected group first.");return false;}
        if(action.equals("collect")){
            var found=new LinkedHashSet<>(targets);var queue=new ArrayDeque<>(targets);
            while(!queue.isEmpty()){var current=queue.remove();for(var dir:Direction.values()){
                var next=current.relative(dir);
                if(found.contains(next)||!p.level().hasChunkAt(next)||next.distSqr(d.getBlockPos())>4096)continue;
                if(!BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(next).getBlock()).getNamespace().equals("noopsnet_neon"))continue;
                if(d.lights.containsKey(next)&&!d.groups.getOrDefault(next,"Main").equals(d.group))continue;
                if(!StageNetwork.claim(p,next)){say(p,"Assembly crosses a protected claim. Nothing changed.");return false;}
                found.add(next);queue.add(next);
                if(found.size()+d.lights.size()-targets.size()>64){say(p,"Assembly exceeds 64 fixtures. Nothing changed.");return false;}
            }
            }
            targets=new ArrayList<>(found);
        }
        if(targets.stream().anyMatch(pos->!p.level().hasChunkAt(pos)||!StageNetwork.claim(p,pos))){say(p,"Load the fixtures and check claim access first.");return false;}
        Comparator<BlockPos> order=switch(action){
            case "strip_y"->Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getY);
            case "strip_z"->Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);
            default->Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX);};
        if(action.equals("reverse")){targets.sort(Comparator.comparingInt(pos->d.lights.get(pos)));Collections.reverse(targets);}else targets.sort(order);
        int address=action.equals("autopatch")?1:d.address;var patch=new LinkedHashMap<BlockPos,Integer>();
        for(var pos:targets){var profile=FixtureProfile.at(p.level(),pos);if(!profile.fits(address)){say(p,"Patch exceeds channel 512. Nothing changed.");return false;}patch.put(pos,address);if(!action.equals("uniform"))address+=profile.footprint();}
        // Refuse accidental overlap with another group's channel allocation.
        if(!action.equals("autopatch"))for(var e:d.lights.entrySet())if(!patch.containsKey(e.getKey()))for(var n:patch.entrySet()){
            int a=e.getValue(),b=n.getValue();if(a<b+FixtureProfile.at(p.level(),n.getKey()).footprint()&&b<a+FixtureProfile.at(p.level(),e.getKey()).footprint()){say(p,"Address overlaps another group. Choose a free starting address or Auto patch all.");return false;}
        }
        for(var e:patch.entrySet()){d.lights.put(e.getKey(),e.getValue());d.groups.putIfAbsent(e.getKey(),d.group);}
        say(p,"Patched "+patch.size()+" fixtures. Export the updated show patch after changes.");return true;
    }
    private static void say(Player p,String text){p.displayClientMessage(Component.literal(text),false);}
}
