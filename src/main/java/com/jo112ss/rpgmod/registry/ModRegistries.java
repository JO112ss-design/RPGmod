package com.jo112ss.rpgmod.registry;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.smithing.ForgeMenu;
import com.jo112ss.rpgmod.smithing.ForgedData;
import com.jo112ss.rpgmod.smithing.ForgingAnvilBlock;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistries {
    private ModRegistries() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RpgMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RpgMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, RpgMod.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RpgMod.MODID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, RpgMod.MODID);

    public static final DeferredBlock<ForgingAnvilBlock> FORGING_ANVIL = BLOCKS.registerBlock("forging_anvil",
            ForgingAnvilBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(5.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.ANVIL));

    public static final DeferredItem<BlockItem> FORGING_ANVIL_ITEM = ITEMS.registerSimpleBlockItem("forging_anvil", FORGING_ANVIL);

    public static final DeferredHolder<MenuType<?>, MenuType<ForgeMenu>> FORGE_MENU = MENUS.register("forge",
            () -> IMenuTypeExtension.create((windowId, inv, buf) -> new ForgeMenu(windowId, inv)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ForgedData>> FORGED_DATA =
            DATA_COMPONENTS.registerComponentType("forged", b -> b.persistent(ForgedData.CODEC).networkSynchronized(ForgedData.STREAM_CODEC));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpgmod"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> new ItemStack(FORGING_ANVIL_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(FORGING_ANVIL_ITEM.get());
                for (com.jo112ss.rpgmod.item.WeaponType t : com.jo112ss.rpgmod.item.WeaponType.values()) {
                    for (com.jo112ss.rpgmod.item.WeaponMaterial m : com.jo112ss.rpgmod.item.WeaponMaterial.values()) {
                        output.accept(com.jo112ss.rpgmod.item.ModItems.get(t, m));
                    }
                }
            }).build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        CREATIVE_TABS.register(modBus);
        DATA_COMPONENTS.register(modBus);
    }
}
