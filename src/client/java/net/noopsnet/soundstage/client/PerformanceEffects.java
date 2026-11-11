// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.noopsnet.soundstage.*;
import org.joml.Vector3f;

/** Ephemeral client effects: no world edits, bounded particles, collision-clipped beams. */
final class PerformanceEffects {
    record Cue(FixtureProfile profile,int[] values,int address){}
    static final Map<BlockPos,Cue> cues=new HashMap<>();
    private static final Map<BlockPos,Integer> emitted=new HashMap<>();
    static String status(BlockPos pos){
        var c=cues.get(pos);if(c==null)return "No live cue";
        int i=c.address-1;return "DMX "+c.address+": "+Arrays.toString(Arrays.copyOfRange(c.values,i,i+c.profile.channels.length))+"; particles "+emitted.getOrDefault(pos,0);
    }
    static BlockPos highlight;static long highlightUntil;
    static void identify(BlockPos pos){highlight=pos;highlightUntil=System.nanoTime()+10_000_000_000L;}
    static void particles(){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;int budget=32;
        emitted.keySet().retainAll(cues.keySet());
        var setting=mc.options.particles().get();
        if(setting==ParticleStatus.MINIMAL)return;
        for(var e:cues.entrySet()){
            var c=e.getValue();if(c.profile!=FixtureProfile.FOG&&c.profile!=FixtureProfile.PARTICLE)continue;
            int i=c.address-1;float output=c.values[i]/255f*SoundstageClient.brightness/100f;if(output==0||mc.player.distanceToSqr(Vec3.atCenterOf(e.getKey()))>48*48)continue;
            var state=mc.level.getBlockState(e.getKey());if(!state.hasProperty(StageBlock.FACING))continue;
            var facing=state.getValue(StageBlock.FACING);
            if(setting==ParticleStatus.DECREASED)output*=.5f;
            for(int n=0;n<(c.profile==FixtureProfile.FOG?4:2)&&budget>0;n++)if(mc.level.random.nextFloat()<output){
                budget--;var r=mc.level.random;var p=Vec3.atCenterOf(e.getKey());
                float spread=c.profile==FixtureProfile.FOG?.1f+c.values[i+1]/255f*.8f:.25f;
                var particle=c.profile==FixtureProfile.FOG?ParticleTypes.CLOUD:new DustParticleOptions(new Vector3f(c.values[i+1]/255f,c.values[i+2]/255f,c.values[i+3]/255f),1.5f);
                // Emit through the front nozzle, outside the full-block collision box
                // including the particle's bounds. Ceiling-mounted machines still work.
                // Apply our explicit 48-block/budget/settings limits instead of silently
                // losing particles to LevelRenderer's additional 32-block cutoff.
                double sideways=(r.nextFloat()-.5)*spread;
                var spawned=mc.particleEngine.createParticle(particle,p.x+facing.getStepX()*.85+facing.getStepZ()*sideways,p.y+.1,p.z+facing.getStepZ()*.85-facing.getStepX()*sideways,facing.getStepX()*.08+(r.nextFloat()-.5)*spread*.2,.025+output*.025,facing.getStepZ()*.08+(r.nextFloat()-.5)*spread*.2);
                if(spawned!=null){
                    emitted.merge(e.getKey(),1,Integer::sum);
                    if(c.profile==FixtureProfile.FOG){spawned.setColor(.92f,.94f,.96f);spawned.scale(2f);spawned.setLifetime(80);}
                }
            }
        }
        if(highlight!=null&&System.nanoTime()<highlightUntil&&mc.level.getGameTime()%4==0){var p=Vec3.atCenterOf(highlight);mc.level.addParticle(ParticleTypes.END_ROD,p.x,p.y+.7,p.z,0,.03,0);}
    }
    static void render(RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(EmissiveGeometry.type());var pose=event.getPoseStack().last();
        for(var e:cues.entrySet()){
            var c=e.getValue();if(c.profile!=FixtureProfile.LASER)continue;int i=c.address-1;float power=c.values[i]/255f*SoundstageClient.brightness/100f;if(power<=0)continue;
            if((c.values[i+1]|c.values[i+2]|c.values[i+3])==0)continue;
            var state=mc.level.getBlockState(e.getKey());if(!state.hasProperty(StageBlock.FACING))continue;
            double yaw=Math.toRadians(state.getValue(StageBlock.FACING).toYRot()+((c.values[i+4]-127.5)/127.5)*180),pitch=Math.toRadians((c.values[i+5]-127.5)/127.5*90);
            var direction=new Vec3(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
            var start=Vec3.atCenterOf(e.getKey()).add(0,.15,0).add(direction.scale(.6));var end=start.add(direction.scale(32));
            var hit=mc.level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player){
                @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(net.minecraft.world.level.block.state.BlockState block,net.minecraft.world.level.BlockGetter world,BlockPos pos){return pos.equals(e.getKey())?net.minecraft.world.phys.shapes.Shapes.empty():super.getBlockShape(block,world,pos);}
            });if(hit.getType()!=HitResult.Type.MISS)end=hit.getLocation();
            start=start.subtract(event.getCamera().getPosition());end=end.subtract(event.getCamera().getPosition());
            EmissiveGeometry.beam(out,pose,start,end,direction,c.values[i+1]/255f*power,c.values[i+2]/255f*power,c.values[i+3]/255f*power);
        }
        buffers.endBatch(EmissiveGeometry.type());
    }
}
