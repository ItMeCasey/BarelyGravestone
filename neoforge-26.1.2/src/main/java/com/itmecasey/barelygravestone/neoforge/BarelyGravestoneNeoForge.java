package com.itmecasey.barelygravestone.neoforge;

import com.itmecasey.barelygravestone.GraveMod;
import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.core.Settings;
import com.itmecasey.barelygravestone.menu.ObituaryMenu;
import com.itmecasey.barelygravestone.server.GraveCommands;
import com.itmecasey.barelygravestone.server.GraveEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** NeoForge entry point for Minecraft 26.x: registers the content, reads the config and forwards game events. */
@Mod(GraveMod.MODID)
public final class BarelyGravestoneNeoForge {
    public BarelyGravestoneNeoForge(IEventBus modBus, ModContainer container) {
        Settings.install(new NeoForgeSettings());
        container.registerConfig(ModConfig.Type.COMMON, NeoForgeSettings.SPEC);
        modBus.addListener(BarelyGravestoneNeoForge::register);

        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDeathEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                GraveEvents.onPlayerDeath(player);
            }
        });
        // Last, so Curios and other mods have already put their drops into the list.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDropsEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                GraveEvents.onPlayerDrops(player, event.getDrops());
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                GraveEvents.onRespawn(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                GraveEvents.onLogin(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> GraveEvents.onServerTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> GraveEvents.onServerStarting(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> GraveEvents.onServerStopped());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> GraveCommands.register(event.getDispatcher()));

        if (GraveMod.DEV) {
            // Development tools (self test, screenshot tour) exist only in builds made with -PdevTools.
            try {
                Class.forName("com.itmecasey.barelygravestone.neoforge.dev.NeoForgeDev").getMethod("install").invoke(null);
            } catch (ReflectiveOperationException e) {
                GraveMod.LOGGER.warn("barelygravestone.dev is set but this build has no development tools");
            }
        }
        GraveMod.LOGGER.info("Barely Gravestone loaded (NeoForge 26.1.2)");
    }

    private static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> helper.register(GraveMod.id("gravestone"), ModContent.GRAVE));
        event.register(Registries.ITEM, helper -> {
            helper.register(GraveMod.id("gravestone"), ModContent.GRAVE_ITEM);
            helper.register(GraveMod.id("obituary"), ModContent.OBITUARY);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> helper.register(GraveMod.id("gravestone"), ModContent.GRAVE_BE));
        event.register(Registries.MENU, helper -> {
            ModContent.obituaryMenu = IMenuTypeExtension.create((id, inventory, buffer) -> new ObituaryMenu(id, inventory));
            helper.register(GraveMod.id("obituary"), ModContent.obituaryMenu);
        });
        event.register(Registries.CREATIVE_MODE_TAB, helper -> helper.register(GraveMod.id("main"), ModContent.creativeTab()));
    }
}
