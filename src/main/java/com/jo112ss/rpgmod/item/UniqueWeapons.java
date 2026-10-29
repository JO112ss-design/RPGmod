package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.JobManager;
import com.jo112ss.rpgmod.job.JobType;
import com.jo112ss.rpgmod.job.SkillExecutor;
import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.job.Venom;
import com.jo112ss.rpgmod.registry.ModEffects;
import com.jo112ss.rpgmod.registry.ModRegistries;
import com.jo112ss.rpgmod.smithing.ForgeSessionManager;
import com.jo112ss.rpgmod.smithing.ForgedData;
import com.jo112ss.rpgmod.smithing.Masterwork;
import com.jo112ss.rpgmod.smithing.core.OptionRoller;
import com.jo112ss.rpgmod.smithing.core.Quality;
import com.jo112ss.rpgmod.smithing.core.RolledOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The bosses' unique weapons. Each dungeon boss has two or three, one per weapon type - ten in all,
 * one for every weapon type. When a boss falls, everyone rewarded for it has a 25% chance of one
 * (a type their job fights with, when the boss has one). The weapon's strength comes from the boss's
 * level (Lv100 about diamond, Lv180 about netherite, more beyond); its quality is random and rolls
 * options like a forged piece (a masterwork too, one in five). Each has its own effect, with a short
 * cooldown so it stays a flavour, not a build.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class UniqueWeapons {
    private UniqueWeapons() {}

    /** Chance each rewarded player gets one when a boss falls. */
    public static final double DROP_CHANCE = 0.25;

    public enum Def {
        SLIME_GREATSWORD("slime_greatsword", "king_slime", WeaponType.SWORD),
        BOUNCING_HAMMER("bouncing_hammer", "king_slime", WeaponType.HAMMER),
        ROCKBREAKER_MACE("rockbreaker_mace", "golem", WeaponType.MACE),
        STONELORD_SPEAR("stonelord_spear", "golem", WeaponType.SPEAR),
        STONE_FISTS("stone_fists", "golem", WeaponType.KNUCKLE),
        LICH_STAFF("lich_staff", "lich", WeaponType.STAFF),
        DEAD_LUTE("dead_lute", "lich", WeaponType.LUTE),
        QUEEN_FANG("queen_fang", "spider_queen", WeaponType.DAGGER),
        SILK_BOW("silk_bow", "spider_queen", WeaponType.BOW),
        VENOM_GUNBLADE("venom_gunblade", "spider_queen", WeaponType.GUNBLADE);

        public final String id, boss;
        public final WeaponType type;

        Def(String id, String boss, WeaponType type) {
            this.id = id;
            this.boss = boss;
            this.type = type;
        }

        public static Def byId(String id) {
            for (Def d : values()) if (d.id.equals(id)) return d;
            return null;
        }
    }

    // ------------------------------------------------------------------ strength by level

    /** Levels where the weapon matches a material: copper, iron, gold, diamond (Lv100), netherite (Lv180). */
    private static final int[] LV = {1, 40, 70, 100, 180};
    private static final double[] FLAT = {3, 9, 12, 15, 18};
    private static final double[] MULT = {1.0, 1.1, 1.2, 1.33, 1.45};
    public static final int MAX_LEVEL = 300;

    private static double along(double[] v, int level) {
        int lv = Math.max(1, Math.min(MAX_LEVEL, level));
        for (int i = 1; i < LV.length; i++) {
            if (lv <= LV[i]) return v[i - 1] + (v[i] - v[i - 1]) * (lv - LV[i - 1]) / (double) (LV[i] - LV[i - 1]);
        }
        int n = LV.length - 1; // past netherite: the last step's pace goes on
        return v[n] + (v[n] - v[n - 1]) * (lv - LV[n]) / (double) (LV[n] - LV[n - 1]);
    }

    /** Flat attack a unique weapon of this level adds (like a material's: the arrow bonus for bows, staff magic - 9 for staffs). */
    public static double flat(int level) {
        return along(FLAT, level);
    }

    /** Multiplier on the whole attack (like a material's). */
    public static double mult(int level) {
        return along(MULT, level);
    }

    public static UniqueWeapon of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(ModRegistries.UNIQUE_WEAPON.get());
    }

    public static boolean is(ItemStack stack, Def def) {
        UniqueWeapon u = of(stack);
        return u != null && def.id.equals(u.id());
    }

    /** Arrow bonus of a bow: a unique bow's from its level, else its material's. */
    public static double bowBonus(ItemStack bow, RpgBowItem item) {
        UniqueWeapon u = of(bow);
        return u != null ? flat(u.level()) : item.damageBonus();
    }

    public static double bowMult(ItemStack bow, RpgBowItem item) {
        UniqueWeapon u = of(bow);
        return u != null ? mult(u.level()) : item.material().multiplier;
    }

    /** A staff's or lute's own magic attack (9 + the flat part), or -1 when it is not unique. */
    public static double staffMagic(ItemStack staff) {
        UniqueWeapon u = of(staff);
        return u == null ? -1 : 9 + flat(u.level());
    }

    // ------------------------------------------------------------------ making one

    private static final Identifier MATERIAL_ID = com.jo112ss.rpgmod.item.SwordTiers.MATERIAL_ID;

    /** A unique weapon of this level and quality (options, a masterwork, durability as forged). */
    public static ItemStack create(Def def, int level, Quality quality, Random rnd) {
        level = Math.max(1, Math.min(MAX_LEVEL, level));
        Item base = ModItems.get(def.type, WeaponMaterial.DIAMOND);
        ItemStack stack = new ItemStack(base);
        List<RolledOption> options = OptionRoller.roll(quality.optionCount, def.type.optionPool(), def.type.magical(), !UnbreakableGear.covers(def.type), rnd);
        ForgedData data = ForgedData.of(quality, options, "", Masterwork.maybe(def.type, rnd));
        stack.set(ModRegistries.FORGED_DATA.get(), data);
        ForgeSessionManager.applyDurability(stack, data);
        stack.set(ModRegistries.UNIQUE_WEAPON.get(), new UniqueWeapon(def.id, level));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.rpgmod.unique." + def.id));
        stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(RpgMod.MODID, "unique/" + def.id));
        if (def.type != WeaponType.BOW) {
            // the attack the material would give, from the level instead
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                    .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, def.type.attackDamage + flat(level), AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND)
                    .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, def.type.attackSpeed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                    .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(MATERIAL_ID, mult(level) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND)
                    .build());
        }
        return stack;
    }

    /** Random quality: common 40%, fine 30%, rare 20%, legendary 10%. */
    public static Quality randomQuality(Random rnd) {
        double r = rnd.nextDouble();
        return r < 0.4 ? Quality.COMMON : r < 0.7 ? Quality.FINE : r < 0.9 ? Quality.RARE : Quality.LEGENDARY;
    }

    /** The boss's weapons; the ones this player's job fights with first (all of them if none fits). */
    public static List<Def> choicesFor(String boss, Player p) {
        List<Def> all = new ArrayList<>(), fit = new ArrayList<>();
        JobType job = JobType.byId(JobManager.data(p).jobId());
        for (Def d : Def.values()) {
            if (!d.boss.equals(boss)) continue;
            all.add(d);
            if (job != null && job.weapons.contains(d.type)) fit.add(d);
        }
        return fit.isEmpty() ? all : fit;
    }

    /** A boss is down: this rewarded player's 25% for one of its weapons. */
    public static void reward(ServerPlayer p, LivingEntity boss, String key) {
        Random rnd = new Random(p.getRandom().nextLong());
        if (rnd.nextDouble() >= DROP_CHANCE) return;
        List<Def> choices = choicesFor(key, p);
        if (choices.isEmpty()) return;
        Def def = choices.get(rnd.nextInt(choices.size()));
        ItemStack stack = create(def, com.jo112ss.rpgmod.mob.MobLevels.level(boss), randomQuality(rnd), rnd);
        p.sendSystemMessage(Component.translatable("rpgmod.unique.drop", stack.getHoverName()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.7F);
        if (!p.getInventory().add(stack)) p.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    // ------------------------------------------------------------------ effects

    /** Each weapon's effect has a cooldown of its own (ticks), per player. */
    private static final Map<String, Long> READY = new HashMap<>();
    /** Hit counters (mace, fists) and the fang's stacks on each target. */
    private static final Map<String, Integer> COUNT = new HashMap<>();
    private static final Map<String, long[]> FANG = new HashMap<>();

    public static final int SLIME_GAP = 30, HAMMER_GAP = 80, SPEAR_GAP = 400, FISTS_GAP = 120, STAFF_GAP = 80, LUTE_GAP = 40,
            FANG_GAP = 120, BOW_GAP = 100, VENOM_GAP = 20;
    public static final double SLIME_SPLASH = 0.3, HAMMER_LANDING = 0.4, MACE_CRACK = 0.6, SPEAR_SKIN = 0.12, STAFF_MANA = 0.04, LUTE_SOUL = 1.5;
    public static final int MACE_EVERY = 4, FISTS_EVERY = 10, FANG_STACKS = 3;

    /** True (and the cooldown starts) when this player's effect of this kind is ready. */
    private static boolean ready(Player p, Def def, int gap) {
        String k = p.getUUID() + "/" + def.id;
        long now = p.level().getGameTime();
        if (READY.getOrDefault(k, 0L) > now) return false;
        READY.put(k, now + gap);
        return true;
    }

    private static DamageSource extra(ServerPlayer p) {
        return p.damageSources().source(SkillExecutor.SKILL_DAMAGE, p, p);
    }

    private static void particles(ServerLevel level, net.minecraft.core.particles.ParticleOptions type, Vec3 at, int n, double spread) {
        level.sendParticles(type, at.x, at.y, at.z, n, spread, spread * 0.6, spread, 0.05);
    }

    /** The weapon behind a plain hit (a swing in the main hand, or a bow's arrow), or empty. */
    private static ItemStack plainWeapon(DamageSource src, ServerPlayer sp) {
        if (src.getDirectEntity() == sp && src.is(DamageTypes.PLAYER_ATTACK)) return sp.getMainHandItem();
        if (src.getDirectEntity() instanceof AbstractArrow arrow && arrow.getWeaponItem() != null) return arrow.getWeaponItem();
        return ItemStack.EMPTY;
    }

    @SubscribeEvent
    public static void onHit(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || victim instanceof Player) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
        ItemStack weapon = plainWeapon(event.getSource(), sp);
        UniqueWeapon u = of(weapon);
        if (u == null || u.def() == null) return;
        float amount = event.getInflictedDamage();
        Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        switch (u.def()) {
            case SLIME_GREATSWORD -> { // a splash of slime round the one hit
                if (!ready(sp, u.def(), SLIME_GAP)) return;
                for (LivingEntity e : SkillExecutor.enemies(sp, victim.position(), 2.5)) {
                    if (e == victim) continue;
                    e.hurtServer(level, extra(sp), amount * (float) SLIME_SPLASH);
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), sp);
                }
                particles(level, ParticleTypes.ITEM_SLIME, at, 24, 1.0);
                level.playSound(null, victim.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            case BOUNCING_HAMMER -> { // up it goes, and it comes down on the ones around
                if (!ready(sp, u.def(), HAMMER_GAP)) return;
                double resist = 1 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.9 * Math.max(0.3, resist), 0));
                victim.needsSync = true;
                level.playSound(null, victim.blockPosition(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.7F);
                float land = amount * (float) HAMMER_LANDING;
                SkillTasks.later(16, () -> {
                    if (!victim.isAlive()) return;
                    for (LivingEntity e : SkillExecutor.enemies(sp, victim.position(), 3)) {
                        if (e != victim) e.hurtServer(level, extra(sp), land);
                    }
                    particles(level, ParticleTypes.ITEM_SLIME, victim.position(), 30, 1.5);
                    level.playSound(null, victim.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.5F);
                });
            }
            case ROCKBREAKER_MACE -> { // every fourth hit cracks the ground
                if (!count(sp, u.def(), MACE_EVERY)) return;
                for (LivingEntity e : SkillExecutor.enemies(sp, victim.position(), 3.5)) {
                    e.hurtServer(level, extra(sp), amount * (float) MACE_CRACK);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.3, 0));
                }
                particles(level, ParticleTypes.EXPLOSION, victim.position(), 3, 1.2);
                level.playSound(null, victim.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 0.7F);
            }
            case STONE_FISTS -> { // every tenth hit stuns
                if (!count(sp, u.def(), FISTS_EVERY) || !ready(sp, u.def(), FISTS_GAP)) return;
                victim.addEffect(new MobEffectInstance(ModEffects.SILENCED, 30, 0), sp);
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 4), sp);
                particles(level, ParticleTypes.CRIT, at, 16, 0.5);
                level.playSound(null, victim.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 1.4F);
            }
            case QUEEN_FANG -> { // poison; the third bite in a row binds in silk
                Venom.apply(victim, 60, 0, sp);
                String k = sp.getUUID() + "/" + victim.getUUID();
                long now = level.getGameTime();
                long[] s = FANG.computeIfAbsent(k, x -> new long[2]);
                s[0] = now - s[1] > 80 ? 1 : s[0] + 1;
                s[1] = now;
                if (s[0] >= FANG_STACKS && ready(sp, u.def(), FANG_GAP)) {
                    s[0] = 0;
                    web(level, victim, sp);
                }
            }
            case SILK_BOW -> {
                if (ready(sp, u.def(), BOW_GAP)) web(level, victim, sp);
            }
            case VENOM_GUNBLADE -> venomShot(sp, victim);
            default -> { }
        }
    }

    /** The venom gunblade's bayonet and its shots: poison (a second apart on one enemy). */
    public static void venomShot(ServerPlayer sp, LivingEntity victim) {
        if (!is(sp.getMainHandItem(), Def.VENOM_GUNBLADE) || victim instanceof Player) return;
        String k = sp.getUUID() + "/venom/" + victim.getUUID();
        long now = sp.level().getGameTime();
        if (READY.getOrDefault(k, 0L) > now) return;
        READY.put(k, now + VENOM_GAP);
        Venom.apply(victim, 60, 0, sp);
    }

    /** Bound in silk: rooted for 1.5s. */
    private static void web(ServerLevel level, LivingEntity victim, ServerPlayer by) {
        victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 6), by);
        victim.setDeltaMovement(Vec3.ZERO);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState()),
                victim.getX(), victim.getY() + 0.5, victim.getZ(), 30, 0.5, 0.5, 0.5, 0.05);
        level.playSound(null, victim.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.3F);
    }

    /** Counts this player's hits with the weapon; true on every {@code every}-th. */
    private static boolean count(Player p, Def def, int every) {
        String k = p.getUUID() + "/" + def.id;
        int n = COUNT.getOrDefault(k, 0) + 1;
        if (n >= every) {
            COUNT.put(k, 0);
            return true;
        }
        COUNT.put(k, n);
        return false;
    }

    /** The stonelord spear: struck by a monster, a skin of stone (absorption) - once in 20s. */
    @SubscribeEvent
    public static void onStruck(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !(event.getSource().getEntity() instanceof LivingEntity att) || att instanceof Player) return;
        if (!is(sp.getMainHandItem(), Def.STONELORD_SPEAR) || !ready(sp, Def.STONELORD_SPEAR, SPEAR_GAP)) return;
        int amp = Math.max(0, (int) Math.round(sp.getMaxHealth() * SPEAR_SKIN / 4.0) - 1);
        sp.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, amp, true, true), sp);
        particles((ServerLevel) sp.level(), ParticleTypes.CRIT, sp.position().add(0, 1, 0), 12, 0.6);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    /** The lich's staff and lute: a kill gives back mana / sends its soul at the next enemy. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || dead instanceof Player || !(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
        ItemStack held = sp.getMainHandItem();
        if (is(held, Def.LICH_STAFF) && ready(sp, Def.LICH_STAFF, STAFF_GAP)) {
            JobManager.restoreMana(sp, JobManager.maxMana(sp) * STAFF_MANA);
            if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) RpgMod.LOGGER.info("[AUTOTEST] lich staff kill: +{} MP", String.format("%.1f", JobManager.maxMana(sp) * STAFF_MANA));
            particles(level, ParticleTypes.SOUL, dead.position().add(0, 0.8, 0), 10, 0.4);
            level.playSound(null, sp.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
        } else if (is(held, Def.DEAD_LUTE) && ready(sp, Def.DEAD_LUTE, LUTE_GAP)) {
            LivingEntity next = null;
            double best = 100;
            for (LivingEntity e : SkillExecutor.enemies(sp, dead.position(), 10)) {
                if (e == dead || !e.isAlive()) continue;
                double d = e.distanceToSqr(dead);
                if (d < best) {
                    best = d;
                    next = e;
                }
            }
            if (next == null) return;
            LivingEntity target = next;
            float dmg = (float) (JobManager.magicAttack(sp) * LUTE_SOUL);
            Vec3 from = dead.position().add(0, 0.8, 0), to = target.position().add(0, target.getBbHeight() * 0.5, 0);
            for (int i = 0; i <= 8; i++) {
                Vec3 pt = from.lerp(to, i / 8.0);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pt.x, pt.y, pt.z, 2, 0.05, 0.05, 0.05, 0.01);
            }
            SkillTasks.later(6, () -> {
                if (target.isAlive()) target.hurtServer(level, sp.damageSources().source(SkillExecutor.SKILL_MAGIC_DAMAGE, sp, sp), dmg);
            });
            level.playSound(null, dead.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
        }
    }

    /** Forgets a player's counters (logout, tests). */
    public static void forget(UUID id) {
        String p = id + "/";
        READY.keySet().removeIf(k -> k.startsWith(p));
        COUNT.keySet().removeIf(k -> k.startsWith(p));
        FANG.keySet().removeIf(k -> k.startsWith(p));
    }
}
