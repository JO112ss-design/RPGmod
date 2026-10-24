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

    public static final net.neoforged.neoforge.registries.DeferredItem<com.jo112ss.rpgmod.item.SkillBookItem> SKILL_BOOK =
            ITEMS.registerItem("skill_book", com.jo112ss.rpgmod.item.SkillBookItem::new, p -> p.stacksTo(64).rarity(net.minecraft.world.item.Rarity.RARE));
    public static final net.neoforged.neoforge.registries.DeferredItem<com.jo112ss.rpgmod.item.RecoveryPotionItem> HP_POTION =
            ITEMS.registerItem("hp_potion", p -> new com.jo112ss.rpgmod.item.RecoveryPotionItem(p, false), com.jo112ss.rpgmod.item.RecoveryPotionItem::props);
    public static final net.neoforged.neoforge.registries.DeferredItem<com.jo112ss.rpgmod.item.RecoveryPotionItem> MP_POTION =
            ITEMS.registerItem("mp_potion", p -> new com.jo112ss.rpgmod.item.RecoveryPotionItem(p, true), com.jo112ss.rpgmod.item.RecoveryPotionItem::props);

    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(RpgMod.MODID);

    /** The blood moon boss. Same base box as a slime; the size scales it. */
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.jo112ss.rpgmod.mob.boss.KingSlime>> KING_SLIME =
            ENTITIES.registerEntityType("king_slime", com.jo112ss.rpgmod.mob.boss.KingSlime::new, net.minecraft.world.entity.MobCategory.MONSTER,
                    b -> b.sized(0.52F, 0.52F).eyeHeight(0.325F).clientTrackingRange(16).fireImmune());

    public static final DeferredHolder<MenuType<?>, MenuType<ForgeMenu>> FORGE_MENU = MENUS.register("forge",
            () -> IMenuTypeExtension.create((windowId, inv, buf) -> new ForgeMenu(windowId, inv)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ForgedData>> FORGED_DATA =
            DATA_COMPONENTS.registerComponentType("forged", b -> b.persistent(ForgedData.CODEC).networkSynchronized(ForgedData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.jo112ss.rpgmod.job.SpellLoadout>> SPELL_LOADOUT =
            DATA_COMPONENTS.registerComponentType("spells", b -> b.persistent(com.jo112ss.rpgmod.job.SpellLoadout.CODEC)
                    .networkSynchronized(com.jo112ss.rpgmod.job.SpellLoadout.STREAM_CODEC));

    public static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.ATTACHMENT_TYPES, RpgMod.MODID);

    /** Job progress on each player: saved, copied on death, synced to the owner. */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<com.jo112ss.rpgmod.job.JobData>> JOB_DATA =
            ATTACHMENTS.register("job", () -> net.neoforged.neoforge.attachment.AttachmentType.builder(com.jo112ss.rpgmod.job.JobData::new)
                    .serialize(com.jo112ss.rpgmod.job.JobData.CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, com.jo112ss.rpgmod.job.JobData.STREAM_CODEC)
                    .build());

    /** Monster level (0 = not levelled yet): saved with the entity, synced to everyone tracking it. */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Integer>> MOB_LEVEL =
            ATTACHMENTS.register("mob_level", () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> 0)
                    .serialize(com.mojang.serialization.Codec.INT.fieldOf("level"), v -> v > 0)
                    .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                    .build());

    /** Overworld: the day whose blood moon already had its boss (so it is not summoned twice). */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Long>> BOSS_DAY =
            ATTACHMENTS.register("boss_day", () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> -1L)
                    .serialize(com.mojang.serialization.Codec.LONG.fieldOf("day"), v -> v >= 0)
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpgmod"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> new ItemStack(FORGING_ANVIL_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(FORGING_ANVIL_ITEM.get());
                output.accept(SKILL_BOOK.get());
                output.accept(HP_POTION.get());
                output.accept(MP_POTION.get());
                for (com.jo112ss.rpgmod.item.WeaponType t : com.jo112ss.rpgmod.item.WeaponType.values()) {
                    if (t.isVanilla()) continue;
                    for (com.jo112ss.rpgmod.item.WeaponMaterial m : com.jo112ss.rpgmod.item.WeaponMaterial.values()) {
                        output.accept(com.jo112ss.rpgmod.item.ModItems.get(t, m));
                    }
                }
            }).build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        MENUS.register(modBus);
        CREATIVE_TABS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
    }
}
