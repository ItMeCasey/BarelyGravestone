package com.itmecasey.barelygravestone.dev;

import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.block.GraveBlock;
import com.itmecasey.barelygravestone.block.GraveBlockEntity;
import com.itmecasey.barelygravestone.core.CollectMode;
import com.itmecasey.barelygravestone.core.GraveRegistry;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import com.itmecasey.barelygravestone.core.Settings;
import com.itmecasey.barelygravestone.core.XpMath;
import com.itmecasey.barelygravestone.item.ObituaryData;
import com.itmecasey.barelygravestone.menu.ObituaryMenu;
import com.itmecasey.barelygravestone.server.GraveEvents;
import com.itmecasey.barelygravestone.server.GraveService;
import com.itmecasey.barelygravestone.server.GraveStores;
import com.itmecasey.barelygravestone.server.TeamMods;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Headless check of the grave rules on a real server. Runs once when the server has started if the environment variable
 * {@code BARELYGRAVESTONE_SELFTEST} is set and prints {@code SELFTEST PASS/FAIL} lines ending with
 * {@code SELFTEST DONE failures=N}. Players are bare {@link ServerPlayer} objects without a connection, and the death is
 * driven through the same entry point the loader's drop event uses.
 */
public final class SelfTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("BarelyGravestone");

    private static int failures;
    private static MinecraftServer server;

    private SelfTest() {
    }

    public static boolean requested() {
        return System.getenv("BARELYGRAVESTONE_SELFTEST") != null || System.getProperty("barelygravestone.selftest") != null;
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            LOGGER.info("SELFTEST PASS {}", name);
        } else {
            failures++;
            LOGGER.error("SELFTEST FAIL {}", name);
        }
    }

    /** A source with chosen values. */
    private record Config(CollectMode mode, int xpPercent, boolean keepXp, int expiry, boolean team) implements Settings.Source {
        @Override
        public CollectMode collectBy() {
            return mode;
        }

        @Override
        public int xpCostPercent() {
            return xpPercent;
        }

        @Override
        public boolean keepLostXp() {
            return keepXp;
        }

        @Override
        public int graveExpiryMinutes() {
            return expiry;
        }

        @Override
        public boolean showCoordinates() {
            return true;
        }

        @Override
        public boolean teamMembersCanOpen() {
            return team;
        }

        @Override
        public boolean deathMarker() {
            return true;
        }

        @Override
        public boolean obituaryParticles() {
            return true;
        }
    }

    private static ServerPlayer player(ServerLevel level, String name) {
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        player.moveTo(0.5, 64, 0.5, 0.0F, 0.0F);
        return player;
    }

    /** What the game's drop event would hand over: every stack the player carries as item entities that are not in the world. */
    private static List<ItemEntity> dropsOf(ServerPlayer player) {
        List<ItemEntity> drops = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                drops.add(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack.copy()));
            }
        }
        player.getInventory().clearContent();
        return drops;
    }

    private static void kit(ServerPlayer player) {
        player.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().add(new ItemStack(Items.DIAMOND, 7));
        player.getInventory().add(new ItemStack(Items.IRON_SWORD));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static GraveBlockEntity graveAt(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof GraveBlockEntity grave ? grave : null;
    }

    /** The grave a player's death made, found through the index (so the check does not depend on the exact cell). */
    private static GraveBlockEntity graveOf(ServerLevel level, ServerPlayer owner) {
        var entries = GraveStores.registry().ownedBy(owner.getUUID());
        if (entries.isEmpty()) {
            return null;
        }
        GraveRegistry.Entry e = entries.get(entries.size() - 1);
        return graveAt(level, new BlockPos(e.x(), e.y(), e.z()));
    }

    /**
     * A flat stone platform with clear air above it, built where the check stands, so the result never depends on the world's
     * terrain (a game test server makes a normal random world, with oceans, hills and trees). Returns the y of the first air cell.
     */
    private static int platform(ServerLevel level, int x, int z) {
        int base = 100;
        level.getChunk(new BlockPos(x, base, z));
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                for (int y = base - 4; y < base; y++) {
                    level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.STONE.defaultBlockState(), 2);
                }
                for (int y = base; y <= base + 10; y++) {
                    level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return base;
    }

    public static int run(MinecraftServer minecraftServer) {
        server = minecraftServer;
        failures = 0;
        GraveStores.open(server);
        // A game test server starts players in creative mode; the checks need survival players.
        server.setDefaultGameType(net.minecraft.world.level.GameType.SURVIVAL);
        Settings.install(new Config(CollectMode.OWNER_ONLY, 0, false, 0, false));
        try {
            execute();
        } catch (Throwable t) {
            failures++;
            LOGGER.error("SELFTEST FAIL exception", t);
        } finally {
            Settings.install(null);
        }
        LOGGER.info("SELFTEST DONE failures={}", failures);
        return failures;
    }

    private static void execute() {
        ServerLevel level = server.overworld();
        int gx = 200;
        int gz = 200;
        level.getChunk(new BlockPos(gx, 0, gz));
        int ground = platform(level, gx, gz);

        // ---- a death on the ground ----
        ServerPlayer steve = player(level, "Steve");
        steve.moveTo(gx + 0.5, ground, gz + 0.5, 90.0F, 0.0F);
        kit(steve);
        int carried = steve.getInventory().getContainerSize();
        int stackCount = 6;
        GraveEvents.onPlayerDeath(steve);
        BlockPos pos = new BlockPos(gx, ground, gz);
        check("grave block placed at the death position", level.getBlockState(pos).is(ModContent.GRAVE));
        check("the inventory is emptied so nothing falls", steve.getInventory().isEmpty());
        List<ItemEntity> late = new ArrayList<>(List.of(new ItemEntity(level, 0, 0, 0, new ItemStack(Items.EMERALD, 2))));
        GraveEvents.onPlayerDrops(steve, late);
        check("drops other mods add right after the death (Curios) join the grave", late.isEmpty()
                && graveAt(level, new BlockPos(gx, ground, gz)).items().size() == 7);
        graveAt(level, new BlockPos(gx, ground, gz)).items().remove(6);
        GraveBlockEntity grave = graveAt(level, pos);
        check("block entity holds every stack (inventory, armor, off-hand)", grave != null && grave.items().size() == stackCount && stackCount == 6);
        check("grave remembers the owner and the profile", grave != null && grave.owner().equals(steve.getUUID())
                && grave.ownerName().equals("Steve") && grave.profile() != null);
        check("no cobblestone under a grave on solid ground", !level.getBlockState(pos.below()).is(Blocks.COBBLESTONE));
        check("grave is in the index", GraveStores.registry().size() == 1 && GraveStores.registry().findAt(
                level.dimension().location().toString(), gx, ground, gz) != null);
        check("an Obituary is waiting for the player", GraveStores.pending().has(steve.getUUID()));
        GraveService.deliverObituaries(steve);
        ItemStack obit = ItemStack.EMPTY;
        for (int i = 0; i < steve.getInventory().getContainerSize(); i++) {
            if (steve.getInventory().getItem(i).is(ModContent.OBITUARY)) {
                obit = steve.getInventory().getItem(i);
            }
        }
        ObituaryRecord record = ObituaryData.read(obit);
        check("the Obituary is handed over with the death record", record != null && record.ownerName().equals("Steve")
                && record.x() == gx && record.y() == ground && record.z() == gz && record.dimension().equals("minecraft:overworld")
                && record.graveId().equals(grave.graveId()) && !record.message().isEmpty() && record.realMillis() > 0
                && record.gameDay() >= 1);
        check("the pending list is empty after delivery", !GraveStores.pending().has(steve.getUUID()));

        // ---- the Obituary menu reflects the grave ----
        ObituaryMenu menu = new ObituaryMenu(7, steve.getInventory(), level, pos, grave.graveId());
        check("menu lists the stacks in the grave", menu.getSlot(0).getItem().getCount() > 0 && menu.getSlot(5).hasItem()
                && !menu.getSlot(6).hasItem() && !menu.graveGone());

        // ---- who may collect ----
        ServerPlayer alex = player(level, "Alex");
        ServerPlayer buddy = player(level, "Buddy");
        // Nobody is an operator here unless a check says so (a game test server gives every player all permissions).
        GraveService.privilegeProbe = p -> false;
        check("owner may collect", GraveService.canCollect(steve, grave));
        check("a stranger may not collect (OWNER_ONLY)", !GraveService.canCollect(alex, grave));
        Settings.install(new Config(CollectMode.OWNER_ONLY, 0, false, 0, true));
        check("a stranger is still refused when only team members are allowed", !GraveService.canCollect(alex, grave));
        PlayerTeam team = server.getScoreboard().addPlayerTeam("gravetest");
        server.getScoreboard().addPlayerToTeam("Steve", team);
        server.getScoreboard().addPlayerToTeam("Buddy", team);
        check("a teammate may collect with teamMembersCanOpen", GraveService.canCollect(buddy, grave));
        check("the teammate does not open the grave of a player on no team", !GraveService.canCollect(alex, grave));
        Settings.install(new Config(CollectMode.OWNER_ONLY, 0, false, 0, false));
        check("a teammate may not collect when the option is off", !GraveService.canCollect(buddy, grave));
        Settings.install(new Config(CollectMode.ANYONE, 0, false, 0, false));
        check("anyone may collect with collectBy=ANYONE", GraveService.canCollect(alex, grave));
        Settings.install(new Config(CollectMode.OWNER_ONLY, 0, false, 0, false));
        server.getScoreboard().removePlayerTeam(team);
        // Team mods are reached by reflection: with none installed (or no party formed) the checks must simply say no.
        TeamMods.install();
        LOGGER.info("SELFTEST detail team mods: {}", TeamMods.status());
        check("team mod lookups fail closed for players in no party (" + TeamMods.status() + ")",
                !com.itmecasey.barelygravestone.server.TeamLinks.sameTeam(alex, steve.getUUID(), "Steve"));
        if (System.getenv("BARELYGRAVESTONE_TEAMMOD") != null) {
            check("the expected team mod was found", TeamMods.status().contains(System.getenv("BARELYGRAVESTONE_TEAMMOD")));
        }

        GraveService.privilegeProbe = p -> p == alex;
        check("an operator or creative player may always collect", GraveService.canCollect(alex, grave));
        GraveService.privilegeProbe = p -> false;

        // A refused click changes nothing.
        GraveService.collect(alex, level, pos, grave);
        check("a refused collect leaves the grave untouched", level.getBlockState(pos).is(ModContent.GRAVE) && grave.items().size() == 6
                && count(alex, Items.DIAMOND) == 0);

        // ---- partial collect with a full inventory, then the rest ----
        for (int i = 0; i < 36; i++) {
            steve.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        steve.getInventory().setItem(35, obit);
        steve.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        steve.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        GraveService.collect(steve, level, pos, grave);
        check("a full inventory leaves items in the grave", level.getBlockState(pos).is(ModContent.GRAVE) && !grave.items().isEmpty());
        menu.broadcastChanges();
        check("the Obituary list updates as the grave changes", menu.getSlot(0).hasItem() && !menu.graveGone());
        for (int i = 0; i < 35; i++) {
            steve.getInventory().setItem(i, ItemStack.EMPTY);
        }
        GraveService.collect(steve, level, pos, grave);
        check("collecting with room moves everything and removes the grave", level.getBlockState(pos).isAir() && grave.items().isEmpty()
                && count(steve, Items.DIAMOND) == 7 && count(steve, Items.COBBLESTONE) == 64);
        check("the grave left the index", GraveStores.registry().size() == 0);
        menu.broadcastChanges();
        check("the menu reports the grave gone and empties its list", menu.graveGone() && !menu.getSlot(0).hasItem());
        ItemStack held = ItemStack.EMPTY;
        for (int i = 0; i < steve.getInventory().getContainerSize(); i++) {
            if (steve.getInventory().getItem(i).is(ModContent.OBITUARY)) {
                held = steve.getInventory().getItem(i);
            }
        }
        check("the Obituary is marked gone (the map marker disappears)", ObituaryData.gone(held));

        // ---- a death in water: cobblestone at the surface, grave on top ----
        int wx = 230;
        int wz = 230;
        level.getChunk(new BlockPos(wx, 0, wz));
        int g2 = platform(level, wx, wz);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int y = g2 - 4; y <= g2 + 1; y++) {
                    level.setBlock(new BlockPos(wx + dx, y, wz + dz), Blocks.WATER.defaultBlockState(), 2);
                }
                for (int y = g2 + 2; y <= g2 + 6; y++) {
                    level.setBlock(new BlockPos(wx + dx, y, wz + dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        ServerPlayer swimmer = player(level, "Swimmer");
        swimmer.moveTo(wx + 0.5, g2 - 2, wz + 0.5, 0.0F, 0.0F);
        swimmer.getInventory().add(new ItemStack(Items.GOLD_INGOT, 5));
        GraveEvents.onPlayerDeath(swimmer);
        BlockPos top = new BlockPos(wx, g2 + 2, wz);
        check("death in water: cobblestone at the surface", level.getBlockState(new BlockPos(wx, g2 + 1, wz)).is(Blocks.COBBLESTONE));
        check("death in water: grave sits on top of it", level.getBlockState(top).is(ModContent.GRAVE));

        // ---- a death inside solid rock: nearest free cell ----
        int rx = 260;
        int rz = 260;
        level.getChunk(new BlockPos(rx, 0, rz));
        int g3 = platform(level, rx, rz);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = g3 - 3; y <= g3 + 2; y++) {
                    level.setBlock(new BlockPos(rx + dx, y, rz + dz), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        ServerPlayer miner = player(level, "Miner");
        miner.moveTo(rx + 0.5, g3, rz + 0.5, 0.0F, 0.0F);
        miner.getInventory().add(new ItemStack(Items.COAL, 3));
        GraveEvents.onPlayerDeath(miner);
        GraveRegistry.Entry minerGrave = GraveStores.registry().ownedBy(miner.getUUID()).isEmpty() ? null
                : GraveStores.registry().ownedBy(miner.getUUID()).get(0);
        check("death inside rock: grave goes to the nearest free cell", minerGrave != null
                && level.getBlockState(new BlockPos(minerGrave.x(), minerGrave.y(), minerGrave.z())).is(ModContent.GRAVE)
                && Math.abs(minerGrave.x() - rx) <= 3 && Math.abs(minerGrave.y() - g3) <= 4);

        // ---- the void: no ground at all, so a cobblestone support is made ----
        ServerLevel end = server.getLevel(Level.END);
        if (end != null) {
            // Find a column of the End with nothing below at all (the seed decides where outer islands are).
            int vx = 6000;
            for (int i = 0; i < 60; i++) {
                end.getChunk(new BlockPos(vx, 0, 6000));
                if (end.getHeight(Heightmap.Types.MOTION_BLOCKING, vx, 6000) <= end.getMinBuildHeight()) {
                    break;
                }
                vx += 16;
            }
            ServerPlayer faller = player(end, "Faller");
            faller.moveTo(vx + 0.5, 100, 6000.5, 0.0F, 0.0F);
            faller.getInventory().add(new ItemStack(Items.APPLE, 2));
            GraveEvents.onPlayerDeath(faller);
            check("death over the void: grave at the death height", end.getBlockState(new BlockPos(vx, 100, 6000)).is(ModContent.GRAVE));
            check("death over the void: cobblestone under the grave", end.getBlockState(new BlockPos(vx, 99, 6000)).is(Blocks.COBBLESTONE));
        } else {
            check("the End dimension is loaded for the void test", false);
        }

        // ---- experience ----
        Settings.install(new Config(CollectMode.OWNER_ONLY, 50, true, 0, false));
        ServerPlayer sage = player(level, "Sage");
        int sx = 290;
        level.getChunk(new BlockPos(sx, 0, 200));
        int g4 = platform(level, sx, 200);
        sage.moveTo(sx + 0.5, g4, 200.5, 0.0F, 0.0F);
        sage.getInventory().add(new ItemStack(Items.BREAD, 3));
        sage.setExperienceLevels(30);
        GraveEvents.onPlayerDeath(sage);
        GraveBlockEntity sageGrave = graveOf(level, sage);
        check("xpCostPercent=50 takes half of 30 levels", sage.experienceLevel == 15);
        check("keepLostXp stores the points in the grave", sageGrave != null && sageGrave.storedXp() == XpMath.pointsTaken(30, 15)
                && sageGrave.death().xpLevels() == 15 && sageGrave.death().xpPoints() == sageGrave.storedXp());
        GraveService.collect(sage, level, sageGrave.getBlockPos(), sageGrave);
        check("collecting returns the stored experience", sage.experienceLevel == 30 && sageGrave.storedXp() == 0);

        Settings.install(new Config(CollectMode.OWNER_ONLY, 100, false, 0, false));
        ServerPlayer monk = player(level, "Monk");
        int mx = 320;
        level.getChunk(new BlockPos(mx, 0, 200));
        int g5 = platform(level, mx, 200);
        monk.moveTo(mx + 0.5, g5, 200.5, 0.0F, 0.0F);
        monk.getInventory().add(new ItemStack(Items.BREAD, 3));
        monk.setExperienceLevels(10);
        GraveEvents.onPlayerDeath(monk);
        GraveBlockEntity monkGrave = graveOf(level, monk);
        check("xpCostPercent=100 takes every level", monk.experienceLevel == 0);
        check("without keepLostXp the experience is lost", monkGrave != null && monkGrave.storedXp() == 0 && monkGrave.death().xpLevels() == 10);

        // ---- zero cost leaves the levels alone ----
        Settings.install(new Config(CollectMode.OWNER_ONLY, 0, true, 0, false));
        ServerPlayer rich = player(level, "Rich");
        int qx = 350;
        level.getChunk(new BlockPos(qx, 0, 200));
        int g6 = platform(level, qx, 200);
        rich.moveTo(qx + 0.5, g6, 200.5, 0.0F, 0.0F);
        rich.getInventory().add(new ItemStack(Items.BREAD, 3));
        rich.setExperienceLevels(20);
        GraveEvents.onPlayerDeath(rich);
        check("xpCostPercent=0 (default) takes nothing", rich.experienceLevel == 20);

        // ---- nothing to keep: no grave ----
        ServerPlayer empty = player(level, "Empty");
        empty.moveTo(380.5, g6, 200.5, 0.0F, 0.0F);
        int before = GraveStores.registry().size();
        GraveEvents.onPlayerDeath(empty);
        check("an empty inventory makes no grave", GraveStores.registry().size() == before);

        // ---- saves survive: the block entity round-trips through NBT ----
        GraveBlockEntity original = monkGrave;
        net.minecraft.nbt.CompoundTag tag = original.saveWithoutMetadata();
        tag.putString("id", "barelygravestone:gravestone");
        BlockEntity loaded = BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(), tag);
        GraveBlockEntity copy = loaded instanceof GraveBlockEntity g ? g : null;
        LOGGER.info("SELFTEST detail loaded={} id={} items={}/{} owner={} profile={} death={}", copy != null,
                copy != null && copy.graveId().equals(original.graveId()), copy == null ? -1 : copy.items().size(),
                original.items().size(), copy == null ? null : copy.ownerName(), copy == null ? null : copy.profile(),
                copy == null ? null : copy.death());
        check("the grave survives a save and load", copy != null && copy.graveId().equals(original.graveId())
                && copy.items().size() == original.items().size() && copy.ownerName().equals("Monk") && copy.profile() != null
                && copy.death() != null && copy.death().xpLevels() == 10);

        // ---- expiry ----
        Settings.install(new Config(CollectMode.OWNER_ONLY, 0, false, 1, false));
        check("a fresh grave does not expire yet", !monkGrave.expireIfDue(level));
        monkGrave.debugAge(1300L);
        check("an old grave expires and drops its items", monkGrave.expireIfDue(level) && level.getBlockState(monkGrave.getBlockPos()).isAir()
                && !level.getEntitiesOfClass(ItemEntity.class, new AABB(monkGrave.getBlockPos()).inflate(2.0)).isEmpty());
        Settings.install(new Config(CollectMode.OWNER_ONLY, 0, false, 0, false));
        GraveBlockEntity richGrave = graveOf(level, rich);
        check("expiry off (default): a grave of any age stays", richGrave != null && !richGrave.expireIfDue(level));

        // ---- the command ----
        try {
            int listed = server.getCommands().getDispatcher().execute("gravestone list", server.createCommandSourceStack());
            check("/gravestone list reports the graves", listed == GraveStores.registry().size() && listed > 0);
            GraveRegistry.Entry entry = GraveStores.registry().ownedBy(rich.getUUID()).get(0);
            String shortId = entry.id().toString().substring(0, 8);
            int removed = server.getCommands().getDispatcher().execute("gravestone remove " + shortId, server.createCommandSourceStack());
            check("/gravestone remove deletes the grave and drops its items", removed == 1
                    && !GraveStores.registry().contains(entry.id()));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            check("gravestone commands parse: " + e.getMessage(), false);
        }
        // restore: give the contents of a grave to another player, removing the grave
        ServerPlayer owner2 = player(level, "Pilgrim");
        int px = 410;
        level.getChunk(new BlockPos(px, 0, 200));
        int g7 = platform(level, px, 200);
        owner2.moveTo(px + 0.5, g7, 200.5, 0.0F, 0.0F);
        owner2.getInventory().add(new ItemStack(Items.EMERALD, 9));
        GraveEvents.onPlayerDeath(owner2);
        GraveRegistry.Entry pilgrimEntry = GraveStores.registry().ownedBy(owner2.getUUID()).get(0);
        int moved = GraveService.restore(server, pilgrimEntry, alex);
        check("restore hands another player the contents and removes the grave", moved == 9 && count(alex, Items.EMERALD) == 9
                && !GraveStores.registry().contains(pilgrimEntry.id()));
    }
}
