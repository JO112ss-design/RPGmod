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

    // ------------------------------------------------------------------ skill showcase

    private static volatile boolean showcaseSetup;
    private static volatile String castRequest;
    private static final java.util.List<net.minecraft.world.entity.monster.zombie.Zombie> targets = new java.util.ArrayList<>();
    private static net.minecraft.world.phys.Vec3 arena;
    private static String lastCast;
    private static com.jo112ss.rpgmod.job.core.SkillDef pendingCast;
    private static int pendingDelay;
    private static int sinceCast = -1;

    /** Client test: build the arena (dummies in front of the player) for casting every skill. */
    public static void requestShowcase() {
        showcaseSetup = true;
    }

    /** Client test: cast this skill now (bypasses job, weapon, mana and cooldown checks). */
    public static void requestCast(String id) {
        castRequest = id;
    }

    private static final double[][] DUMMIES = {{0, 4}, {-2.2, 4.5}, {2.2, 4.5}, {0, 7}, {1.6, 2.2}};

    private static void showcaseTick() {
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        if (showcaseSetup) {
            showcaseSetup = false;
            var server = level.getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
            arena = player.position().add(0, 0, 6); // away from the forge anvil
            for (double[] d : DUMMIES) {
                // husks do not burn in daylight, so the screenshots are not full of fire
                var z = net.minecraft.world.entity.EntityTypes.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                if (z == null) continue;
                z.setNoAi(true);
                z.setSilent(true);
                z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 100000, 0));
                z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                z.setHealth(1000);
                level.addFreshEntity(z);
                targets.add(z);
            }
            resetArena();
            RpgMod.LOGGER.info("[AUTOTEST] showcase arena at {} with {} dummies", arena, targets.size());
        }
        String id = castRequest;
        if (id != null && arena != null) {
            castRequest = null;
            logLastCast();
            resetArena();
            if (id.equals("!reset")) { // the client test drives this one itself (staff charging)
                player.getInventory().setSelectedSlot(0);
                return;
            }
            com.jo112ss.rpgmod.job.core.SkillDef def = com.jo112ss.rpgmod.job.core.JobDefs.skill(id);
            if (def != null) {
                var job = com.jo112ss.rpgmod.job.JobType.byId(def.job());
                var weapon = def.kind() == com.jo112ss.rpgmod.job.core.SkillKind.SPELL || job == null ? com.jo112ss.rpgmod.item.WeaponType.STAFF : job.weapons.get(0);
                if (id.equals("lucky_shot")) weapon = com.jo112ss.rpgmod.item.WeaponType.BOW;
                player.getInventory().setItem(6, new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(weapon, com.jo112ss.rpgmod.item.WeaponMaterial.IRON)));
                player.getInventory().setSelectedSlot(6);
                // cast two ticks later, once the held weapon's attack is applied to the player
                pendingCast = def;
                pendingDelay = 2;
            }
        }
        if (pendingCast != null && --pendingDelay <= 0) {
            var def = pendingCast;
            pendingCast = null;
            double mult = com.jo112ss.rpgmod.job.JobManager.powerMultiplier(player, def);
            boolean ok = com.jo112ss.rpgmod.job.SkillExecutor.execute(player, def, mult);
            RpgMod.LOGGER.info("[AUTOTEST] cast {} ok={} atk={} matk={} ratio={} -> {}", def.id(), ok,
                    String.format("%.1f", com.jo112ss.rpgmod.job.JobManager.physicalAttack(player)),
                    String.format("%.1f", com.jo112ss.rpgmod.job.JobManager.magicAttack(player)), def.power(),
                    String.format("%.1f", def.power() * mult));
            lastCast = def.id();
            sinceCast = 0;
        }
        if (sinceCast >= 0 && ++sinceCast == 40) logLastCast();
        if (arena != null && player.tickCount % 10 == 0) {
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.monster.cubemob.Slime.class, player.getBoundingBox().inflate(48))) m.discard();
        }
    }

    private static void logLastCast() {
        if (lastCast == null) return;
        int hit = 0;
        float total = 0;
        for (var z : targets) {
            float lost = z.getMaxHealth() - z.getHealth();
            if (lost > 0) hit++;
            total += lost;
        }
        RpgMod.LOGGER.info("[AUTOTEST] skill {} hit {} dummies for {} total damage", lastCast, hit, String.format("%.1f", total));
        lastCast = null;
        sinceCast = -1;
    }

    /** Puts the player and the dummies back in place, full health, no effects. */
    private static void resetArena() {
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        // anything that wandered in (slimes on flat worlds) would get in the way of the pictures
        for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(48), m -> !targets.contains(m))) m.discard();
        for (var it : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(48))) it.discard();
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.teleportTo(level, arena.x, arena.y, arena.z, java.util.Set.of(), 0F, 12F, false);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        for (int i = 0; i < targets.size(); i++) {
            var z = targets.get(i);
            if (!z.isAlive()) continue;
            double[] d = DUMMIES[i];
            // yaw 0 faces +z, so "forward" is +z and the player's right is -x
            z.snapTo(arena.x - d[0], arena.y, arena.z + d[1], 180F, 0F);
            z.setYHeadRot(180F);
            z.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            z.removeAllEffects();
            z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 100000, 0));
            z.clearFire();
            z.setTicksFrozen(0);
            z.setHealth(z.getMaxHealth());
        }
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
        showcaseTick();
    }

    private static int critT = -1;
    private static net.minecraft.world.entity.monster.zombie.Zombie dummy;

    private static void critTest() {
        critT++;
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        if (critT == 1) {
            dummy = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (dummy == null) { critT = -1; return; }
            dummy.snapTo(player.getX() + 1.5, player.getY(), player.getZ());
            dummy.setNoAi(true);
            dummy.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 20000, 0)); // no sunburn ticks during the test
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
