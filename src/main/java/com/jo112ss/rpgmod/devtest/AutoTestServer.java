package com.jo112ss.rpgmod.devtest;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.effects.ForgeOptionEffects;
import com.jo112ss.rpgmod.registry.ModRegistries;
import com.jo112ss.rpgmod.smithing.ForgeMenu;
import com.jo112ss.rpgmod.smithing.ForgedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development-only end-to-end test, active only with -Drpgmod.autotest=true (never set in normal
 * play). Places an anvil, gives materials and opens the forge GUI for the joining player; later it
 * equips the forged sword and logs the attribute values so CI can check the options apply/unapply.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class AutoTestServer {
    private AutoTestServer() {}

    public static final boolean ENABLED = Boolean.getBoolean("rpgmod.autotest");

    private static ServerPlayer player;
    private static int ticks = -1;
    private static int checkTicks = -1;

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer sp)) return;
        player = sp;
        ticks = 0;
    }

    /** Called by the client test once the result screen was captured. */
    public static void requestEquipCheck() {
        checkTicks = 0;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!ENABLED || player == null) return;
        if (ticks >= 0 && ++ticks == 40) {
            BlockPos pos = player.blockPosition().north(2);
            player.level().setBlockAndUpdate(pos, ModRegistries.FORGING_ANVIL.get().defaultBlockState());
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 6));
            player.getInventory().add(new ItemStack(Items.STICK, 3));
            player.getInventory().setSelectedSlot(8);
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new ForgeMenu(id, inv, ContainerLevelAccess.create(player.level(), pos)),
                    Component.translatable("screen.rpgmod.forge")));
            RpgMod.LOGGER.info("[AUTOTEST] opened forge menu at {}", pos);
            ticks = -1;
        }
        if (checkTicks >= 0) {
            checkTicks++;
            if (checkTicks == 1) {
                log("before equip");
                for (int i = 0; i < 9; i++) {
                    if (ForgeOptionEffects.data(player.getInventory().getItem(i)) != null) {
                        player.getInventory().setSelectedSlot(i);
                        ForgedData d = ForgeOptionEffects.data(player.getInventory().getItem(i));
                        RpgMod.LOGGER.info("[AUTOTEST] forged item in slot {}: {} maxDamage={}", i, d,
                                player.getInventory().getItem(i).getMaxDamage());
                    }
                }
            } else if (checkTicks == 10) {
                log("holding forged sword");
                player.getInventory().setSelectedSlot(8);
            } else if (checkTicks == 20) {
                log("after switching away");
                checkTicks = -1;
            }
        }
    }

    private static void log(String when) {
        RpgMod.LOGGER.info("[AUTOTEST] {}: attack={} speed={} maxHealth={} armor={} move={} kbRes={}", when,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE), player.getAttributeValue(Attributes.ATTACK_SPEED),
                player.getAttributeValue(Attributes.MAX_HEALTH), player.getAttributeValue(Attributes.ARMOR),
                player.getAttributeValue(Attributes.MOVEMENT_SPEED), player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
    }
}
