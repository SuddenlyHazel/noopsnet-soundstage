// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

public final class LinkTool extends Item {
    public LinkTool(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        var level = c.getLevel();
        var p = c.getPlayer();
        if (p == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var pos = c.getClickedPos();
        if (!StageNetwork.access(p, pos)) return InteractionResult.FAIL;
        var target = level.getBlockEntity(pos);
        if (target instanceof StageEntity source && source.isSource()) {
            if (!source.canEdit(p)) return message(p, "You cannot operate this sound system.");
            var tag = new CompoundTag();
            tag.putLong("desk", pos.asLong());
            tag.putUUID("id", source.id);
            tag.putString("dimension", level.dimension().location().toString());
            c.getItemInHand().set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            return message(
                    p, "Sound system selected. Use on speakers or lights; sneak-use to unlink.");
        }
        var tag =
                c.getItemInHand()
                        .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                        .copyTag();
        if (!tag.hasUUID("id")
                || !tag.getString("dimension").equals(level.dimension().location().toString()))
            return message(p, "Select a stereo or sound desk first.");
        var deskPos = BlockPos.of(tag.getLong("desk"));
        if (!level.hasChunkAt(deskPos)
                || !(level.getBlockEntity(deskPos) instanceof StageEntity desk)
                || !desk.id.equals(tag.getUUID("id"))
                || !desk.canEdit(p)) return message(p, "The selected system is unavailable.");
        if (pos.distSqr(deskPos) > 64 * 64)
            return message(p, "Equipment must be within 64 blocks of its sound system.");
        // Claim permission for the source is checked without the editing-distance requirement.
        var ev =
                net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(
                        p,
                        c.getHand(),
                        deskPos,
                        new net.minecraft.world.phys.BlockHitResult(
                                net.minecraft.world.phys.Vec3.atCenterOf(deskPos),
                                net.minecraft.core.Direction.UP,
                                deskPos,
                                false));
        if (ev.isCanceled()
                || ev.getUseBlock() == net.neoforged.neoforge.common.util.TriState.FALSE)
            return InteractionResult.FAIL;
        if (target instanceof StageEntity equipment && !equipment.isSource()) {
            if (!equipment.canEdit(p))
                return message(p, "You cannot link equipment owned by another player.");
            if (!p.isShiftKeyDown() && !FixtureProfile.at(level, pos).fits(desk.address))
                return message(
                        p, "Not enough channels at this address. Use Auto patch in the desk.");
            if (equipment.deskId != null && !equipment.deskId.equals(desk.id))
                return message(p, "Unlink this equipment from its current system first.");
            if (p.isShiftKeyDown()) {
                desk.speakers.remove(pos);
                desk.lights.remove(pos);
                desk.groups.remove(pos);
                desk.names.remove(pos);
                equipment.desk = null;
                equipment.deskId = null;
            } else {
                var kind = ((StageBlock) equipment.getBlockState().getBlock()).kind;
                if (kind == StageBlock.Kind.SPEAKER) {
                    if (desk.speakers.size() >= 32 && !desk.speakers.contains(pos))
                        return message(p, "Speaker limit reached (32).");
                    desk.speakers.add(pos.immutable());
                } else {
                    if (desk.lights.size() >= 64 && !desk.lights.containsKey(pos))
                        return message(p, "Light limit reached (64).");
                    desk.lights.put(pos.immutable(), desk.address);
                    desk.groups.put(pos.immutable(), desk.group);
                }
                equipment.desk = deskPos;
                equipment.deskId = desk.id;
            }
            equipment.changed();
        } else if (target != null
                && BuiltInRegistries.BLOCK
                        .getKey(target.getBlockState().getBlock())
                        .getNamespace()
                        .equals("noopsnet_neon")) {
            if (p.isShiftKeyDown()) {
                desk.lights.remove(pos);
                desk.groups.remove(pos);
                desk.names.remove(pos);
            } else {
                if (desk.lights.size() >= 64 && !desk.lights.containsKey(pos))
                    return message(p, "Light limit reached (64).");
                desk.lights.put(pos.immutable(), desk.address);
                desk.groups.put(pos.immutable(), desk.group);
            }
        } else return message(p, "Link speakers, stage lights or Afterlight fixtures.");
        desk.changed();
        return message(
                p,
                p.isShiftKeyDown()
                        ? "Equipment unlinked."
                        : "Equipment linked. Light address: " + desk.address);
    }

    private static InteractionResult message(
            net.minecraft.world.entity.player.Player p, String text) {
        p.displayClientMessage(Component.literal(text), true);
        return InteractionResult.CONSUME;
    }
}
