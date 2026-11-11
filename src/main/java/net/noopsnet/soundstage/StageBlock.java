// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class StageBlock extends net.minecraft.world.level.block.HorizontalDirectionalBlock implements EntityBlock {
    public enum Kind {STEREO,DESK,SPEAKER,LIGHT,LASER,FOG,PARTICLE}
    public final Kind kind;
    public StageBlock(Kind kind,Properties properties){super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH));}
    @Override protected MapCodec<? extends net.minecraft.world.level.block.HorizontalDirectionalBlock> codec(){return simpleCodec(p->new StageBlock(kind,p));}
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState s,net.minecraft.world.level.block.Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,net.minecraft.world.level.block.Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new StageEntity(pos,state);}
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level,BlockState state,net.minecraft.world.level.block.entity.BlockEntityType<T> type){
        if(level.isClientSide)return null;
        return (l,p,s,e)->{if(l.getGameTime()%20==0&&e instanceof StageEntity d&&d.isSource()){
            if(d.enabled&&(!StreamPolicy.allowed(d.effectiveUrl(),Soundstage.HOSTS.get())||d.playlist.stream().anyMatch(t->!StreamPolicy.allowed(t.url(),Soundstage.HOSTS.get())))){d.enabled=false;d.changed();}
            d.advance();
        }};
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof StageEntity entity){entity.owner=player.getUUID();entity.changed();}
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player.getMainHandItem().is(Soundstage.LINK.get()))return InteractionResult.PASS;
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof StageEntity entity && player instanceof ServerPlayer server){
            if(entity.isSource()){
                if(entity.canEdit(player))PacketDistributor.sendToPlayer(server,StageNetwork.snapshot(entity));
                else player.displayClientMessage(Component.literal("Only the owner, trusted operators or server operators can change this system."),true);
            }else player.displayClientMessage(Component.literal("Use the linking tool: select a stereo or desk, then this equipment."),true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
