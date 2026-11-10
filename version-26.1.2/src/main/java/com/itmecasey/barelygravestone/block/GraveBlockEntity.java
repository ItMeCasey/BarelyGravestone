package com.itmecasey.barelygravestone.block;

import com.itmecasey.barelygravestone.GraveMod;
import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import com.itmecasey.barelygravestone.core.Settings;
import com.itmecasey.barelygravestone.server.GraveStores;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import com.mojang.authlib.GameProfile;

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
    private ResolvableProfile profile;
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
        this.profile = gameProfile == null ? null : ResolvableProfile.createResolved(gameProfile);
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
    public ResolvableProfile profile() {
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
            net.minecraft.world.entity.ExperienceOrb.award(serverLevel, net.minecraft.world.phys.Vec3.atCenterOf(worldPosition), storedXp);
            storedXp = 0;
        }
    }

    /** The block is going away for real (not an unload): drop what is left and forget the grave. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            dropContents(serverLevel);
            GraveStores.graveRemoved(graveId);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("GraveId", graveId.toString());
        output.putString("Owner", owner.toString());
        output.putString("OwnerName", ownerName);
        output.storeNullable("Profile", ResolvableProfile.CODEC, profile);
        output.putInt("Xp", storedXp);
        output.putLong("Created", createdGameTime);
        if (death != null) {
            output.putString("Death", death.encode());
        }
        var list = output.list("Items", ItemStack.OPTIONAL_CODEC);
        for (ItemStack stack : items) {
            list.add(stack);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try {
            graveId = UUID.fromString(input.getStringOr("GraveId", graveId.toString()));
            owner = UUID.fromString(input.getStringOr("Owner", owner.toString()));
        } catch (IllegalArgumentException e) {
            GraveMod.LOGGER.warn("Grave at {} has a damaged id; keeping the defaults", worldPosition);
        }
        ownerName = input.getStringOr("OwnerName", "");
        profile = input.read("Profile", ResolvableProfile.CODEC).orElse(null);
        storedXp = input.getIntOr("Xp", 0);
        createdGameTime = input.getLongOr("Created", 0L);
        death = ObituaryRecord.decode(input.getStringOr("Death", ""));
        items.clear();
        for (ItemStack stack : input.listOrEmpty("Items", ItemStack.OPTIONAL_CODEC)) {
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
