package com.itmecasey.barelygravestone.item;

import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Reads and writes what an Obituary stack carries: the death record and whether its grave is gone. */
public final class ObituaryData {
    private static final String DATA = "Data";
    private static final String GONE = "Gone";

    private ObituaryData() {
    }

    public static ItemStack create(ObituaryRecord record) {
        ItemStack stack = new ItemStack(ModContent.OBITUARY);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(DATA, record.encode());
        tag.putBoolean(GONE, false);
        stack.setHoverName(Component.translatable("item.barelygravestone.obituary.named", record.ownerName()).withStyle(style -> style.withItalic(false)));
        return stack;
    }

    /** The record on a stack, or {@code null} for a blank one. */
    public static ObituaryRecord read(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : ObituaryRecord.decode(tag.getString(DATA));
    }

    public static boolean gone(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(GONE);
    }

    public static void markGone(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(GONE, true);
    }
}
