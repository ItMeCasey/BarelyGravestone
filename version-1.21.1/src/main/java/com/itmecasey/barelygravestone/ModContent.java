package com.itmecasey.barelygravestone;

import com.itmecasey.barelygravestone.block.GraveBlock;
import com.itmecasey.barelygravestone.block.GraveBlockEntity;
import com.itmecasey.barelygravestone.item.ObituaryItem;
import com.itmecasey.barelygravestone.menu.ObituaryMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Every block, item, block entity type and menu type the mod adds, built once and handed to the loader's registry. */
public final class ModContent {
    public static final GraveBlock GRAVE = new GraveBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            // Unbreakable by survival players (like bedrock) and immune to explosions; collecting removes it.
            .strength(-1.0F, 3600000.0F)
            .sound(SoundType.STONE)
            .noOcclusion()
            .noLootTable()
            .pushReaction(PushReaction.BLOCK));

    public static final Item GRAVE_ITEM = new BlockItem(GRAVE, new Item.Properties());

    public static final ObituaryItem OBITUARY = new ObituaryItem(new Item.Properties().stacksTo(1));

    public static final BlockEntityType<GraveBlockEntity> GRAVE_BE =
            BlockEntityType.Builder.of(GraveBlockEntity::new, GRAVE).build(null);

    /** The Obituary screen's menu type. The loader creates it with its own helper. */
    public static MenuType<ObituaryMenu> obituaryMenu;

    private ModContent() {
    }

    /** The creative tab. */
    public static CreativeModeTab creativeTab() {
        return CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + GraveMod.MODID))
                .icon(() -> new ItemStack(GRAVE_ITEM))
                .displayItems((params, output) -> output.accept(GRAVE_ITEM))
                .build();
    }
}
