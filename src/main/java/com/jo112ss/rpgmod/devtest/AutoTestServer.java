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

    private static volatile boolean jobSetup;
    private static volatile boolean forgeRequested;

    /** Client test: level the player up, unlock some panel cells, hand over a staff with spells. */
    public static void requestJobSetup() {
        jobSetup = true;
    }

    /** Client test: place an anvil and open the forge GUI. */
    public static void requestForge() {
        forgeRequested = true;
    }

    /** Called by the client test once the result screen was captured. */
    public static void requestEquipCheck() {
        checkTicks = 0;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!ENABLED || player == null) return;
        if (jobSetup) {
            jobSetup = false;
            com.jo112ss.rpgmod.job.JobManager.grantExp(player, 3000);
            com.jo112ss.rpgmod.job.JobData jd = com.jo112ss.rpgmod.job.JobManager.data(player);
            // unlock a path towards the outer ring so the panel shows every state
            for (int id : new int[] {1, 2, 7, 8, 9, 19, 20}) com.jo112ss.rpgmod.job.JobManager.unlockNode(player, id);
            ItemStack staff = new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(com.jo112ss.rpgmod.item.WeaponType.STAFF, com.jo112ss.rpgmod.item.WeaponMaterial.IRON));
            player.getInventory().setItem(0, staff);
            player.getInventory().setSelectedSlot(0);
            java.util.List<String> spells = com.jo112ss.rpgmod.job.JobManager.knownSpells(jd);
            for (int i = 0; i < spells.size() && i < 4; i++) com.jo112ss.rpgmod.job.JobManager.setSpell(player, i, spells.get(i));
            RpgMod.LOGGER.info("[AUTOTEST] job setup: job={} level={} points={} unlocked={} spells={}", jd.jobId(), jd.level(), jd.freePoints(), jd.unlocked(), spells);
        }
        if (ticks >= 0 && forgeRequested) {
            forgeRequested = false;
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
                critT = 0;
            }
        }
        if (critT >= 0) critTest();
    }

    private static int critT = -1;

    @SubscribeEvent
    public static void onDamagePost(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e) {
        if (!ENABLED || dummy == null || e.getEntity() != dummy) return;
        var R = net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.class;
        StringBuilder sb = new StringBuilder();
        for (var r : R.getEnumConstants()) {
            float v = e.getReduction(r);
            if (v != 0) sb.append(r).append('=').append(v).append(' ');
        }
        RpgMod.LOGGER.info("[AUTOTEST] damage post: original={} inflicted={} health={} reductions={}", e.getOriginalDamage(),
                e.getInflictedDamage(), e.getHealthDamage(), sb);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onIncomingLast(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) {
        if (!ENABLED || dummy == null || e.getEntity() != dummy) return;
        RpgMod.LOGGER.info("[AUTOTEST] incoming (lowest): amount={} original={}", e.getAmount(), e.getOriginalAmount());
    }
    private static net.minecraft.world.entity.monster.zombie.Zombie dummy;

    /** Hits a dummy zombie with and without forced RPG crits and vanilla jump crits, logging the damage. */
    private static void critTest() {
        critT++;
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        if (critT == 1) {
            dummy = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (dummy == null) { critT = -1; return; }
            dummy.snapTo(player.getX() + 1.5, player.getY(), player.getZ());
            dummy.setNoAi(true);
            dummy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            dummy.getAttribute(Attributes.ARMOR).setBaseValue(0);
            dummy.setHealth(1000);
            level.addFreshEntity(dummy);
            player.getInventory().setItem(7, new ItemStack(Items.IRON_SWORD));
            player.getInventory().setSelectedSlot(7);
        }
        int[] at = {30, 60, 90, 120};
        for (int k = 0; k < at.length; k++) {
            if (critT == at[k]) {
                boolean rpg = k % 2 == 1, jump = k >= 2;
                com.jo112ss.rpgmod.job.JobManager.testForceCrit = rpg;
                if (jump) { player.fallDistance = 1.0; player.setOnGround(false); }
                float h0 = dummy.getHealth();
                player.attack(dummy);
                float dealt = h0 - dummy.getHealth();
                RpgMod.LOGGER.info("[AUTOTEST] crit test rpg={} jump={} damage={}", rpg, jump, dealt);
                player.setOnGround(true);
                player.fallDistance = 0;
            }
        }
        if (critT == 140) {
            com.jo112ss.rpgmod.job.JobManager.testForceCrit = null;
            dummy.discard();
            critT = -1;
        }
    }

    private static void log(String when) {
        RpgMod.LOGGER.info("[AUTOTEST] {}: attack={} speed={} maxHealth={} armor={} move={} kbRes={}", when,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE), player.getAttributeValue(Attributes.ATTACK_SPEED),
                player.getAttributeValue(Attributes.MAX_HEALTH), player.getAttributeValue(Attributes.ARMOR),
                player.getAttributeValue(Attributes.MOVEMENT_SPEED), player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
    }
}
