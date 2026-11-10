package com.itmecasey.barelygravestone.block;

import com.itmecasey.barelygravestone.GraveMod;
import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import com.itmecasey.barelygravestone.core.Settings;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The data of one grave: who it belongs to (with their game profile, so the renderer can draw their skin), what is inside,
 * the experience kept for them and the facts the Obituary reports. Everything lives here, so a grave survives restarts and
 * works the same on a dedicated server and in single player.
 */
public class GraveBlockEntity extends BlockEntity {
    private UUID graveId = UUID.randomUUID();
    private UUID owner = new UUID(0L, 0L);
    private String ownerName = "";
    @Nullable
    private GameProfile profile;
    private final List<ItemStack> items = new ArrayList<>();
    private int storedXp;
    private long createdGameTime;
    @Nullable
    private ObituaryRecord death;

    public GraveBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.GRAVE_BE, pos, state);
    }

    /** Fills a freshly placed grave. */
    public void fill(UUID graveId, UUID owner, String ownerName, GameProfile gameProfile, List<ItemStack> stacks, int xp,
                     long gameTime, ObituaryRecord record) {
        this.graveId = graveId;
        this.owner = owner;
        this.ownerName = ownerName;
        this.profile = gameProfile;
        this.items.clear();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                this.items.add(stack.copy());
            }
        }
        this.storedXp = xp;
        this.createdGameTime = gameTime;
        this.death = record;
        syncToClients();
    }

    public UUID graveId() {
        return graveId;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    @Nullable
    public GameProfile profile() {
        return profile;
    }

    public int storedXp() {
        return storedXp;
    }

    @Nullable
    public ObituaryRecord death() {
        return death;
    }

    /** The stacks still in the grave (live list; callers that change it call {@link #contentsChanged()}). */
    public List<ItemStack> items() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty() && storedXp <= 0;
    }

    /** Takes the experience out of the grave. */
    public int takeXp() {
        int xp = storedXp;
        storedXp = 0;
        contentsChanged();
        return xp;
    }

    /** Call after changing {@link #items()}: saves and tells nearby clients. */
    public void contentsChanged() {
        items.removeIf(ItemStack::isEmpty);
        setChanged();
        syncToClients();
    }

    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    /** Once a second: a grave with an expiry timer crumbles when it runs out and drops what it holds. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, GraveBlockEntity grave) {
        if (level instanceof ServerLevel serverLevel && level.getGameTime() % 20L == 0L) {
            grave.expireIfDue(serverLevel);
        }
    }

    /** Removes the grave (dropping its contents) when the expiry timer is on and has run out. Returns whether it did. */
    public boolean expireIfDue(ServerLevel serverLevel) {
        int minutes = Settings.graveExpiryMinutes();
        if (minutes > 0 && createdGameTime != 0 && serverLevel.getGameTime() - createdGameTime >= minutes * 1200L) {
            GraveMod.LOGGER.info("Grave {} of {} at {} expired", graveId, ownerName, worldPosition);
            serverLevel.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    /** Dev and test hook: pretends the grave is older by the given number of ticks. */
    public void debugAge(long ticks) {
        createdGameTime -= ticks;
    }

    /** Drops everything the grave still holds at its position (expiry, creative break, command). */
    public void dropContents(ServerLevel serverLevel) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                serverLevel.addFreshEntity(new ItemEntity(serverLevel, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5, stack.copy()));
            }
        }
        items.clear();
        if (storedXp > 0) {
            ExperienceOrb.award(serverLevel, Vec3.atCenterOf(worldPosition), storedXp);
            storedXp = 0;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("GraveId", graveId.toString());
        tag.putString("Owner", owner.toString());
        tag.putString("OwnerName", ownerName);
        if (profile != null) {
            tag.put("Profile", NbtUtils.writeGameProfile(new CompoundTag(), profile));
        }
        tag.putInt("Xp", storedXp);
        tag.putLong("Created", createdGameTime);
        if (death != null) {
            tag.putString("Death", death.encode());
        }
        ListTag list = new ListTag();
        for (ItemStack stack : items) {
            list.add(stack.save(new CompoundTag()));
        }
        tag.put("Items", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        try {
            if (tag.contains("GraveId")) {
                graveId = UUID.fromString(tag.getString("GraveId"));
            }
            if (tag.contains("Owner")) {
                owner = UUID.fromString(tag.getString("Owner"));
            }
        } catch (IllegalArgumentException e) {
            GraveMod.LOGGER.warn("Grave at {} has a damaged id; keeping the defaults", worldPosition);
        }
        ownerName = tag.getString("OwnerName");
        profile = tag.contains("Profile", Tag.TAG_COMPOUND) ? NbtUtils.readGameProfile(tag.getCompound("Profile")) : null;
        storedXp = tag.getInt("Xp");
        createdGameTime = tag.getLong("Created");
        death = ObituaryRecord.decode(tag.getString("Death"));
        items.clear();
        for (Tag entry : tag.getList("Items", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) entry);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
