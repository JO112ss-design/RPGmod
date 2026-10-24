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
        // monsters are fast and strong now: keep the test player alive outside the monster scenes
        sp.getAbilities().invulnerable = true;
        sp.onUpdateAbilities();
    }

    private static volatile boolean jobSetup;
    private static volatile boolean forgeRequested;

    /** Client test: level the player up, unlock some panel cells, hand over a staff with spells. */
    /** Logs every weapon's attack (material steps) once. */
    private static void logWeaponAttacks() {
        StringBuilder sb = new StringBuilder();
        for (var type : new com.jo112ss.rpgmod.item.WeaponType[] {com.jo112ss.rpgmod.item.WeaponType.SWORD, com.jo112ss.rpgmod.item.WeaponType.DAGGER,
                com.jo112ss.rpgmod.item.WeaponType.MACE, com.jo112ss.rpgmod.item.WeaponType.HAMMER}) {
            sb.append(type.id).append(':');
            for (var mat : com.jo112ss.rpgmod.item.WeaponMaterial.values()) {
                ItemStack st = new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(type, mat));
                double[] dmg = {0};
                st.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attr, mod) -> {
                    if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) dmg[0] += mod.amount();
                });
                sb.append(' ').append(mat.id).append('=').append(dmg[0] + 1);
            }
            sb.append(" | ");
        }
        RpgMod.LOGGER.info("[AUTOTEST] weapon attack (incl. base 1): {}", sb);
    }

    public static void requestJobSetup() {
        logWeaponAttacks();
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
            if (targets.size() >= 2) { // bow arrows carry the shooter's own attack: plain, then with strength II
                bowTest(level, player, targets.get(0), 0);
                bowTest(level, player, targets.get(1), 2);
            }
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
            MobScenes.end(level, player);
            if (id.startsWith("mob:")) {
                mobScene(level, id.substring(4));
                return;
            }
            com.jo112ss.rpgmod.job.core.SkillDef def = com.jo112ss.rpgmod.job.core.JobDefs.skill(id);
            if (def != null) {
                var job = com.jo112ss.rpgmod.job.JobType.byId(com.jo112ss.rpgmod.job.core.JobDefs.BASE_OF.getOrDefault(def.job(), def.job()));
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
        sceneStep(level);
        MobScenes.tick(level, player);
        if (arena != null && player.tickCount % 10 == 0 && !MobScenes.active()) {
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.monster.cubemob.Slime.class, player.getBoundingBox().inflate(48))) m.discard();
        }
    }

    // ------------------------------------------------------------------ enemy scenes

    private static int sceneTick = -1;
    private static String scene;

    /** Enemy test scenes: levelled dummies with health bars, damage numbers of every kind. */
    private static void mobScene(net.minecraft.server.level.ServerLevel level, String name) {
        scene = name;
        sceneTick = 0;
        switch (name) {
            case "levels" -> {
                int[] lv = {3, 18, 33, 52, 78};
                float[] hp = {1f, 0.7f, 0.4f, 0.85f, 0.15f};
                for (int i = 0; i < targets.size(); i++) {
                    var z = targets.get(i);
                    com.jo112ss.rpgmod.mob.MobLevels.apply(z, lv[i % lv.length]);
                    com.jo112ss.rpgmod.mob.MobGear.equip(z, i == 4 ? 95 : lv[i % lv.length]);
                    z.tick();
                    RpgMod.LOGGER.info("[AUTOTEST] gear Lv{} armor={} defence={} reduction={} attack={}", lv[i % lv.length], z.getArmorValue(),
                            com.jo112ss.rpgmod.mob.Defense.defence(z), String.format("%.2f", com.jo112ss.rpgmod.mob.Defense.reduction(z)),
                            String.format("%.1f", z.getAttributeValue(Attributes.ATTACK_DAMAGE)));
                    RpgMod.LOGGER.info("[AUTOTEST] gear Lv{}: {}", lv[i % lv.length], java.util.Arrays.stream(net.minecraft.world.entity.EquipmentSlot.values())
                            .map(sl -> z.getItemBySlot(sl)).filter(st -> !st.isEmpty()).map(st -> st.getItem().toString()).toList());
                    z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
                    z.setHealth(z.getMaxHealth() * hp[i % hp.length]);
                }
                var r = com.jo112ss.rpgmod.mob.MobConfig.rules();
                for (int l : new int[] {1, 10, 20, 30, 40, 50, 60, 70, 80, 85}) {
                    RpgMod.LOGGER.info("[AUTOTEST] level {}: hp x{} dmg x{} explosion x{} speed +{}", l,
                            String.format("%.2f", com.jo112ss.rpgmod.mob.core.MobLevelMath.healthMultiplier(r, l)),
                            String.format("%.2f", com.jo112ss.rpgmod.mob.core.MobLevelMath.damageMultiplier(r, l)),
                            String.format("%.2f", com.jo112ss.rpgmod.mob.core.MobLevelMath.explosionMultiplier(r, l)),
                            String.format("%.2f", com.jo112ss.rpgmod.mob.core.MobLevelMath.speedBonus(r, l)));
                }
                for (var type : java.util.List.of(net.minecraft.world.entity.EntityTypes.CREEPER, net.minecraft.world.entity.EntityTypes.SPIDER)) {
                    var m = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                    for (int k = 0; k < 4 && m.getArmorValue() == 0; k++) {
                        com.jo112ss.rpgmod.mob.MobGear.equip(m, 60);
                        m.tick(); // equipment attributes are applied on tick
                    }
                    RpgMod.LOGGER.info("[AUTOTEST] hidden gear {}: armor={} attack={} items={}", type.getDescriptionId(), m.getArmorValue(),
                            m.getAttribute(Attributes.ATTACK_DAMAGE) == null ? "-" : String.format("%.1f", m.getAttributeValue(Attributes.ATTACK_DAMAGE)),
                            java.util.Arrays.stream(net.minecraft.world.entity.EquipmentSlot.values()).map(m::getItemBySlot).filter(st -> !st.isEmpty()).map(st -> st.getItem().toString()).toList());
                    m.discard();
                }
                int[] rolls = new int[8];
                for (int i = 0; i < rolls.length; i++) rolls[i] = com.jo112ss.rpgmod.mob.MobLevels.rollLevel(level, player.blockPosition(), i / 8.0);
                RpgMod.LOGGER.info("[AUTOTEST] top player level {} -> new monsters {}", com.jo112ss.rpgmod.mob.MobLevels.topPlayerLevel(level.getServer()),
                        java.util.Arrays.toString(rolls));
                for (int d : new int[] {0, 500, 1500, 3000, 5000}) {
                    RpgMod.LOGGER.info("[AUTOTEST] overworld level at {} blocks: {}", d, com.jo112ss.rpgmod.mob.core.MobLevelMath.baseLevel(r,
                            com.jo112ss.rpgmod.mob.core.MobLevelMath.Realm.OVERWORLD, d, 64));
                }
            }
            default -> MobScenes.start(level, player, arena, name);
        }
    }

    /** Scene bookkeeping: health the test player loses during monster scenes. */
    @SubscribeEvent
    public static void onPlayerHurt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (!ENABLED || event.getEntity() != player) return;
        MobScenes.onPlayerHurt(event.getInflictedDamage(), event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow);
    }

    /** Runs every tick while a scene is active: hits for the damage numbers. */
    private static void sceneStep(net.minecraft.server.level.ServerLevel level) {
        if (scene == null) return;
        int t = sceneTick++;
        if (scene.equals("levels") && targets.size() >= 4) {
            var src = level.damageSources().playerAttack(player);
            switch (t) {
                case 2 -> targets.get(0).hurtServer(level, src, 6.5F);
                case 4 -> {
                    com.jo112ss.rpgmod.mob.DamageNumbers.markCrit(targets.get(3));
                    targets.get(3).hurtServer(level, src, 23F);
                }
                case 6 -> targets.get(1).hurtServer(level, level.damageSources().source(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DAMAGE_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath(RpgMod.MODID, "skill_magic")), player, player), 14F);
                case 8 -> targets.get(2).hurtServer(level, level.damageSources().onFire(), 2F);
                case 10 -> targets.get(2).heal(5F);
                case 12 -> {
                    targets.get(0).hurtServer(level, src, 3.2F);
                    scene = null;
                }
                default -> { }
            }
        }
    }

    private static void bowTest(net.minecraft.server.level.ServerLevel level, ServerPlayer player, net.minecraft.world.entity.LivingEntity target, int strength) {
        if (strength > 0) player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH, 60, strength - 1));
        ItemStack bow = new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(com.jo112ss.rpgmod.item.WeaponType.BOW, com.jo112ss.rpgmod.item.WeaponMaterial.IRON));
        var arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(level, player, new ItemStack(net.minecraft.world.item.Items.ARROW), bow);
        arrow.setPos(player.getEyePosition());
        var to = target.position().add(0, target.getBbHeight() * 0.6, 0).subtract(player.getEyePosition());
        arrow.shoot(to.x, to.y, to.z, 3.0F, 0);
        level.addFreshEntity(arrow);
        RpgMod.LOGGER.info("[AUTOTEST] bow test strength={} own attack={} stored={}", strength,
                String.format("%.1f", com.jo112ss.rpgmod.effects.ForgeOptionEffects.ownAttack(player, bow)),
                String.format("%.1f", arrow.getPersistentData().getDoubleOr("rpgmod_own_attack", 0)));
        if (strength > 0) player.removeEffect(net.minecraft.world.effect.MobEffects.STRENGTH);
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
        var fortune = player.getEffect(com.jo112ss.rpgmod.registry.ModEffects.FORTUNE);
        if (fortune != null) {
            RpgMod.LOGGER.info("[AUTOTEST] fortune: number {} for {}s, crit chance {}%, crit x{}, reroll {}", fortune.getAmplifier() + 1,
                    fortune.getDuration() / 20, com.jo112ss.rpgmod.job.JobManager.critChance(player, null),
                    String.format("%.2f", com.jo112ss.rpgmod.job.JobManager.critMultiplier(player, null)), com.jo112ss.rpgmod.job.JobManager.luckyReroll(player));
        }
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
        if (advancedSetupPending && !jobSetup) {
            advancedSetupPending = false;
            advancedSetup();
        }
        if (swapCheckIn > 0 && --swapCheckIn == 0) {
            swapCheck();
            potionCheck();
        }
        if (jobSetup) {
            jobSetup = false;
            swapCheckIn = 40;
            com.jo112ss.rpgmod.job.JobManager.grantExp(player, 3000);
            advancedSetupPending = true;
            // some armour on, for the status screen check (client values must match the server's)
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
            com.jo112ss.rpgmod.job.JobData jd = com.jo112ss.rpgmod.job.JobManager.data(player);
            // unlock a path towards the outer ring so the panel shows every state
            for (int id : new int[] {1, 2, 7, 8, 9, 19, 20}) com.jo112ss.rpgmod.job.JobManager.unlockNode(player, id, false);
            // and walk up to the two nearest skill cells, so they show as "learnable now"
            walkToSkills(jd, false, 2, false);
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
            player.getInventory().add(new ItemStack(Items.SHIELD, 1));
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

    private static int swapCheckIn;
    private static boolean advancedSetupPending;

    /**
     * Unlocks the stat cells on the way to the {@code count} nearest skill cells of a panel (and the
     * skill cells too when {@code take}).
     */
    private static java.util.List<String> walkToSkills(com.jo112ss.rpgmod.job.JobData jd, boolean adv, int count, boolean take) {
        var panel = jd.panel(adv);
        java.util.List<String> got = new java.util.ArrayList<>();
        java.util.Set<Integer> reached = new java.util.HashSet<>();
        for (int goalCount = 0; goalCount < count; goalCount++) {
            java.util.Map<Integer, Integer> prev = new java.util.HashMap<>();
            java.util.ArrayDeque<com.jo112ss.rpgmod.job.core.SkillPanel.Node> queue = new java.util.ArrayDeque<>();
            for (int id : jd.unlocked(adv)) {
                prev.put(id, -1);
                queue.add(panel.node(id));
            }
            com.jo112ss.rpgmod.job.core.SkillPanel.Node goal = null;
            while (!queue.isEmpty()) {
                var n = queue.poll();
                if (!jd.unlocked(adv).contains(n.id()) && n.type() != com.jo112ss.rpgmod.job.core.SkillPanel.NodeType.STAT && !reached.contains(n.id())) {
                    goal = n;
                    break;
                }
                for (var o : panel.neighbours(n)) {
                    if (!prev.containsKey(o.id()) && (o.type() == com.jo112ss.rpgmod.job.core.SkillPanel.NodeType.STAT || !jd.unlocked(adv).contains(o.id()))) {
                        prev.put(o.id(), n.id());
                        queue.add(o);
                    }
                }
            }
            if (goal == null) break;
            reached.add(goal.id());
            java.util.List<Integer> path = new java.util.ArrayList<>();
            for (int id = prev.get(goal.id()); id >= 0 && !jd.unlocked(adv).contains(id); id = prev.get(id)) path.add(0, id);
            if (take) path.add(goal.id());
            for (int id : path) com.jo112ss.rpgmod.job.JobManager.unlockNode(player, id, adv);
            if (take && jd.unlocked(adv).contains(goal.id())) got.add(goal.skill());
        }
        return got;
    }

    /** Advanced job: promoted at 100, advanced points, a few advanced cells, the stats they add. */
    private static void advancedSetup() {
        var d = com.jo112ss.rpgmod.job.JobManager.data(player);
        var before = com.jo112ss.rpgmod.job.JobStats.compute(d);
        d.setLevel(120);
        boolean promoted = d.ensureAdvanced();
        if (promoted) com.jo112ss.rpgmod.job.JobManager.promote(player, d);
        int free = d.freePoints(true);
        for (int id = 1; id <= 4; id++) com.jo112ss.rpgmod.job.JobManager.unlockNode(player, id, true);
        // learn the two nearest advanced skills and put an active one in the third slot
        java.util.List<String> learnt = walkToSkills(d, true, 2, true);
        for (String s : learnt) {
            var sd = com.jo112ss.rpgmod.job.core.JobDefs.skill(s);
            if (sd != null && sd.kind() == com.jo112ss.rpgmod.job.core.SkillKind.ACTIVE) com.jo112ss.rpgmod.job.JobManager.equipSkill(player, 3, s);
            if (sd != null && sd.kind() == com.jo112ss.rpgmod.job.core.SkillKind.SPELL) com.jo112ss.rpgmod.job.JobManager.setSpell(player, 3, s);
        }
        RpgMod.LOGGER.info("[AUTOTEST] advanced skills learnt {} (known now {}), slot 3 = {}", learnt, d.knownSkills(), d.slot(3));
        com.jo112ss.rpgmod.job.SkillTasks.later(320, AutoTestServer::advCheck);
        var after = com.jo112ss.rpgmod.job.JobStats.compute(d);
        RpgMod.LOGGER.info("[AUTOTEST] advanced: promoted={} job={} shown {} Lv{} (total {}) adv points {} -> {} base points {}; magic attack {} -> {}, mana {} -> {}; monsters around Lv{}",
                promoted, d.jobId(), d.displayJobId(), d.displayLevel(), d.level(), free, d.freePoints(true), d.freePoints(),
                String.format("%.1f", before.get(com.jo112ss.rpgmod.job.core.JobStat.MAGIC_ATTACK)), String.format("%.1f", after.get(com.jo112ss.rpgmod.job.core.JobStat.MAGIC_ATTACK)),
                String.format("%.0f", before.get(com.jo112ss.rpgmod.job.core.JobStat.MAX_MANA)), String.format("%.0f", after.get(com.jo112ss.rpgmod.job.core.JobStat.MAX_MANA)),
                com.jo112ss.rpgmod.mob.MobLevels.topPlayerLevel(player.level().getServer()));
    }

    /** Skill swap cooldown, level-scaled effect times and whole MP costs. */
    private static void swapCheck() {
        var d = com.jo112ss.rpgmod.job.JobManager.data(player);
        var defs = com.jo112ss.rpgmod.job.core.JobDefs.SKILLS;
        RpgMod.LOGGER.info("[AUTOTEST] level {}: time x{} (active mana_barrier), x{} (spell arcane_shield), x{} (ultimate meteor), x{} (enchanter empower); MP power_slash={} magic_bolt={} russian_roulette={}",
                d.level(), String.format("%.3f", com.jo112ss.rpgmod.job.JobManager.levelDurationScale(player, defs.get("mana_barrier"))),
                String.format("%.3f", com.jo112ss.rpgmod.job.JobManager.levelDurationScale(player, defs.get("arcane_shield"))),
                String.format("%.3f", com.jo112ss.rpgmod.job.JobManager.levelDurationScale(player, defs.get("meteor"))),
                String.format("%.3f", com.jo112ss.rpgmod.job.JobManager.levelDurationScale(player, defs.get("empower"))),
                defs.get("power_slash").mana(), defs.get("magic_bolt").mana(), defs.get("russian_roulette").mana());
        long now = player.level().getGameTime();
        var known = d.knownSkills(com.jo112ss.rpgmod.job.core.SkillKind.ACTIVE);
        RpgMod.LOGGER.info("[AUTOTEST] swap test: known actives {} slots {} / {}", known, d.slot(0), d.slot(1));
        if (known.isEmpty()) return;
        String id = known.get(0);
        // take it out of every slot, then put it back in: a fresh skill in a slot
        for (int i = 0; i < 2; i++) if (d.slot(i).equals(id)) com.jo112ss.rpgmod.job.JobManager.equipSkill(player, i, "");
        long before = d.cooldownEnd(id) - now;
        com.jo112ss.rpgmod.job.JobManager.equipSkill(player, 0, id);
        long after = d.cooldownEnd(id) - now;
        // moving it to the other slot is not a new skill: no extra wait
        d.setCooldown(id, now);
        com.jo112ss.rpgmod.job.JobManager.equipSkill(player, 1, id);
        RpgMod.LOGGER.info("[AUTOTEST] swap {} into slot 0: cooldown {} -> {} ticks; moved to slot 1: {} ticks", id, Math.max(0, before), after,
                Math.max(0, d.cooldownEnd(id) - now));
    }

    /** Recovery potions: what one gives back, and how often 300 kills drop them. */
    private static void potionCheck() {
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        player.setHealth(player.getMaxHealth() * 0.2F);
        var d = com.jo112ss.rpgmod.job.JobManager.data(player);
        d.setMana(0);
        float hpBefore = player.getHealth();
        com.jo112ss.rpgmod.registry.ModRegistries.HP_POTION.get().finishUsingItem(new ItemStack(com.jo112ss.rpgmod.registry.ModRegistries.HP_POTION.get()), level, player);
        com.jo112ss.rpgmod.registry.ModRegistries.MP_POTION.get().finishUsingItem(new ItemStack(com.jo112ss.rpgmod.registry.ModRegistries.MP_POTION.get()), level, player);
        RpgMod.LOGGER.info("[AUTOTEST] potions: hp {} -> {} of {}, mp 0 -> {} of {}", hpBefore, player.getHealth(), player.getMaxHealth(),
                String.format("%.1f", d.mana()), String.format("%.1f", com.jo112ss.rpgmod.job.JobManager.maxMana(player)));
        int hp = 0, mp = 0;
        var at = player.position().add(0, 0, 40);
        for (int i = 0; i < 300; i++) {
            var z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            z.snapTo(at.x, at.y, at.z, 0, 0);
            level.addFreshEntity(z);
            z.hurtServer(level, level.damageSources().playerAttack(player), 100000F);
        }
        for (var it : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(6))) {
            if (it.getItem().is(com.jo112ss.rpgmod.registry.ModRegistries.HP_POTION.get())) hp += it.getItem().getCount();
            if (it.getItem().is(com.jo112ss.rpgmod.registry.ModRegistries.MP_POTION.get())) mp += it.getItem().getCount();
            it.discard();
        }
        RpgMod.LOGGER.info("[AUTOTEST] potion drops from 300 kills: hp={} mp={} (expected about 9 each); meteor ratio {}", hp, mp,
                com.jo112ss.rpgmod.job.core.JobDefs.skill("meteor").power());
        player.setHealth(player.getMaxHealth());
        shieldCheck(com.jo112ss.rpgmod.item.ModItems.get(com.jo112ss.rpgmod.item.WeaponType.SHIELD, com.jo112ss.rpgmod.item.WeaponMaterial.DIAMOND), 0);
    }

    /** A zombie hit taken with the shield up and down: the shield should take its share off. */
    private static void shieldCheck(net.minecraft.world.item.Item shieldItem, int round) {
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        var mode = player.gameMode();
        var diff = level.getDifficulty();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        boolean wasInvulnerable = player.getAbilities().invulnerable;
        player.getAbilities().invulnerable = false;
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(shieldItem));
        com.jo112ss.rpgmod.job.SkillTasks.later(2, () -> {
            player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
            RpgMod.LOGGER.info("[AUTOTEST] shield raised: using={} item={} difficulty was {}", player.isUsingItem(), player.getUseItem(), diff);
        });
        com.jo112ss.rpgmod.job.SkillTasks.later(14, () -> {
            var z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            var look = player.getLookAngle().multiply(1, 0, 1).normalize();
            z.snapTo(player.getX() + look.x * 2, player.getY(), player.getZ() + look.z * 2, 0, 0);
            z.setNoAi(true);
            level.addFreshEntity(z);
            boolean guarding = com.jo112ss.rpgmod.item.Shields.guarding(player);
            boolean blocking = player.isBlocking();
            player.setHealth(player.getMaxHealth());
            player.removeAllEffects();
            float before = player.getHealth();
            RpgMod.LOGGER.info("[AUTOTEST] shield hit check: invulnerableTo={} abilities={} mode={} health={}",
                    player.isInvulnerableTo(level, level.damageSources().mobAttack(z)), player.getAbilities().invulnerable, player.gameMode(), player.getHealth());
            player.hurtServer(level, level.damageSources().mobAttack(z), 10F);
            float up = before - player.getHealth();
            player.stopUsingItem();
            com.jo112ss.rpgmod.job.SkillTasks.later(25, () -> {
                player.setHealth(player.getMaxHealth());
                float b2 = player.getHealth();
                player.hurtServer(level, level.damageSources().mobAttack(z), 10F);
                float down = b2 - player.getHealth();
                RpgMod.LOGGER.info("[AUTOTEST] shield {}: guarding={} blocking={} damage with shield up {} / down {} -> {}% let through (expected {}%)",
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(shieldItem), guarding, blocking,
                        String.format("%.2f", up), String.format("%.2f", down), String.format("%.0f", down > 0 ? up / down * 100 : -1),
                        String.format("%.0f", (1 - com.jo112ss.rpgmod.item.Shields.block(player, new ItemStack(shieldItem))) * 100));
                z.discard();
                player.setHealth(player.getMaxHealth());
                player.setGameMode(mode);
                player.getAbilities().invulnerable = wasInvulnerable;
                player.onUpdateAbilities();
                level.getServer().setDifficulty(diff, true);
                if (round == 0) shieldCheck(net.minecraft.world.item.Items.SHIELD, 1);
                else player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            });
        });
    }

    // ------------------------------------------------------------------ advanced job effects

    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> SKILL_KEY =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("rpgmod", "skill"));

    private static net.minecraft.world.entity.monster.zombie.Husk advDummy(net.minecraft.server.level.ServerLevel level, double dx) {
        var z = net.minecraft.world.entity.EntityTypes.HUSK.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        z.snapTo(player.getX() + dx, player.getY(), player.getZ() + 2, 180F, 0F);
        z.setNoAi(true);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        z.getAttribute(Attributes.ARMOR).setBaseValue(0);
        z.setHealth(1000);
        level.addFreshEntity(z);
        return z;
    }

    private static float hit(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.LivingEntity e, net.minecraft.world.damagesource.DamageSource src, float amount) {
        float before = e.getHealth();
        e.hurtServer(level, src, amount);
        return before - e.getHealth();
    }

    private static String f(double v) {
        return String.format("%.2f", v);
    }

    /** The lasting effects of the advanced skills, each measured against the same hit without it. */
    private static void advCheck() {
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        var mode = player.gameMode();
        var diff = level.getDifficulty();
        boolean wasInv = player.getAbilities().invulnerable;
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.getAbilities().invulnerable = false;
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        com.jo112ss.rpgmod.job.JobManager.testForceCrit = false;
        var z = advDummy(level, 0);
        var plain = level.damageSources().source(SKILL_KEY);
        // exposed (+30% per level)
        float a = hit(level, z, plain, 10);
        z.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.EXPOSED, 200, 1));
        float b = hit(level, z, plain, 10);
        z.removeAllEffects();
        RpgMod.LOGGER.info("[AUTOTEST] adv exposed II: {} -> {} (x{}, expected x1.60)", f(a), f(b), f(b / a));
        // asura at half health: x1.75, 15% heals
        var mine = player.damageSources().source(SKILL_KEY, player, player);
        float c0 = hit(level, z, mine, 10);
        player.setHealth(player.getMaxHealth() * 0.5F);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.ASURA, 200, 0));
        float hp0 = player.getHealth();
        float c1 = hit(level, z, mine, 10);
        RpgMod.LOGGER.info("[AUTOTEST] adv asura at 50% health: {} -> {} (x{}, expected x1.75), healed {} (expected {})", f(c0), f(c1), f(c1 / c0),
                f(player.getHealth() - hp0), f(c1 * 0.15));
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        // iron wall / sanctum on the player
        float p0 = hit(level, player, plain, 10);
        player.setHealth(player.getMaxHealth());
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.IRON_WALL, 200, 0));
        float p1 = hit(level, player, plain, 10);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.SANCTUM, 200, 0));
        float p2 = hit(level, player, plain, 10);
        player.removeAllEffects();
        RpgMod.LOGGER.info("[AUTOTEST] adv player hit {}: iron wall {} (x{}, expected x0.50, knockback res now {}), sanctum {} (x{}, expected x0.70)", f(p0), f(p1), f(p1 / p0),
                f(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)), f(p2), f(p2 / p0));
        // undying: a huge hit leaves 1 health
        player.setHealth(10);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.UNDYING, 200, 0));
        player.hurtServer(level, plain, 500);
        RpgMod.LOGGER.info("[AUTOTEST] adv undying: 500 damage at 10 health -> health {} alive={}", f(player.getHealth()), player.isAlive());
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        // vanish: monsters cannot pick you
        boolean seen0 = player.canBeSeenAsEnemy();
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.VANISH, 200, 0));
        boolean seen1 = player.canBeSeenAsEnemy();
        float v1 = hit(level, z, mine, 10);
        RpgMod.LOGGER.info("[AUTOTEST] adv vanish: seen as enemy {} -> {}; first hit {} (expected x2 of {}), vanish left {}", seen0, seen1, f(v1), f(c0),
                player.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.VANISH));
        player.removeAllEffects();
        // yaksha: a sword hit lands twice
        player.getInventory().setItem(7, new ItemStack(Items.IRON_SWORD));
        player.getInventory().setSelectedSlot(7);
        com.jo112ss.rpgmod.job.SkillTasks.later(15, () -> {
            // feather: no fall damage (after the hit cooldown of the last checks ran out)
            float h = player.getHealth();
            player.causeFallDamage(12, 1, level.damageSources().fall());
            float fall0 = h - player.getHealth();
            player.setHealth(player.getMaxHealth());
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.FEATHER, 200, 0));
            player.causeFallDamage(12, 1, level.damageSources().fall());
            RpgMod.LOGGER.info("[AUTOTEST] adv feather: 12-block fall {} -> {}", f(fall0), f(player.getMaxHealth() - player.getHealth()));
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            float y0 = z.getHealth();
            player.attack(z);
            float single = y0 - z.getHealth();
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.YAKSHA, 200, 0));
            com.jo112ss.rpgmod.job.SkillTasks.later(60, () -> { // attack cooldown fully back (off-job weapons swing slower)
                float y1 = z.getHealth();
                player.attack(z);
                com.jo112ss.rpgmod.job.SkillTasks.later(8, () -> {
                    RpgMod.LOGGER.info("[AUTOTEST] adv yaksha: sword hit {} -> with yaksha {} in total (expected about x2)", f(single), f(y1 - z.getHealth()));
                    player.removeAllEffects();
                    advCheck2(level, z, mode, diff, wasInv);
                });
            });
        });
    }

    private static void advCheck2(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.LivingEntity z,
                                  net.minecraft.world.level.GameType mode, net.minecraft.world.Difficulty diff, boolean wasInv) {
        // twin venom on a sword hit
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.TWIN_VENOM, 200, 0));
        player.attack(z);
        RpgMod.LOGGER.info("[AUTOTEST] adv twin venom (on a husk: undead are immune to poison): poison={} wither={}", z.hasEffect(net.minecraft.world.effect.MobEffects.POISON), z.hasEffect(net.minecraft.world.effect.MobEffects.WITHER));
        player.removeAllEffects();
        // empowered shot: the same bow arrow with and without
        var z2 = advDummy(level, 3);
        var z3 = advDummy(level, -3);
        float[] start = new float[2];
        com.jo112ss.rpgmod.job.SkillTasks.later(2, () -> {
            start[0] = z2.getHealth();
            start[1] = z3.getHealth();
            bowTest(level, player, z2, 0);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.EMPOWERED_SHOT, 200, 0));
            bowTest(level, player, z3, 0);
            RpgMod.LOGGER.info("[AUTOTEST] adv empowered shot used up: {}", !player.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.EMPOWERED_SHOT));
        });
        com.jo112ss.rpgmod.job.SkillTasks.later(30, () -> {
            RpgMod.LOGGER.info("[AUTOTEST] adv empowered shot: arrow {} -> empowered {} (expected about x2.5)", f(start[0] - z2.getHealth()), f(start[1] - z3.getHealth()));
            z2.discard();
            z3.discard();
            // loaded dice: roulette lands on 6
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.LOADED_DICE, 600, 0));
            var rd = com.jo112ss.rpgmod.job.core.JobDefs.skill("roulette");
            com.jo112ss.rpgmod.job.SkillExecutor.execute(player, rd, 1);
        });
        com.jo112ss.rpgmod.job.SkillTasks.later(75, () -> {
            var fo = player.getEffect(com.jo112ss.rpgmod.registry.ModEffects.FORTUNE);
            RpgMod.LOGGER.info("[AUTOTEST] adv loaded dice: roulette number {} (expected 6), die used up {}", fo == null ? -1 : fo.getAmplifier() + 1,
                    !player.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.LOADED_DICE));
            player.removeAllEffects();
            // double cast: the staff's spell goes off twice
            ItemStack staff = new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(com.jo112ss.rpgmod.item.WeaponType.STAFF, com.jo112ss.rpgmod.item.WeaponMaterial.IRON));
            player.getInventory().setItem(7, staff);
            player.getInventory().setSelectedSlot(7);
            com.jo112ss.rpgmod.job.JobManager.setSpell(player, 0, "magic_bolt");
            com.jo112ss.rpgmod.job.JobManager.selectSpell(player, 0);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.DOUBLE_CAST, 300, 1));
            var jd = com.jo112ss.rpgmod.job.JobManager.data(player);
            jd.setMana(500);
            jd.setCooldown("magic_bolt", 0);
            boolean cast = com.jo112ss.rpgmod.job.JobManager.releaseCast(player, player.getMainHandItem(), 1000);
            RpgMod.LOGGER.info("[AUTOTEST] adv double cast: cast={} charges left={}", cast,
                    player.getEffect(com.jo112ss.rpgmod.registry.ModEffects.DOUBLE_CAST) == null ? 0 : player.getEffect(com.jo112ss.rpgmod.registry.ModEffects.DOUBLE_CAST).getAmplifier() + 1);
            // overload: magic attack x1.5, spells cost 50% more
            double m0 = com.jo112ss.rpgmod.job.JobManager.magicAttack(player);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.OVERLOAD, 200, 0));
            RpgMod.LOGGER.info("[AUTOTEST] adv overload: magic attack {} -> {}, meteor... thunder cost {} -> {}", f(m0), f(com.jo112ss.rpgmod.job.JobManager.magicAttack(player)),
                    com.jo112ss.rpgmod.job.core.JobDefs.skill("thunder").mana(), com.jo112ss.rpgmod.job.JobManager.manaCost(player, com.jo112ss.rpgmod.job.core.JobDefs.skill("thunder")));
            player.removeAllEffects();
            // keen: +25 crit rate
            com.jo112ss.rpgmod.job.JobManager.testForceCrit = null;
            double k0 = com.jo112ss.rpgmod.job.JobManager.critChance(player, null);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jo112ss.rpgmod.registry.ModEffects.KEEN, 200, 0));
            RpgMod.LOGGER.info("[AUTOTEST] adv keen: crit rate {} -> {}", f(k0), f(com.jo112ss.rpgmod.job.JobManager.critChance(player, null)));
            player.removeAllEffects();
            // revive through the Resurrection mod
            boolean downed = false;
            try {
                Class<?> dm = Class.forName("io.github.jo112ss.resurrection.DownedManager");
                downed = Boolean.TRUE.equals(dm.getMethod("forceDown", ServerPlayer.class).invoke(null, player));
            } catch (ReflectiveOperationException | LinkageError ex) {
                RpgMod.LOGGER.info("[AUTOTEST] adv revive: no Resurrection ({})", ex.toString());
            }
            boolean isDown = com.jo112ss.rpgmod.compat.Downed.is(player);
            boolean revived = com.jo112ss.rpgmod.job.AdvSkills.revive(player);
            RpgMod.LOGGER.info("[AUTOTEST] adv revive: forced down={} downed={} -> revived={} downed now={} health={}", downed, isDown, revived,
                    com.jo112ss.rpgmod.compat.Downed.is(player), f(player.getHealth()));
            z.discard();
            player.setHealth(player.getMaxHealth());
            player.setGameMode(mode);
            player.getAbilities().invulnerable = wasInv;
            player.onUpdateAbilities();
            level.getServer().setDifficulty(diff, true);
            player.getInventory().setSelectedSlot(0);
        });
    }

    private static void log(String when) {
        RpgMod.LOGGER.info("[AUTOTEST] {}: attack={} speed={} maxHealth={} armor={} move={} kbRes={}", when,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE), player.getAttributeValue(Attributes.ATTACK_SPEED),
                player.getAttributeValue(Attributes.MAX_HEALTH), player.getAttributeValue(Attributes.ARMOR),
                player.getAttributeValue(Attributes.MOVEMENT_SPEED), player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
    }
}
