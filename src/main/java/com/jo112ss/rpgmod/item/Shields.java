package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.JobManager;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * Shields only take off part of a blocked hit: the vanilla shield 20%, forged shields by material
 * (copper 40%, iron 50%, diamond and netherite 60%; netherite never breaks). Paladins block 20% more.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class Shields {
    private Shields() {}

    /** Share of a hit the paladin blocks on top of the shield's own. */
    public static final double PALADIN_BONUS = 0.2;

    /** Share of a blocked hit a shield takes off (before the paladin bonus). */
    public static double block(ItemStack shield) {
        if (shield.is(Items.SHIELD)) return 0.2;
        if (ModItems.typeOf(shield.getItem()) == WeaponType.SHIELD) {
            WeaponMaterial m = ModItems.materialOf(shield.getItem());
            if (m != null) return switch (m) {
                case COPPER -> 0.4;
                case IRON -> 0.5;
                case GOLD -> 0.45;
                case DIAMOND, NETHERITE -> 0.6;
            };
        }
        return 0.2; // other mods' shields
    }

    /** What this player blocks with this shield. */
    public static double block(Player player, ItemStack shield) {
        double b = block(shield);
        if (player != null && "paladin".equals(JobManager.data(player).jobId())) b += PALADIN_BONUS;
        return Math.min(1, b);
    }

    /** Item properties of a forged shield: a vanilla shield in the material's colour. */
    public static Item.Properties properties(Item.Properties p, WeaponMaterial mat) {
        DyeColor color = switch (mat) {
            case COPPER -> DyeColor.ORANGE;
            case IRON -> DyeColor.LIGHT_GRAY;
            case GOLD -> DyeColor.YELLOW;
            case DIAMOND -> DyeColor.CYAN;
            case NETHERITE -> DyeColor.BLACK;
        };
        p = p.durability(mat.bowDurability)
                .component(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
                .component(DataComponents.BASE_COLOR, color)
                .repairable(mat.tool.repairItems())
                .enchantable(Math.max(1, mat.tool.enchantmentValue() / 10))
                .equippableUnswappable(EquipmentSlot.OFFHAND)
                .delayedComponent(DataComponents.BLOCKS_ATTACKS, context -> new BlocksAttacks(
                        0.25F, 1.0F,
                        List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
                        new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
                        Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
                        Optional.of(SoundEvents.SHIELD_BLOCK),
                        Optional.of(SoundEvents.SHIELD_BREAK)))
                .component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK);
        if (mat == WeaponMaterial.NETHERITE) p = p.fireResistant().component(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        return p;
    }

    /** A blocked hit: only the shield's share is taken off, the rest gets through. */
    @SubscribeEvent
    public static void onBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.getBlocked()) return;
        LivingEntity e = event.getEntity();
        ItemStack shield = e.getItemBlockingWith();
        if (shield == null || shield.isEmpty()) return;
        event.setBlockedDamage((float) (event.getOriginalBlockedDamage() * block(player, shield)));
    }

    /** True while the player holds a shield up (or is raising it): skills wait until it is lowered. */
    public static boolean guarding(Player player) {
        return player.isUsingItem() && player.getUseItem().has(DataComponents.BLOCKS_ATTACKS);
    }
}
