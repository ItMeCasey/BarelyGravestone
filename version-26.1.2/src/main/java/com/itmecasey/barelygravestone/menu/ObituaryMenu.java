package com.itmecasey.barelygravestone.menu;

import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.block.GraveBlockEntity;
import com.itmecasey.barelygravestone.core.Settings;
import com.itmecasey.barelygravestone.server.GraveStores;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The menu behind the Obituary screen. It only displays: the stacks still in the grave travel as ordinary slot contents (a
 * read-only 9 by 6 grid) and a few numbers as data slots, so there is no custom network code and the list updates by itself
 * as items are taken.
 */
public class ObituaryMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 9;
    public static final int ROWS = 6;
    public static final int SLOTS = COLUMNS * ROWS;
    /** Where the grid starts inside the screen's background. */
    public static final int GRID_X = 154;
    public static final int GRID_Y = 30;

    public static final int DATA_GONE = 0;
    public static final int DATA_XP_LOW = 1;
    public static final int DATA_XP_HIGH = 2;
    public static final int DATA_FLAGS = 3;
    public static final int FLAG_SHOW_COORDINATES = 1;

    private final SimpleContainer view = new SimpleContainer(SLOTS);
    private final ContainerData data = new SimpleContainerData(4);
    @Nullable
    private final ServerLevel level;
    private final BlockPos pos;
    private final UUID graveId;

    /** Client side: waits for the server to fill it. */
    public ObituaryMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, BlockPos.ZERO, new UUID(0L, 0L));
    }

    /** Server side: reads live from the grave. */
    public ObituaryMenu(int containerId, Inventory inventory, @Nullable ServerLevel level, BlockPos pos, UUID graveId) {
        super(ModContent.obituaryMenu, containerId);
        this.level = level;
        this.pos = pos;
        this.graveId = graveId;
        addDataSlots(data);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                addSlot(new ReadOnlySlot(view, row * COLUMNS + col, GRID_X + col * 18, GRID_Y + row * 18));
            }
        }
        if (level != null) {
            refresh();
        }
    }

    /** Copies the grave's current contents into the display slots. Server side only. */
    private void refresh() {
        if (level == null) {
            return;
        }
        boolean registered = GraveStores.registry().contains(graveId);
        List<ItemStack> stacks = List.of();
        int xp = 0;
        if (registered && level.getBlockEntity(pos) instanceof GraveBlockEntity grave && grave.graveId().equals(graveId)) {
            stacks = grave.items();
            xp = grave.storedXp();
        }
        for (int i = 0; i < SLOTS; i++) {
            ItemStack wanted = i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY;
            if (!ItemStack.matches(view.getItem(i), wanted)) {
                view.setItem(i, wanted.copy());
            }
        }
        data.set(DATA_GONE, registered ? 0 : 1);
        data.set(DATA_XP_LOW, xp & 0x7FFF);
        data.set(DATA_XP_HIGH, xp >> 15);
        data.set(DATA_FLAGS, Settings.showCoordinates() ? FLAG_SHOW_COORDINATES : 0);
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    public boolean graveGone() {
        return data.get(DATA_GONE) != 0;
    }

    /** Experience points still in the grave. */
    public int storedXp() {
        return data.get(DATA_XP_LOW) | (data.get(DATA_XP_HIGH) << 15);
    }

    public boolean showCoordinates() {
        return (data.get(DATA_FLAGS) & FLAG_SHOW_COORDINATES) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** A slot that shows a stack and never lets it be moved. */
    private static final class ReadOnlySlot extends Slot {
        ReadOnlySlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
