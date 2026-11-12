// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(Soundstage.ID)
public final class Soundstage {
    public static final String ID = "noopsnet_soundstage";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final Supplier<StageBlock> STEREO = block("stereo", StageBlock.Kind.STEREO);
    public static final Supplier<StageBlock> DESK = block("sound_desk", StageBlock.Kind.DESK);
    public static final Supplier<StageBlock> SPEAKER = block("speaker", StageBlock.Kind.SPEAKER);
    public static final Supplier<StageBlock> LAMP = block("stage_light", StageBlock.Kind.LIGHT);
    public static final Supplier<StageBlock> LASER = block("laser", StageBlock.Kind.LASER);
    public static final Supplier<StageBlock> FOG = block("fog_machine", StageBlock.Kind.FOG);
    public static final Supplier<StageBlock> PARTICLE =
            block("particle_machine", StageBlock.Kind.PARTICLE);
    public static final Supplier<LinkTool> LINK =
            ITEMS.register("linking_tool", () -> new LinkTool(new Item.Properties().stacksTo(1)));
    public static final Supplier<BlockEntityType<StageEntity>> ENTITY =
            ENTITIES.register(
                    "equipment",
                    () ->
                            BlockEntityType.Builder.of(
                                            StageEntity::new,
                                            STEREO.get(),
                                            DESK.get(),
                                            SPEAKER.get(),
                                            LAMP.get(),
                                            LASER.get(),
                                            FOG.get(),
                                            PARTICLE.get())
                                    .build(null));
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> HOSTS;
    public static final ModConfigSpec.ConfigValue<String> DEMO_URL;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> RADIO_STATIONS;
    private static final ModConfigSpec CONFIG;

    static {
        var b = new ModConfigSpec.Builder();
        HOSTS =
                b.comment(
                                "Trusted media hostnames, exact matches. Clients fetch these URLs."
                                    + " Add your relay's public hostname. Playlists/redirects must"
                                    + " also be trusted. Loopback is for local testing only.")
                        .defineListAllowEmpty(
                                "allowedStreamHosts",
                                java.util.List.of(),
                                () -> "relay.example.com",
                                o -> o instanceof String s && !s.isBlank());
        DEMO_URL =
                b.comment("Optional public demo stream. Never put a publishing password here.")
                        .define("demoStreamUrl", "");
        RADIO_STATIONS =
                b.comment(
                                "Named live radio stations, synced to players. Each entry is Name |"
                                        + " URL.",
                                "URLs must also match allowedStreamHosts. No publish credentials.",
                                "The Stereo and Sound Desk Radio tab offers up to 32 distinct"
                                        + " streams.")
                        .defineListAllowEmpty(
                                "radioStations",
                                java.util.List.of(),
                                () -> "Station name | https://relay.example.com/radio/index.m3u8",
                                RadioStation::validEntry);
        CONFIG = b.build();
        TABS.register(
                "soundstage",
                () ->
                        CreativeModeTab.builder()
                                .title(Component.literal("Soundstage"))
                                .icon(() -> new ItemStack(DESK.get()))
                                .displayItems(
                                        (p, o) -> {
                                            o.accept(STEREO.get());
                                            o.accept(DESK.get());
                                            o.accept(SPEAKER.get());
                                            o.accept(LAMP.get());
                                            o.accept(LASER.get());
                                            o.accept(FOG.get());
                                            o.accept(PARTICLE.get());
                                            o.accept(LINK.get());
                                        })
                                .build());
    }

    private static Supplier<StageBlock> block(String id, StageBlock.Kind kind) {
        var block =
                BLOCKS.register(
                        id,
                        () ->
                                new StageBlock(
                                        kind,
                                        BlockBehaviour.Properties.of()
                                                .strength(2)
                                                .sound(SoundType.METAL)
                                                .noOcclusion()));
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public Soundstage(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, CONFIG);
        bus.addListener(StageNetwork::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(StageCommands::register);
    }
}
