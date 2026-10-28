package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.JobManager;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
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
 * (copper 40%, iron 50%, diamond and netherite 60%). Paladins block 20% more.
 *
 * <p>A player's shield never breaks: when guarding wears it down to nothing it is knocked out
 * instead - unusable for {@link #DOWN_SECONDS} seconds (netherite {@link #DOWN_SECONDS_NETHERITE}),
 * shown with the same cooldown as an axe hit - and then comes back at full durability.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class Shields {
    private Shields() {}

    /** Share of a hit the paladin blocks on top of the shield's own. */
    public static final double PALADIN_BONUS = 0.2;

    public static final int DOWN_SECONDS = 30;
    public static final int DOWN_SECONDS_NETHERITE = 15;
    /** Game time a knocked-out shield comes back (custom data on the stack). */
    public static final String DOWN_KEY = "rpgmod_shield_down";

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
        if (mat == WeaponMaterial.NETHERITE) p = p.fireResistant();
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

    /** After every other handler: the shield's wear is applied here, so it can be knocked out instead of breaking. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onBlockWear(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getBlocked()) return;
        ItemStack shield = player.getItemBlockingWith();
        if (shield == null || shield.isEmpty() || !shield.isDamageableItem()) return;
        BlocksAttacks ba = shield.get(DataComponents.BLOCKS_ATTACKS);
        if (ba == null) return;
        int amount = event.shieldDamage() >= 0 ? event.shieldDamage() : ba.itemDamage().apply(event.getBlockedDamage());
        event.setShieldDamage(0); // worn here instead
        wear(player, shield, amount);
    }

    /** Wears a player's shield by {@code amount}; at zero it is knocked out rather than broken. */
    public static void wear(ServerPlayer player, ItemStack shield, int amount) {
        if (amount <= 0 || isDown(shield)) return;
        ServerLevel level = player.level();
        amount = net.minecraft.world.item.enchantment.EnchantmentHelper.processDurabilityChange(level, shield, amount);
        if (amount <= 0) return;
        int next = shield.getDamageValue() + amount;
        if (next < shield.getMaxDamage()) {
            shield.setDamageValue(next);
            return;
        }
        // knocked out: empty bar, cooldown, back at full later
        int seconds = downSeconds(shield);
        shield.setDamageValue(shield.getMaxDamage() - 1);
        long until = level.getGameTime() + seconds * 20L;
        net.minecraft.world.item.component.CustomData.update(DataComponents.CUSTOM_DATA, shield, t -> t.putLong(DOWN_KEY, until));
        player.getCooldowns().addCooldown(shield, seconds * 20);
        player.stopUsingItem();
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
        player.sendSystemMessage(Component.translatable("rpgmod.shield.down", seconds).withStyle(ChatFormatting.RED), true);
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) {
            RpgMod.LOGGER.info("[AUTOTEST] shield down: {} for {}s, damage {}/{}", shield.getItem(), seconds, shield.getDamageValue(), shield.getMaxDamage());
        }
    }

    public static int downSeconds(ItemStack shield) {
        return ModItems.materialOf(shield.getItem()) == WeaponMaterial.NETHERITE ? DOWN_SECONDS_NETHERITE : DOWN_SECONDS;
    }

    public static boolean isDown(ItemStack s) {
        net.minecraft.world.item.component.CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().contains(DOWN_KEY);
    }

    /** Brings knocked-out shields in a player's inventory back once their time is up (and keeps the cooldown on until then). */
    public static void tickDown(ServerPlayer player) {
        long now = player.level().getGameTime();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !isDown(s)) continue;
            long until = s.get(DataComponents.CUSTOM_DATA).copyTag().getLongOr(DOWN_KEY, 0L);
            if (now >= until) {
                s.setDamageValue(0);
                net.minecraft.world.item.component.CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.remove(DOWN_KEY));
                player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(s));
                player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
                player.sendSystemMessage(Component.translatable("rpgmod.shield.back").withStyle(ChatFormatting.GREEN), true);
                if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) {
                    RpgMod.LOGGER.info("[AUTOTEST] shield back: {} damage {}/{}", s.getItem(), s.getDamageValue(), s.getMaxDamage());
                }
            } else if (!player.getCooldowns().isOnCooldown(s)) {
                // cooldowns are lost on relog: put it back for the time left
                player.getCooldowns().addCooldown(s, (int) (until - now));
            }
        }
    }

    /**
     * A spear in the main hand and a shield in the off hand: right click raises the shield (the
     * spear's own right-click charge gives way to it). Both sides: the client then uses the off hand.
     */
    @SubscribeEvent
    public static void onUseSpear(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        Player p = event.getEntity();
        if (ModItems.typeOf(event.getItemStack().getItem()) != WeaponType.SPEAR) return;
        ItemStack off = p.getOffhandItem();
        if (!off.has(DataComponents.BLOCKS_ATTACKS) || p.getCooldowns().isOnCooldown(off)) return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.PASS);
    }

    /** True while the player holds a shield up (or is raising it): skills wait until it is lowered. */
    public static boolean guarding(Player player) {
        return player.isUsingItem() && player.getUseItem().has(DataComponents.BLOCKS_ATTACKS);
    }
}
