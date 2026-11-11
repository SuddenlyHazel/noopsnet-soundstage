// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.noopsnet.soundstage.*;
import org.joml.Vector3f;

/** Ephemeral client effects: no world edits, bounded particles, collision-clipped beams. */
final class PerformanceEffects {
    record Cue(FixtureProfile profile,int[] values,int address){}
    static final Map<BlockPos,Cue> cues=new HashMap<>();
    static BlockPos highlight;static long highlightUntil;
    static void identify(BlockPos pos){highlight=pos;highlightUntil=System.nanoTime()+10_000_000_000L;}
    static void particles(){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;int budget=32;
        for(var e:cues.entrySet()){
            var c=e.getValue();if(c.profile!=FixtureProfile.FOG&&c.profile!=FixtureProfile.PARTICLE)continue;
            int i=c.address-1;float output=c.values[i]/255f*SoundstageClient.brightness/100f;if(output==0||mc.player.distanceToSqr(Vec3.atCenterOf(e.getKey()))>48*48)continue;
            for(int n=0;n<2&&budget>0;n++)if(mc.level.random.nextFloat()<output){
                budget--;var r=mc.level.random;var p=Vec3.atCenterOf(e.getKey());
                float spread=c.profile==FixtureProfile.FOG?.1f+c.values[i+1]/255f*.8f:.25f;
                var particle=c.profile==FixtureProfile.FOG?ParticleTypes.CAMPFIRE_COSY_SMOKE:new DustParticleOptions(new Vector3f(c.values[i+1]/255f,c.values[i+2]/255f,c.values[i+3]/255f),1.5f);
                mc.level.addParticle(particle,p.x+(r.nextFloat()-.5)*spread,p.y+.45,p.z+(r.nextFloat()-.5)*spread,(r.nextFloat()-.5)*spread*.2,.025+output*.05,(r.nextFloat()-.5)*spread*.2);
            }
        }
        if(highlight!=null&&System.nanoTime()<highlightUntil&&mc.level.getGameTime()%4==0){var p=Vec3.atCenterOf(highlight);mc.level.addParticle(ParticleTypes.END_ROD,p.x,p.y+.7,p.z,0,.03,0);}
    }
    static void render(RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(RenderType.lightning());var matrix=event.getPoseStack().last().pose();
        for(var e:cues.entrySet()){
            var c=e.getValue();if(c.profile!=FixtureProfile.LASER)continue;int i=c.address-1;float power=c.values[i]/255f*SoundstageClient.brightness/100f;if(power<=0)continue;
            var state=mc.level.getBlockState(e.getKey());if(!state.hasProperty(StageBlock.FACING))continue;
            double yaw=Math.toRadians(state.getValue(StageBlock.FACING).toYRot()+((c.values[i+4]-127.5)/127.5)*180),pitch=Math.toRadians((c.values[i+5]-127.5)/127.5*90);
            var direction=new Vec3(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
            var start=Vec3.atCenterOf(e.getKey()).add(0,.15,0).add(direction.scale(.6));var end=start.add(direction.scale(32));
            var hit=mc.level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player){
                @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(net.minecraft.world.level.block.state.BlockState block,net.minecraft.world.level.BlockGetter world,BlockPos pos){return pos.equals(e.getKey())?net.minecraft.world.phys.shapes.Shapes.empty():super.getBlockShape(block,world,pos);}
            });if(hit.getType()!=HitResult.Type.MISS)end=hit.getLocation();
            var side=direction.cross(Math.abs(direction.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();var up=direction.cross(side).normalize();
            start=start.subtract(event.getCamera().getPosition());end=end.subtract(event.getCamera().getPosition());
            for(int layer=0;layer<2;layer++)for(int n=0;n<8;n++){
                double a=n*Math.PI/4,b=(n+1)*Math.PI/4,radius=layer==0?.012:.045;
                var u=side.scale(Math.cos(a)*radius).add(up.scale(Math.sin(a)*radius));var v=side.scale(Math.cos(b)*radius).add(up.scale(Math.sin(b)*radius));
                for(var vertex:List.of(start.add(u),end.add(u),end.add(v),start.add(v)))out.addVertex(matrix,(float)vertex.x,(float)vertex.y,(float)vertex.z).setColor(c.values[i+1]/255f,c.values[i+2]/255f,c.values[i+3]/255f,power*(layer==0?.8f:.12f));
            }
        }
        buffers.endBatch(RenderType.lightning());
    }
}
