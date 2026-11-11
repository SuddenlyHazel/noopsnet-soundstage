// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("noopsnet_soundstage")
@PrefixGameTestTemplate(false)
public class StageGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void persistenceAndPermissions(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(2,1,2));var level=h.getLevel();
        level.setBlockAndUpdate(pos,Soundstage.DESK.get().defaultBlockState());
        var desk=(StageEntity)level.getBlockEntity(pos);
        var owner=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"StageOwner"));
        var visitor=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"StageVisitor"));
        desk.owner=owner.getUUID();desk.url="http://127.0.0.1:8888/soundstage/index.m3u8";desk.enabled=true;
        desk.speakers.add(pos.east());desk.lights.put(pos.west(),509);desk.groups.put(pos.west(),"Wash");
        desk.playlist.add(new PlaylistTrack("Test","http://127.0.0.1/test.mp3",42));desk.playlistMode=true;desk.loopPlaylist=false;desk.spatial=false;desk.walls=false;desk.fallbackMode=2;desk.fallbackColor=0x345678;desk.trackStartedTick=1234;
        var east=desk.getBlockState().setValue(StageBlock.FACING,net.minecraft.core.Direction.EAST);
        h.assertTrue(east.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90).getValue(StageBlock.FACING)==net.minecraft.core.Direction.SOUTH,"Fixture orientation rotates");
        h.assertTrue(desk.canEdit(owner),"Owner may edit");h.assertTrue(!desk.canEdit(visitor),"Visitor denied");
        desk.operators.add(visitor.getUUID());h.assertTrue(desk.canEdit(visitor),"Trusted operator allowed");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        try{var settings=desk.settings();settings.write(buffer);h.assertTrue(StageNetwork.Settings.read(buffer).equals(settings),"Complete settings survive the network codec");}finally{buffer.release();}
        h.assertTrue(!new StageNetwork.Settings("",101,16,true,true,1).valid(),"Invalid volume is rejected");
        var copy=new StageEntity(pos,desk.getBlockState());copy.loadWithComponents(desk.saveWithFullMetadata(level.registryAccess()),level.registryAccess());
        h.assertTrue(copy.id.equals(desk.id)&&copy.owner.equals(desk.owner)&&copy.enabled&&copy.url.equals(desk.url),"Playback and identity persist");
        h.assertTrue(copy.speakers.equals(desk.speakers)&&copy.lights.equals(desk.lights)&&copy.operators.equals(desk.operators),"Patch and operators persist");
        h.assertTrue(copy.playlist.equals(desk.playlist)&&copy.playlistMode&&!copy.loopPlaylist&&!copy.spatial&&!copy.walls&&copy.trackStartedTick==1234,"Advanced audio persists");
        h.assertTrue(copy.groups.equals(desk.groups)&&copy.fallbackMode==2&&copy.fallbackColor==0x345678,"Lighting groups and fallback persist");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void equipmentAndLink(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,1,2));
        var owner=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"LinkOwner"));owner.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
        level.setBlockAndUpdate(pos,Soundstage.STEREO.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Soundstage.SPEAKER.get().defaultBlockState());
        var source=(StageEntity)level.getBlockEntity(pos);var speaker=(StageEntity)level.getBlockEntity(pos.east());source.owner=owner.getUUID();speaker.owner=owner.getUUID();
        var tool=new net.minecraft.world.item.ItemStack(Soundstage.LINK.get());owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,tool);
        for(var target:new BlockPos[]{pos,pos.east()}){
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(target),net.minecraft.core.Direction.UP,target,false);
            Soundstage.LINK.get().useOn(new net.minecraft.world.item.context.UseOnContext(owner,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
        }
        h.assertTrue(source.speakers.contains(pos.east())&&source.id.equals(speaker.deskId),"Tool links speaker to one source");
        owner.setShiftKeyDown(true);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos.east()),net.minecraft.core.Direction.UP,pos.east(),false);
        Soundstage.LINK.get().useOn(new net.minecraft.world.item.context.UseOnContext(owner,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
        h.assertTrue(source.speakers.isEmpty()&&speaker.deskId==null,"Sneak use unlinks speaker");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void venuePatchAndExport(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,1,2));
        level.setBlockAndUpdate(pos,Soundstage.DESK.get().defaultBlockState());var d=(StageEntity)level.getBlockEntity(pos);
        var owner=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"VenueOwner"));d.owner=owner.getUUID();owner.setPos(pos.getX(),pos.getY(),pos.getZ());
        var fixtures=java.util.List.of(Soundstage.LAMP.get(),Soundstage.LASER.get(),Soundstage.FOG.get(),Soundstage.PARTICLE.get());
        for(int i=0;i<fixtures.size();i++){var p=pos.offset(i,0,1);level.setBlockAndUpdate(p,fixtures.get(i).defaultBlockState());d.lights.put(p,1);d.groups.put(p,"Main");}
        h.assertTrue(VenueTools.action(owner,d,"autopatch"),"Automatic patch succeeds");
        h.assertTrue(new java.util.ArrayList<>(d.lights.values()).equals(java.util.List.of(1,5,11,13)),"Mixed fixture footprints do not overlap");
        var before=new java.util.LinkedHashMap<>(d.lights);d.address=509;
        h.assertTrue(!VenueTools.action(owner,d,"strip_x")&&d.lights.equals(before),"Overflow is atomic");d.address=1;
        h.assertTrue(VenueTools.action(owner,d,"reverse")&&d.lights.get(pos.offset(3,0,1))==1,"Reversed pixel order persists in addresses");
        d.names.put(pos.offset(1,0,1),"Laser <left> & \"A\"");
        var b=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        var snapshot=StageNetwork.snapshot(d);try{StageNetwork.Open.CODEC.encode(b,snapshot);h.assertTrue(StageNetwork.Open.CODEC.decode(b).equals(snapshot),"Named fixture profiles roundtrip");}finally{b.release();}
        var copy=new StageEntity(pos,d.getBlockState());copy.loadWithComponents(d.saveWithFullMetadata(level.registryAccess()),level.registryAccess());h.assertTrue(copy.names.equals(d.names)&&copy.lights.equals(d.lights),"Assembly names/order persist");
        try{ShowExport.write(java.nio.file.Path.of("../../build/test-venue-export"),snapshot.patches());var files=ShowExport.files(snapshot.patches());
            var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
            for(var e:files.entrySet())if(e.getKey().endsWith(".qxf")||e.getKey().endsWith(".qxw"))factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            h.assertTrue(files.get("venue.qxw").contains("&lt;left&gt; &amp; &quot;A&quot;"),"Export escapes fixture names");
        }catch(Exception e){throw new RuntimeException(e);}
        h.succeed();
    }
}
