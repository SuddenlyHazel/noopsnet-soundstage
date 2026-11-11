// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import java.net.URI;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.noopsnet.soundstage.*;
import org.watermedia.api.player.PlayerAPI;

@Mod(value=Soundstage.ID,dist=Dist.CLIENT)
public final class SoundstageClient {
    private static final Set<StageEntity> LOADED=new HashSet<>();
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<BlockPos,Integer> COLORS=new HashMap<>();
    private static final Map<BlockPos,net.minecraft.world.level.block.entity.BlockEntity> OVERRIDDEN=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static int tick,personalVolume=100;
    static int smoothingMillis,brightness=100;
    private static final Map<BlockPos,LightSmoother> SMOOTHED=new HashMap<>();
    private static long lastLightFrame;
    private static List<StageEntity> active=List.of();
    private static long lastAfterlight;
    private static boolean mute,lights=true;
    private static final Path PREFS=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("soundstage-listener.properties");
    private static final class Session {
        VenuePlayer player;String url;String playbackKey;boolean lighting;long started;long retry;long lastSeek;int failures;String status="Starting";
        void close(){if(player!=null){try{player.release();}catch(RuntimeException ignored){}player=null;}}
    }
    public SoundstageClient(IEventBus bus){
        loadPrefs();StageEntity.clientLoaded=LOADED::add;
        StageNetwork.openClient=p->Minecraft.getInstance().setScreen(new DeskScreen(p));
        NeoForge.EVENT_BUS.addListener(SoundstageClient::tick);
        NeoForge.EVENT_BUS.addListener(SoundstageClient::render);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e)->clear());
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e)->{
            e.getDispatcher().register(Commands.literal("soundstagesmoothing").then(Commands.argument("milliseconds",IntegerArgumentType.integer(0,2000)).executes(c->{smoothingMillis=IntegerArgumentType.getInteger(c,"milliseconds");savePrefs();return 1;})));
            e.getDispatcher().register(Commands.literal("soundstagebrightness").then(Commands.argument("percent",IntegerArgumentType.integer(0,100)).executes(c->{brightness=IntegerArgumentType.getInteger(c,"percent");savePrefs();return 1;})));
            e.getDispatcher().register(Commands.literal("soundstagevolume").then(Commands.argument("percent",IntegerArgumentType.integer(0,100)).executes(c->{personalVolume=IntegerArgumentType.getInteger(c,"percent");savePrefs();return 1;})));
            e.getDispatcher().register(Commands.literal("soundstagemute").executes(c->{mute=!mute;savePrefs();c.getSource().sendSuccess(()->Component.literal(mute?"Soundstage muted":"Soundstage unmuted"),false);return 1;}));
            e.getDispatcher().register(Commands.literal("soundstagelights").executes(c->{lights=!lights;savePrefs();restoreLights();c.getSource().sendSuccess(()->Component.literal(lights?"Soundstage lighting enabled":"Soundstage lighting disabled"),false);return 1;}));
        });
    }
    private static void loadPrefs(){try(var in=Files.newInputStream(PREFS)){var p=new Properties();p.load(in);personalVolume=Math.clamp(Integer.parseInt(p.getProperty("volume","100")),0,100);mute=Boolean.parseBoolean(p.getProperty("mute","false"));lights=Boolean.parseBoolean(p.getProperty("lights","true"));smoothingMillis=Math.clamp(Integer.parseInt(p.getProperty("smoothingMillis","0")),0,2000);brightness=Math.clamp(Integer.parseInt(p.getProperty("brightness","100")),0,100);}catch(Exception ignored){}}
    static void savePrefs(){var p=new Properties();p.setProperty("volume",""+personalVolume);p.setProperty("mute",""+mute);p.setProperty("lights",""+lights);p.setProperty("smoothingMillis",""+smoothingMillis);p.setProperty("brightness",""+brightness);try{Files.createDirectories(PREFS.getParent());try(var out=Files.newOutputStream(PREFS)){p.store(out,"Soundstage listener settings");}}catch(java.io.IOException ignored){}}
    private static void clear(){SESSIONS.values().forEach(Session::close);SESSIONS.clear();active=List.of();restoreLights();LOADED.clear();level=null;}
    private static double distance(StageEntity d,Vec3 listener){
        double best=Vec3.atCenterOf(d.getBlockPos()).distanceTo(listener);
        for(var p:d.speakers)if(level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof StageEntity s&&d.id.equals(s.deskId))best=Math.min(best,Vec3.atCenterOf(p).distanceTo(listener));
        // Keep the show active near linked fixtures even outside audible speaker coverage.
        return best;
    }
    private static double proximity(StageEntity d,Vec3 listener){double best=distance(d,listener);if(lights&&d.lighting)for(var p:d.lights.keySet())best=Math.min(best,Vec3.atCenterOf(p).distanceTo(listener));return best;}
    private static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(level!=mc.level){var pending=LOADED.stream().filter(e->e.getLevel()==mc.level).toList();clear();level=mc.level;LOADED.addAll(pending);}
        if(level==null||mc.player==null)return;
        LOADED.removeIf(e->e.isRemoved()||e.getLevel()!=level);
        if(++tick%5!=0)return;
        var listener=mc.player.getEyePosition();long now=System.nanoTime();
        var selected=LOADED.stream().filter(e->e.isSource()&&e.enabled&&!e.effectiveUrl().isBlank()&&proximity(e,listener)<e.range+4)
            .sorted(Comparator.<StageEntity>comparingDouble(e->proximity(e,listener)).thenComparingLong(e->e.getBlockPos().asLong())).limit(4).toList();
        var wanted=new HashSet<UUID>();selected.forEach(d->wanted.add(d.id));
        SESSIONS.entrySet().removeIf(e->{if(wanted.contains(e.getKey()))return false;e.getValue().close();return true;});
        for(var d:selected){
            var s=SESSIONS.computeIfAbsent(d.id,k->new Session());
            String key=d.effectiveUrl()+"|"+(d.playlistMode?d.trackStartedTick:0);
            if(!key.equals(s.playbackKey)||d.lighting!=s.lighting){s.close();s.url=d.effectiveUrl();s.playbackKey=key;s.lighting=d.lighting;s.retry=0;s.failures=0;s.lastSeek=0;}
            if(s.player==null&&now>=s.retry){
                if(!PlayerAPI.isReady()){s.status="VLC backend unavailable";continue;}
                try{s.player=new VenuePlayer(d.lighting);s.player.setVolume(0);s.player.start(URI.create(s.url),d.playlistMode?new String[]{":start-time="+(d.desiredMillis()/1000.0)}:new String[0]);s.started=now;s.status="Connecting";}
                catch(RuntimeException ex){s.close();s.retry=now+10_000_000_000L;s.status="Unable to open stream";}
            }
            if(s.player!=null){
                s.status=s.player.getStateName();
                int v=mute?0:(int)Math.round(d.volume*personalVolume/100.0*StreamPolicy.gain(distance(d,listener),d.range));var acoustic=acoustics(d,listener,mc.player.getYRot());
                s.player.spatial(v/100.0,d.spatial?acoustic[0]:0,d.walls?acoustic[1]:0);
                if(d.playlistMode&&s.player.isPlaying()&&now-s.started>500_000_000L&&now-s.lastSeek>5_000_000_000L){
                    long desired=d.desiredMillis();if(Math.abs(s.player.getTime()-desired)>1000){s.player.seekTo(desired);s.lastSeek=now;}
                }
                String state=s.status.toLowerCase(Locale.ROOT);
                if(now-s.started>15_000_000_000L&&(state.contains("error")||state.contains("ended")||state.contains("stopped")||state.contains("nothing"))){s.close();s.failures=Math.min(5,s.failures+1);s.retry=now+Math.min(30,2L<<s.failures)*1_000_000_000L;s.status="Reconnecting";}
            }
        }
        active=selected;
    }
    private static double[] acoustics(StageEntity d,Vec3 listener,float yaw){
        var points=new ArrayList<BlockPos>();points.add(d.getBlockPos());
        for(var p:d.speakers)if(level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof StageEntity e&&d.id.equals(e.deskId))points.add(p);
        double radians=Math.toRadians(yaw),rightX=-Math.cos(radians),rightZ=-Math.sin(radians),weight=0,pan=0,blocked=0;
        for(var p:points){var vector=Vec3.atCenterOf(p).subtract(listener);double gain=StreamPolicy.gain(vector.length(),d.range);if(gain<=0)continue;
            double w=gain*gain;weight+=w;double horizontal=Math.hypot(vector.x,vector.z);if(horizontal>.01)pan+=w*(vector.x*rightX+vector.z*rightZ)/horizontal;
            if(d.walls){var hit=level.clip(new net.minecraft.world.level.ClipContext(listener,Vec3.atCenterOf(p),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,Minecraft.getInstance().player));if(hit.getType()!=HitResult.Type.MISS&&!hit.getBlockPos().equals(p))blocked+=w;}
        }
        return weight==0?new double[]{0,0}:new double[]{pan/weight,blocked/weight};
    }
    private static void updateLights(List<StageEntity> selected){
        COLORS.clear();var applied=new HashSet<BlockPos>();
        long now=System.nanoTime();double dt=lastLightFrame==0?16:(now-lastLightFrame)/1_000_000.0;lastLightFrame=now;
        boolean updateAfterlight=System.nanoTime()-lastAfterlight>100_000_000L;
        if(updateAfterlight)lastAfterlight=System.nanoTime();
        if(lights)for(var d:selected){var s=SESSIONS.get(d.id);if(!d.lighting)continue;
            for(var patch:d.lights.entrySet()){
                var pos=patch.getKey();if(!level.hasChunkAt(pos)||applied.contains(pos))continue;
                int color=s!=null&&s.player!=null&&d.enabled?s.player.color(patch.getValue()):-1;
                if(color<0)color=d.fallbackMode==1?0:d.fallbackMode==2?d.fallbackColor:-1;
                if(color<0){SMOOTHED.remove(pos);continue;}
                int blended=SMOOTHED.computeIfAbsent(pos,p->new LightSmoother()).advance(color,dt,smoothingMillis);
                color=0;for(int shift:new int[]{16,8,0})color|=(((blended>>shift)&255)*brightness/100)<<shift;
                var entity=level.getBlockEntity(pos);
                if(entity instanceof StageEntity equipment){if(!d.id.equals(equipment.deskId))continue;COLORS.put(pos,color);applied.add(pos);}
                else if(entity!=null){
                    if(updateAfterlight&&applyAfterlight(entity,color))OVERRIDDEN.put(pos,entity);
                    if(OVERRIDDEN.get(pos)==entity)applied.add(pos);
                }
            }
        }
        SMOOTHED.keySet().retainAll(applied);
        OVERRIDDEN.entrySet().removeIf(e->{if(applied.contains(e.getKey())&&level.getBlockEntity(e.getKey())==e.getValue())return false;applyAfterlight(e.getValue(),null);return true;});
    }
    private static final Map<Class<?>,Optional<java.lang.reflect.Method>> HOOKS=new HashMap<>();
    private static boolean applyAfterlight(net.minecraft.world.level.block.entity.BlockEntity e,Integer color){
        var method=HOOKS.computeIfAbsent(e.getClass(),c->{try{return Optional.of(c.getMethod("setLiveColor",Integer.class));}catch(NoSuchMethodException ex){return Optional.empty();}});
        if(method.isEmpty())return false;
        try{method.get().invoke(e,color);return true;}catch(ReflectiveOperationException ex){return false;}
    }
    private static void restoreLights(){OVERRIDDEN.values().forEach(e->applyAfterlight(e,null));OVERRIDDEN.clear();COLORS.clear();SMOOTHED.clear();}
    static String status(BlockPos p){if(level==null||!(level.getBlockEntity(p) instanceof StageEntity d))return "Unavailable";var s=SESSIONS.get(d.id);return s==null?(d.enabled?"Outside playback range":"Stopped"):s.status;}
    private static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(level==null||mc.player==null)return;
        var fixtures=LOADED.stream().filter(d->d.isSource()&&proximity(d,mc.player.getEyePosition())<d.range+4).sorted(Comparator.<StageEntity>comparingDouble(d->proximity(d,mc.player.getEyePosition())).thenComparingLong(d->d.getBlockPos().asLong())).limit(4).toList();
        updateLights(fixtures);if(COLORS.isEmpty())return;
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
        COLORS.forEach((pos,rgb)->{var p=Vec3.atLowerCornerOf(pos).subtract(e.getCamera().getPosition());
            var facing=level.getBlockState(pos).getValue(StageBlock.FACING);
            AABB lens=switch(facing){
                case SOUTH->new AABB(p.x+.12,p.y+.28,p.z+.96,p.x+.88,p.y+.86,p.z+1.006);
                case EAST->new AABB(p.x+.96,p.y+.28,p.z+.12,p.x+1.006,p.y+.86,p.z+.88);
                case WEST->new AABB(p.x-.006,p.y+.28,p.z+.12,p.x+.04,p.y+.86,p.z+.88);
                default->new AABB(p.x+.12,p.y+.28,p.z-.006,p.x+.88,p.y+.86,p.z+.04);};
            DebugRenderer.renderFilledBox(e.getPoseStack(),buffers,lens,((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,1f);
        });buffers.endBatch(RenderType.debugFilledBox());
    }
}
