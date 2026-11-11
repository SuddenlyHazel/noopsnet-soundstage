// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.automation;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Development source set only: local UI control, never included in the mod JAR. */
@Mod(value="noopsnet_soundstage", dist=Dist.CLIENT)
public final class LocalDriver {
    private int ticks;
    private String error = "";
    public LocalDriver() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private void tick(ClientTickEvent.Post event) {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        Path dir = mc.gameDirectory.toPath().resolve("automation");
        try {
            Files.createDirectories(dir);
            Path request = dir.resolve("request.json");
            if (Files.exists(request)) {
                JsonObject r = JsonParser.parseString(Files.readString(request)).getAsJsonObject();
                Files.delete(request);
                error = "";
                switch (r.get("action").getAsString()) {
                    case "chat" -> { if (mc.hasSingleplayerServer()) mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")); }
                    case "use" -> { if (mc.hasSingleplayerServer() && mc.player!=null && mc.gameMode!=null && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,hit); }
                    case "pause" -> { if (mc.hasSingleplayerServer()) mc.pauseGame(false); }
                    case "title" -> { if (mc.level == null) mc.setScreen(new TitleScreen()); }
                    case "click" -> { if (mc.screen != null) {
                        double x=r.get("x").getAsDouble(), y=r.get("y").getAsDouble();
                        mc.screen.mouseClicked(x,y,0); mc.screen.mouseReleased(x,y,0);
                    } }
                    case "key" -> { if (mc.screen != null) { int key=r.get("key").getAsInt(); var screen=mc.screen; screen.keyPressed(key,0,0); screen.keyReleased(key,0,0); } }
                    case "text" -> { if (mc.screen != null) for(char c:r.get("text").getAsString().toCharArray()) mc.screen.charTyped(c,0); }
                    default -> throw new IllegalArgumentException("Unknown local action");
                }
            }
            JsonObject s = new JsonObject();
            s.addProperty("screen",mc.screen == null ? "none" : mc.screen.getClass().getName());
            s.addProperty("localWorld",mc.hasSingleplayerServer());
            s.addProperty("error",error);
            JsonArray widgets = new JsonArray();
            if (mc.screen != null) for(var child:mc.screen.children()) if(child instanceof AbstractWidget w) {
                JsonObject b=new JsonObject(); b.addProperty("label",w.getMessage().getString());
                b.addProperty("x",w.getX());b.addProperty("y",w.getY());b.addProperty("width",w.getWidth());b.addProperty("height",w.getHeight());b.addProperty("active",w.active);widgets.add(b);
            }
            s.add("widgets",widgets);
            Files.writeString(dir.resolve("state.json"),s.toString());
        } catch(Exception e) { error=e.toString(); }
    }
}
