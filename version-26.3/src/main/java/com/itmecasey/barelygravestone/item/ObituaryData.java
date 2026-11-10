package com.itmecasey.barelygravestone.item;

import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Reads and writes what an Obituary stack carries: the death record and whether its grave is gone. */
public final class ObituaryData {
    private static final String DATA = "Data";
    private static final String GONE = "Gone";

    private ObituaryData() {
    }

    public static ItemStack create(ObituaryRecord record) {
        ItemStack stack = new ItemStack(ModContent.OBITUARY);
        CompoundTag tag = new CompoundTag();
        tag.putString(DATA, record.encode());
        tag.putBoolean(GONE, false);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.translatable("item.barelygravestone.obituary.named", record.ownerName()).withStyle(style -> style.withItalic(false)));
        return stack;
    }

    /** The record on a stack, or {@code null} for a blank one. */
    public static ObituaryRecord read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : ObituaryRecord.decode(data.copyTag().getStringOr(DATA, ""));
    }

    public static boolean gone(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(GONE, false);
    }

    public static void markGone(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(GONE, true));
    }
}
