package com.itmecasey.barelygravestone.forge;

import com.itmecasey.barelygravestone.GraveMod;
import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.core.Settings;
import com.itmecasey.barelygravestone.menu.ObituaryMenu;
import com.itmecasey.barelygravestone.server.GraveCommands;
import com.itmecasey.barelygravestone.server.GraveEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;

/** Forge entry point for Minecraft 1.21.1: registers the content, reads the config and forwards game events. */
@Mod(GraveMod.MODID)
public final class BarelyGravestoneForge {
    public BarelyGravestoneForge(FMLJavaModLoadingContext context) {
        Settings.install(new ForgeSettings());
        context.registerConfig(ModConfig.Type.COMMON, ForgeSettings.SPEC);
        context.getModEventBus().addListener(BarelyGravestoneForge::register);

        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (LivingDeathEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                GraveEvents.onPlayerDeath(player);
            }
        });
        // Last, so Curios and other mods have already put their drops into the list.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (LivingDropsEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                GraveEvents.onPlayerDrops(player, event.getDrops());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                GraveEvents.onRespawn(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                GraveEvents.onLogin(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                GraveEvents.onServerTick(event.getServer());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent event) -> GraveEvents.onServerStarting(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> GraveEvents.onServerStopped());
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> GraveCommands.register(event.getDispatcher()));

        if (GraveMod.DEV) {
            // Development tools (self test, screenshot tour) exist only in builds made with -PdevTools.
            try {
                Class.forName("com.itmecasey.barelygravestone.forge.dev.ForgeDev").getMethod("install").invoke(null);
            } catch (ReflectiveOperationException e) {
                GraveMod.LOGGER.warn("barelygravestone.dev is set but this build has no development tools");
            }
        }
        GraveMod.LOGGER.info("Barely Gravestone loaded (Forge 1.21.1)");
    }

    private static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> helper.register(GraveMod.id("gravestone"), ModContent.GRAVE));
        event.register(Registries.ITEM, helper -> {
            helper.register(GraveMod.id("gravestone"), ModContent.GRAVE_ITEM);
            helper.register(GraveMod.id("obituary"), ModContent.OBITUARY);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> helper.register(GraveMod.id("gravestone"), ModContent.GRAVE_BE));
        event.register(Registries.MENU, helper -> {
            ModContent.obituaryMenu = IForgeMenuType.create((id, inventory, buffer) -> new ObituaryMenu(id, inventory));
            helper.register(GraveMod.id("obituary"), ModContent.obituaryMenu);
        });
        event.register(Registries.CREATIVE_MODE_TAB, helper -> helper.register(GraveMod.id("main"), ModContent.creativeTab()));
    }
}
