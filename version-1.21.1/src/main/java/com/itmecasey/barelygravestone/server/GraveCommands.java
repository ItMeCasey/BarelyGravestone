package com.itmecasey.barelygravestone.server;

import com.itmecasey.barelygravestone.block.GraveBlockEntity;
import com.itmecasey.barelygravestone.core.GraveRegistry;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import com.itmecasey.barelygravestone.item.ObituaryData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /gravestone} for operators: {@code list [player]}, {@code tp <id>}, {@code remove <id>}, {@code restore <id> [player]}
 * and {@code obituary <id> [player]}. An id is the first characters of the grave's id shown by {@code list}.
 */
public final class GraveCommands {
    private static final SuggestionProvider<CommandSourceStack> IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(GraveStores.registry().all().stream().map(e -> shortId(e)), builder);

    private GraveCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gravestone")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("tp")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(IDS).executes(GraveCommands::teleport)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(IDS).executes(GraveCommands::remove)))
                .then(Commands.literal("restore")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
                                .executes(ctx -> restore(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> restore(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("obituary")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
                                .executes(ctx -> obituary(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> obituary(ctx, EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static String shortId(GraveRegistry.Entry entry) {
        return entry.id().toString().substring(0, 8);
    }

    private static GraveRegistry.Entry resolve(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String wanted = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
        List<GraveRegistry.Entry> matches = new ArrayList<>();
        for (GraveRegistry.Entry entry : GraveStores.registry().all()) {
            if (entry.id().toString().startsWith(wanted)) {
                matches.add(entry);
            }
        }
        if (matches.size() != 1) {
            ctx.getSource().sendFailure(Component.translatable(matches.isEmpty()
                    ? "command.barelygravestone.no_such_grave" : "command.barelygravestone.ambiguous", wanted));
            return null;
        }
        return matches.get(0);
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer only) {
        List<GraveRegistry.Entry> entries = only == null ? GraveStores.registry().all() : GraveStores.registry().ownedBy(only.getUUID());
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.barelygravestone.none"), false);
            return 0;
        }
        long now = System.currentTimeMillis();
        for (GraveRegistry.Entry e : entries) {
            long minutes = Math.max(0L, (now - e.createdMillis()) / 60000L);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.barelygravestone.entry", shortId(e), e.ownerName(),
                    ObituaryRecord.prettyDimension(e.dimension()), e.x(), e.y(), e.z(), minutes), false);
        }
        return entries.size();
    }

    private static int teleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GraveRegistry.Entry entry = resolve(ctx);
        if (entry == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = GraveService.levelOf(ctx.getSource().getServer(), entry.dimension());
        if (level == null) {
            ctx.getSource().sendFailure(Component.translatable("command.barelygravestone.no_dimension", entry.dimension()));
            return 0;
        }
        player.teleportTo(level, entry.x() + 0.5, entry.y() + 1.0, entry.z() + 2.0, 180.0F, 0.0F);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.barelygravestone.teleported", shortId(entry)), false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GraveRegistry.Entry entry = resolve(ctx);
        if (entry == null) {
            return 0;
        }
        GraveService.remove(ctx.getSource().getServer(), entry, true);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.barelygravestone.removed", shortId(entry)), true);
        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> ctx, ServerPlayer target) throws CommandSyntaxException {
        GraveRegistry.Entry entry = resolve(ctx);
        if (entry == null) {
            return 0;
        }
        int moved = GraveService.restore(ctx.getSource().getServer(), entry, target);
        if (moved < 0) {
            ctx.getSource().sendFailure(Component.translatable("command.barelygravestone.not_loaded", shortId(entry)));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.barelygravestone.restored", shortId(entry),
                target.getDisplayName()), true);
        return 1;
    }

    private static int obituary(CommandContext<CommandSourceStack> ctx, ServerPlayer target) throws CommandSyntaxException {
        GraveRegistry.Entry entry = resolve(ctx);
        if (entry == null) {
            return 0;
        }
        GraveBlockEntity grave = GraveService.find(ctx.getSource().getServer(), entry);
        ObituaryRecord record = grave == null ? null : grave.death();
        if (record == null) {
            ctx.getSource().sendFailure(Component.translatable("command.barelygravestone.not_loaded", shortId(entry)));
            return 0;
        }
        ItemStack stack = ObituaryData.create(record);
        if (!target.getInventory().add(stack)) {
            net.minecraft.world.level.block.Block.popResource(target.level(), target.blockPosition(), stack);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.barelygravestone.obituary", shortId(entry),
                target.getDisplayName()), true);
        return 1;
    }
}
