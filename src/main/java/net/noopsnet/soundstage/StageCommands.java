// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
public final class StageCommands {
    public static void register(RegisterCommandsEvent e){
        var root=Commands.literal("soundstage");
        for(String action:new String[]{"trust","untrust"})root.then(Commands.literal(action).then(Commands.argument("player",EntityArgument.player()).executes(c->{
            var p=c.getSource().getPlayerOrException();var hit=p.pick(6,0,false);
            if(!(hit instanceof BlockHitResult b)||!StageNetwork.access(p,b.getBlockPos())||!(p.level().getBlockEntity(b.getBlockPos()) instanceof StageEntity desk)||!desk.isSource()||!desk.ownedBy(p))return 0;
            var other=EntityArgument.getPlayer(c,"player").getUUID();
            if(action.equals("trust")){if(desk.operators.size()>=32)return 0;desk.operators.add(other);}else desk.operators.remove(other);
            desk.changed();c.getSource().sendSuccess(()->Component.literal("Soundstage operator list updated."),false);return 1;
        })));
        e.getDispatcher().register(root);
    }
}
