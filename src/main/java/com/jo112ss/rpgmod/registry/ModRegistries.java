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
    public static final net.neoforged.neoforge.registries.DeferredItem<com.jo112ss.rpgmod.item.JobBookItem> JOB_BOOK =
            ITEMS.registerItem("job_change_book", com.jo112ss.rpgmod.item.JobBookItem::new, p -> p.stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final net.neoforged.neoforge.registries.DeferredItem<com.jo112ss.rpgmod.item.RecoveryPotionItem> HP_POTION =
            ITEMS.registerItem("hp_potion", p -> new com.jo112ss.rpgmod.item.RecoveryPotionItem(p, false), com.jo112ss.rpgmod.item.RecoveryPotionItem::props);
    public static final net.neoforged.neoforge.registries.DeferredItem<com.jo112ss.rpgmod.item.RecoveryPotionItem> MP_POTION =
            ITEMS.registerItem("mp_potion", p -> new com.jo112ss.rpgmod.item.RecoveryPotionItem(p, true), com.jo112ss.rpgmod.item.RecoveryPotionItem::props);

    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(RpgMod.MODID);

    /** The King Slime (a dungeon boss). Same base box as a slime; the size scales it. */
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.jo112ss.rpgmod.mob.boss.KingSlime>> KING_SLIME =
            ENTITIES.registerEntityType("king_slime", com.jo112ss.rpgmod.mob.boss.KingSlime::new, net.minecraft.world.entity.MobCategory.MONSTER,
                    b -> b.sized(0.52F, 0.52F).eyeHeight(0.325F).clientTrackingRange(16).fireImmune());

    /** The Golem (a dungeon boss): a giant stone golem (2.2x an iron golem). */
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.jo112ss.rpgmod.mob.boss.Golem>> GOLEM =
            ENTITIES.registerEntityType("golem", com.jo112ss.rpgmod.mob.boss.Golem::new, net.minecraft.world.entity.MobCategory.MONSTER,
                    b -> b.sized(1.4F * com.jo112ss.rpgmod.mob.boss.Golem.SCALE, 2.7F * com.jo112ss.rpgmod.mob.boss.Golem.SCALE).eyeHeight(2.3F * com.jo112ss.rpgmod.mob.boss.Golem.SCALE)
                            .clientTrackingRange(16).fireImmune());

    /** The Lich King: a giant skeletal sorcerer (sized by its scale attribute). */
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.jo112ss.rpgmod.mob.boss.Lich>> LICH =
            ENTITIES.registerEntityType("lich", com.jo112ss.rpgmod.mob.boss.Lich::new, net.minecraft.world.entity.MobCategory.MONSTER,
                    b -> b.sized(0.6F, 1.99F).eyeHeight(1.74F).clientTrackingRange(16).fireImmune());

    /** The Abyssal Wither: the hidden boss behind the Ender Dragon's key (a wither, sized by its scale attribute). */
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.jo112ss.rpgmod.mob.boss.AbyssWither>> ABYSS_WITHER =
            ENTITIES.registerEntityType("abyss_wither", com.jo112ss.rpgmod.mob.boss.AbyssWither::new, net.minecraft.world.entity.MobCategory.MONSTER,
                    b -> b.sized(0.9F, 3.5F).clientTrackingRange(16).fireImmune());

    /** The Spider Queen: a giant spider (sized by her scale attribute). */
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.jo112ss.rpgmod.mob.boss.SpiderQueen>> SPIDER_QUEEN =
            ENTITIES.registerEntityType("spider_queen", com.jo112ss.rpgmod.mob.boss.SpiderQueen::new, net.minecraft.world.entity.MobCategory.MONSTER,
                    b -> b.sized(1.4F, 0.9F).eyeHeight(0.65F).clientTrackingRange(16));

    /** Fallen adventurers: zombies that still fight in their old job's way, deep underground. */
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.jo112ss.rpgmod.mob.revenant.Revenant>> REVENANT =
            ENTITIES.registerEntityType("revenant", com.jo112ss.rpgmod.mob.revenant.Revenant::new, net.minecraft.world.entity.MobCategory.MONSTER,
                    b -> b.sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.0125F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());

    /** The Golem's armour pillars. */
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.jo112ss.rpgmod.mob.boss.GolemPillar>> GOLEM_PILLAR =
            ENTITIES.registerEntityType("golem_pillar", com.jo112ss.rpgmod.mob.boss.GolemPillar::new, net.minecraft.world.entity.MobCategory.MONSTER,
                    b -> b.sized(1.0F, 2.6F).clientTrackingRange(10).fireImmune());

    /** Things skills set down for a while (pirate's cannon, keg and flag, the lancer's thrown spear). */
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.jo112ss.rpgmod.job.SkillProp>> SKILL_PROP =
            ENTITIES.registerEntityType("skill_prop", com.jo112ss.rpgmod.job.SkillProp::new, net.minecraft.world.entity.MobCategory.MISC,
                    b -> b.sized(0.8F, 0.8F).clientTrackingRange(8).updateInterval(1).noSave().noSummon());

    public static final DeferredHolder<MenuType<?>, MenuType<ForgeMenu>> FORGE_MENU = MENUS.register("forge",
            () -> IMenuTypeExtension.create((windowId, inv, buf) -> new ForgeMenu(windowId, inv)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ForgedData>> FORGED_DATA =
            DATA_COMPONENTS.registerComponentType("forged", b -> b.persistent(ForgedData.CODEC).networkSynchronized(ForgedData.STREAM_CODEC));

    /** A boss's unique weapon: which, and the level it dropped at. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.jo112ss.rpgmod.item.UniqueWeapon>> UNIQUE_WEAPON =
            DATA_COMPONENTS.registerComponentType("unique", b -> b.persistent(com.jo112ss.rpgmod.item.UniqueWeapon.CODEC).networkSynchronized(com.jo112ss.rpgmod.item.UniqueWeapon.STREAM_CODEC));

    /** The gem of a gem item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.jo112ss.rpgmod.gem.Gem>> GEM =
            DATA_COMPONENTS.registerComponentType("gem", b -> b.persistent(com.jo112ss.rpgmod.gem.Gem.CODEC).networkSynchronized(com.jo112ss.rpgmod.gem.Gem.STREAM_CODEC));

    /** Gems socketed into gear. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.jo112ss.rpgmod.gem.Sockets>> SOCKETS =
            DATA_COMPONENTS.registerComponentType("sockets", b -> b.persistent(com.jo112ss.rpgmod.gem.Sockets.CODEC).networkSynchronized(com.jo112ss.rpgmod.gem.Sockets.STREAM_CODEC));

    public static final DeferredItem<net.minecraft.world.item.Item> GEM_COMMON = ITEMS.registerSimpleItem("gem_common", p -> p.stacksTo(1));
    public static final DeferredItem<net.minecraft.world.item.Item> GEM_RARE = ITEMS.registerSimpleItem("gem_rare", p -> p.stacksTo(1));
    public static final DeferredItem<net.minecraft.world.item.Item> GEM_LEGENDARY = ITEMS.registerSimpleItem("gem_legendary", p -> p.stacksTo(1).fireResistant());

    public static final DeferredBlock<com.jo112ss.rpgmod.gem.SocketTableBlock> SOCKET_TABLE = BLOCKS.registerBlock("socket_table",
            com.jo112ss.rpgmod.gem.SocketTableBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(3.5F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.AMETHYST));

    public static final DeferredItem<BlockItem> SOCKET_TABLE_ITEM = ITEMS.registerSimpleBlockItem("socket_table", SOCKET_TABLE);

    /** The dungeon altar and what can be offered on it. */
    public static final DeferredBlock<com.jo112ss.rpgmod.dungeon.AltarBlock> DUNGEON_ALTAR = BLOCKS.registerBlock("dungeon_altar",
            com.jo112ss.rpgmod.dungeon.AltarBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(5.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS)
                    .lightLevel(com.jo112ss.rpgmod.dungeon.AltarBlock::light));
    public static final DeferredItem<BlockItem> DUNGEON_ALTAR_ITEM = ITEMS.registerSimpleBlockItem("dungeon_altar", DUNGEON_ALTAR);
    public static final DeferredItem<net.minecraft.world.item.Item> DUNGEON_KEY = ITEMS.registerSimpleItem("dungeon_key",
            p -> p.stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON));
    public static final DeferredItem<net.minecraft.world.item.Item> SEALED_KEY = ITEMS.registerSimpleItem("sealed_key",
            p -> p.stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE));
    public static final DeferredItem<net.minecraft.world.item.Item> SLIME_CROWN = ITEMS.registerSimpleItem("slime_crown",
            p -> p.stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE));
    public static final DeferredItem<net.minecraft.world.item.Item> GOLEM_CORE = ITEMS.registerSimpleItem("golem_core",
            p -> p.stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE));
    public static final DeferredItem<net.minecraft.world.item.Item> SOUL_PHYLACTERY = ITEMS.registerSimpleItem("soul_phylactery",
            p -> p.stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE));
    public static final DeferredItem<net.minecraft.world.item.Item> QUEEN_COCOON = ITEMS.registerSimpleItem("queen_cocoon",
            p -> p.stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE));
    /** The Ender Dragon's key: offered at a dungeon altar, it opens the Abyssal Wither's arena. */
    public static final DeferredItem<net.minecraft.world.item.Item> ABYSS_KEY = ITEMS.registerSimpleItem("abyss_key",
            p -> p.stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC));

    /** Spawn eggs for the mod's monsters: the four bosses, a fallen adventurer of each job, a party. */
    public static final java.util.Map<com.jo112ss.rpgmod.item.RpgSpawnEggItem.Kind, DeferredItem<com.jo112ss.rpgmod.item.RpgSpawnEggItem>> SPAWN_EGGS = registerEggs();

    private static java.util.Map<com.jo112ss.rpgmod.item.RpgSpawnEggItem.Kind, DeferredItem<com.jo112ss.rpgmod.item.RpgSpawnEggItem>> registerEggs() {
        var out = new java.util.EnumMap<com.jo112ss.rpgmod.item.RpgSpawnEggItem.Kind, DeferredItem<com.jo112ss.rpgmod.item.RpgSpawnEggItem>>(
                com.jo112ss.rpgmod.item.RpgSpawnEggItem.Kind.class);
        for (var k : com.jo112ss.rpgmod.item.RpgSpawnEggItem.Kind.values()) {
            out.put(k, ITEMS.registerItem(k.itemId(), p -> new com.jo112ss.rpgmod.item.RpgSpawnEggItem(p, k),
                    p -> k.boss() ? p.rarity(net.minecraft.world.item.Rarity.EPIC) : p));
        }
        return out;
    }

    public static final DeferredHolder<MenuType<?>, MenuType<com.jo112ss.rpgmod.gem.SocketMenu>> SOCKET_MENU = MENUS.register("socket",
            () -> IMenuTypeExtension.create((windowId, inv, buf) -> new com.jo112ss.rpgmod.gem.SocketMenu(windowId, inv)));

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

    /** "job:level" of a player, synced to everyone who can see them (for the name plates of allies). */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<String>> JOB_TAG =
            ATTACHMENTS.register("job_tag", () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> "")
                    .sync(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
                    .build());

    /** Monster level (0 = not levelled yet): saved with the entity, synced to everyone tracking it. */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Integer>> MOB_LEVEL =
            ATTACHMENTS.register("mob_level", () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> 0)
                    .serialize(com.mojang.serialization.Codec.INT.fieldOf("level"), v -> v > 0)
                    .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                    .build());

    /** Elite monster traits (a bit mask of {@link com.jo112ss.rpgmod.mob.Elite.Trait}; 0 = an ordinary monster). */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Integer>> MOB_ELITE =
            ATTACHMENTS.register("mob_elite", () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> 0)
                    .serialize(com.mojang.serialization.Codec.INT.fieldOf("elite"), v -> v != 0)
                    .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                    .build());


    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpgmod"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> new ItemStack(FORGING_ANVIL_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(FORGING_ANVIL_ITEM.get());
                output.accept(SOCKET_TABLE_ITEM.get());
                output.accept(DUNGEON_ALTAR_ITEM.get());
                output.accept(DUNGEON_KEY.get());
                for (int lv : new int[] {30, 60, 100, 150}) output.accept(com.jo112ss.rpgmod.dungeon.DungeonManager.sealedKey(lv));
                output.accept(SLIME_CROWN.get());
                output.accept(GOLEM_CORE.get());
                output.accept(SOUL_PHYLACTERY.get());
                output.accept(QUEEN_COCOON.get());
                output.accept(ABYSS_KEY.get());
                for (var egg : SPAWN_EGGS.values()) output.accept(egg.get());
                // one gem of every special effect, and one of every stat (rare, at its top value)
                for (com.jo112ss.rpgmod.gem.GemSpecial s : com.jo112ss.rpgmod.gem.GemSpecial.values()) {
                    output.accept(com.jo112ss.rpgmod.gem.GemItems.stack(com.jo112ss.rpgmod.gem.Gem.of(s)));
                }
                for (com.jo112ss.rpgmod.smithing.core.OptionType t : com.jo112ss.rpgmod.gem.GemRoller.statPool()) {
                    output.accept(com.jo112ss.rpgmod.gem.GemItems.stack(new com.jo112ss.rpgmod.gem.Gem(t.id, com.jo112ss.rpgmod.gem.Gem.COMMON, com.jo112ss.rpgmod.gem.GemRoller.max(t))));
                }
                // the bosses' unique weapons: each at Lv100 and Lv180, legendary (fixed options per weapon)
                for (int lv : new int[] {100, 180}) {
                    for (var u : com.jo112ss.rpgmod.item.UniqueWeapons.Def.values()) {
                        output.accept(com.jo112ss.rpgmod.item.UniqueWeapons.create(u, lv, com.jo112ss.rpgmod.smithing.core.Quality.LEGENDARY,
                                new java.util.Random(u.ordinal() * 31L + lv)));
                    }
                }
                output.accept(SKILL_BOOK.get());
                output.accept(JOB_BOOK.get());
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
