package com.itmecasey.barelygravestone.client;

import com.itmecasey.barelygravestone.core.ObituaryRecord;
import com.itmecasey.barelygravestone.core.TimeText;
import com.itmecasey.barelygravestone.item.ObituaryData;
import com.itmecasey.barelygravestone.item.ObituaryItem;
import com.itmecasey.barelygravestone.menu.ObituaryMenu;
import java.time.ZoneId;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Obituary as an open book. The left page tells how, when and where the player died and what the grave cost in
 * experience; the right page shows what is still in the grave and updates while it is open.
 */
public class ObituaryScreen extends AbstractContainerScreen<ObituaryMenu> {
    private static final int WIDTH = 322;
    private static final int HEIGHT = 158;
    private static final int COVER = 0xFF3A2414;
    private static final int COVER_LIGHT = 0xFF5A3A20;
    private static final int PAPER = 0xFFE8D8AE;
    private static final int PAPER_SHADE = 0xFFD6C28F;
    private static final int SPINE = 0xFFB89F66;
    private static final int INK = 0xFF3B2A16;
    private static final int INK_SOFT = 0xFF6B5636;
    private static final int RED_INK = 0xFF8A2A1E;

    @Nullable
    private final ObituaryRecord record;

    public ObituaryScreen(ObituaryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.record = findRecord(inventory);
    }

    private static @Nullable ObituaryRecord findRecord(Inventory inventory) {
        ItemStack main = inventory.player.getMainHandItem();
        ItemStack off = inventory.player.getOffhandItem();
        for (ItemStack stack : List.of(main, off)) {
            if (stack.getItem() instanceof ObituaryItem) {
                ObituaryRecord found = ObituaryData.read(stack);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int l = leftPos;
        int t = topPos;
        int mid = l + WIDTH / 2;
        // Cover, then the two pages and the spine between them.
        graphics.fill(l, t, l + WIDTH, t + HEIGHT, COVER);
        graphics.fill(l + 1, t + 1, l + WIDTH - 1, t + 3, COVER_LIGHT);
        graphics.fill(l + 5, t + 5, mid - 1, t + HEIGHT - 5, PAPER);
        graphics.fill(mid + 1, t + 5, l + WIDTH - 5, t + HEIGHT - 5, PAPER);
        graphics.fill(mid - 4, t + 5, mid - 1, t + HEIGHT - 5, PAPER_SHADE);
        graphics.fill(mid + 1, t + 5, mid + 4, t + HEIGHT - 5, PAPER_SHADE);
        graphics.fill(mid - 1, t + 5, mid + 1, t + HEIGHT - 5, SPINE);

        drawLeftPage(graphics, l + 12, t + 12, WIDTH / 2 - 28);
        drawRightPage(graphics, l, t);
    }

    private void drawLeftPage(GuiGraphicsExtractor graphics, int x, int y, int width) {
        if (record == null) {
            graphics.text(font, Component.translatable("gui.barelygravestone.blank"), x, y, INK_SOFT, false);
            return;
        }
        graphics.text(font, Component.translatable("gui.barelygravestone.obituary").withStyle(style -> style.withBold(true)), x, y, RED_INK, false);
        y += 13;
        graphics.text(font, Component.translatable("gui.barelygravestone.in_memory", record.ownerName()), x, y, INK, false);
        y += 13;
        y = wrapped(graphics, Component.literal(record.message()), x, y, width, INK) + 5;

        graphics.text(font, Component.translatable("gui.barelygravestone.when"), x, y, INK_SOFT, false);
        y += 10;
        String when = TimeText.realTime(record.realMillis(), ZoneId.systemDefault());
        y = wrapped(graphics, Component.translatable("gui.barelygravestone.when_value", when, record.gameDay()), x, y, width, INK) + 5;

        graphics.text(font, Component.translatable("gui.barelygravestone.where"), x, y, INK_SOFT, false);
        y += 10;
        Component place = menu.showCoordinates()
                ? Component.translatable("gui.barelygravestone.where_value", record.dimensionName(), record.x(), record.y(), record.z())
                : Component.translatable("gui.barelygravestone.where_hidden", record.dimensionName());
        y = wrapped(graphics, place, x, y, width, INK) + 5;

        if (record.xpLevels() > 0) {
            graphics.text(font, Component.translatable("gui.barelygravestone.xp"), x, y, INK_SOFT, false);
            y += 10;
            y = wrapped(graphics, Component.translatable("gui.barelygravestone.xp_cost", record.xpLevels()), x, y, width, INK);
            if (record.xpPoints() > 0) {
                Component kept = menu.storedXp() > 0
                        ? Component.translatable("gui.barelygravestone.xp_kept", menu.storedXp())
                        : Component.translatable("gui.barelygravestone.xp_taken");
                wrapped(graphics, kept, x, y, width, INK);
            }
        }
    }

    private int wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.text(font, line, x, y, color, false);
            y += font.lineHeight + 1;
        }
        return y;
    }

    private void drawRightPage(GuiGraphicsExtractor graphics, int l, int t) {
        int x = l + ObituaryMenu.GRID_X;
        graphics.text(font, Component.translatable("gui.barelygravestone.contents"), x, t + 14, INK_SOFT, false);
        // No slot grid: the stacks are drawn straight on the page.
        if (menu.graveGone()) {
            graphics.text(font, Component.translatable("gui.barelygravestone.gone"), x, t + HEIGHT - 17, RED_INK, false);
        } else {
            int count = 0;
            for (var slot : menu.slots) {
                if (slot.hasItem()) {
                    count++;
                }
            }
            graphics.text(font, Component.translatable("gui.barelygravestone.count", count), x, t + HEIGHT - 17, INK, false);
        }
    }
}
