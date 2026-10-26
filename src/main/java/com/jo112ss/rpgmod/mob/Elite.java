package com.jo112ss.rpgmod.mob;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Elite monsters: a share of ordinary monsters appear with one to three traits (by level). Every
 * trait is one you can see and play around - its colour shows on the monster, its name over it -
 * and none of them punishes you out of nowhere (no death blasts, no reflected damage, no
 * teleporting, no invisibility). Elites are tougher (x2.5 health, x1.25 damage), a little bigger,
 * and drop gems and potions more often.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class Elite {
    private Elite() {}

    /** The traits; the bit of each is its ordinal. */
    public enum Trait {
        IRON("iron", 0xB8C0D0), SWIFT("swift", 0x9CFFB0), MIGHTY("mighty", 0xFF5040), REGEN("regen", 0x70FF90), VAMPIRE("vampire", 0xC01828),
        SPLIT("split", 0xD0A0FF), COMMANDER("commander", 0xFFD050), WARDED("warded", 0x9A7BFF), STEADFAST("steadfast", 0xC0A070),
        BLAZE("blaze", 0xFF8A30), FROST("frost", 0x9FE8FF), GUARDIAN("guardian", 0x60C8FF);

        public final String id;
        public final int color;

        Trait(String id, int color) {
            this.id = id;
            this.color = color;
        }

        public int bit() {
            return 1 << ordinal();
        }
    }

    public static final double HEALTH = 2.5, DAMAGE = 1.25, SCALE = 0.15;
    public static final float IRON_CUT = 0.35F, WARD_CUT = 0.5F, MIGHTY_MULT = 1.4F, COMMAND_MULT = 1.2F, VAMPIRE_SHARE = 0.25F;
    public static final double SWIFT_SPEED = 0.3, MIGHTY_SPEED = -0.1, COMMAND_RANGE = 8, GUARD_RANGE = 8, GUARD_SHARE = 0.15;
    /** Ticks a regenerating elite must go unhurt before it heals; its healing a second (share of max). */
    public static final int REGEN_WAIT = 60;
    public static final double REGEN_SHARE = 0.03;
    public static final int GUARD_EVERY = 200, GUARD_WARN = 30;
    /** Tag on the halves a splitting elite leaves (they never split or roll traits themselves). */
    public static final String HALF = "rpgmod_elite_half";

    private static final Identifier HP = id("elite_health"), SPEED = id("elite_speed"), SIZE = id("elite_scale"), KB = id("elite_knockback");

    private static Identifier id(String p) {
        return Identifier.fromNamespaceAndPath(RpgMod.MODID, p);
    }

    public static int traits(Entity e) {
        return e == null ? 0 : e.getData(ModRegistries.MOB_ELITE);
    }

    public static boolean is(Entity e) {
        return traits(e) != 0;
    }

    public static boolean has(Entity e, Trait t) {
        return (traits(e) & t.bit()) != 0;
    }

    public static List<Trait> list(int mask) {
        List<Trait> out = new ArrayList<>();
        for (Trait t : Trait.values()) if ((mask & t.bit()) != 0) out.add(t);
        return out;
    }

    /** Monsters that never become elites: bosses, creepers (a fast or armoured creeper is a nasty surprise), boss minions, halves. */
    static boolean eligible(Mob mob) {
        if (!(mob instanceof Enemy) || mob instanceof Creeper) return false;
        if (com.jo112ss.rpgmod.gem.GemDrops.isBoss(mob) || mob instanceof com.jo112ss.rpgmod.mob.boss.GolemPillar) return false;
        if (mob instanceof net.minecraft.world.entity.monster.Shulker) return false;
        if (mob.getPersistentData().getBooleanOr(com.jo112ss.rpgmod.mob.boss.BossEvents.CHILD, false)) return false;
        return !mob.entityTags().contains(HALF);
    }

    /** Dev test: forces the next roll (null = by chance). */
    public static Integer forceNext;

    /** Rolls a new monster (first time it enters the world): elite or not, and its traits. */
    public static void roll(Mob mob, int lv) {
        if (!eligible(mob)) return;
        Integer forced = forceNext;
        int mask;
        if (forced != null) {
            forceNext = null;
            mask = forced;
        } else {
            if (mob.getRandom().nextDouble() >= MobConfig.eliteChance()) return;
            int n = lv >= 130 ? 3 : lv >= 60 ? 2 : 1;
            List<Trait> pool = new ArrayList<>(List.of(Trait.values()));
            if (mob instanceof net.minecraft.world.entity.monster.Slime) pool.remove(Trait.SPLIT); // they split anyway
            mask = 0;
            for (int i = 0; i < n && !pool.isEmpty(); i++) mask |= pool.remove(mob.getRandom().nextInt(pool.size())).bit();
        }
        if (mask == 0) return;
        mob.setData(ModRegistries.MOB_ELITE, mask);
        apply(mob);
        mob.setHealth(mob.getMaxHealth());
    }

    /** (Re)sets an elite's stat modifiers (also after loading). */
    public static void apply(Mob mob) {
        int mask = traits(mob);
        if (mask == 0) return;
        boolean full = mob.getHealth() >= mob.getMaxHealth() - 0.01F;
        modifier(mob, Attributes.MAX_HEALTH, HP, HEALTH - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        double speed = ((mask & Trait.SWIFT.bit()) != 0 ? SWIFT_SPEED : 0) + ((mask & Trait.MIGHTY.bit()) != 0 ? MIGHTY_SPEED : 0);
        modifier(mob, Attributes.MOVEMENT_SPEED, SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(mob, Attributes.SCALE, SIZE, SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(mob, Attributes.KNOCKBACK_RESISTANCE, KB, (mask & Trait.STEADFAST.bit()) != 0 ? 1 : 0, AttributeModifier.Operation.ADD_VALUE);
        if ((mask & Trait.BLAZE.bit()) != 0) mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        if (full) mob.setHealth(mob.getMaxHealth());
    }

    private static void modifier(LivingEntity e, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount != 0) inst.addPermanentModifier(new AttributeModifier(id, amount, op));
    }

    // ------------------------------------------------------------------ fighting

    /** Hits an elite takes: armour cuts all of them, a ward cuts magic. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingIncomingDamageEvent event) {
        LivingEntity v = event.getEntity();
        int mask = traits(v);
        if (mask == 0 || event.getAmount() <= 0) return;
        float amount = event.getAmount();
        if ((mask & Trait.IRON.bit()) != 0) amount *= 1 - IRON_CUT;
        if ((mask & Trait.WARDED.bit()) != 0 && magic(event.getSource())) amount *= 1 - WARD_CUT;
        event.setAmount(amount);
    }

    static boolean magic(DamageSource s) {
        return s.is(com.jo112ss.rpgmod.job.SkillExecutor.magicKey()) || s.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO);
    }

    /** Hits a monster deals: an elite hits harder (more if mighty), any monster near a commander too. */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onHit(LivingIncomingDamageEvent event) {
        LivingEntity attacker = MobLevels.attacker(event.getSource());
        if (!(attacker instanceof Mob mob) || !(mob instanceof Enemy) || event.getEntity() instanceof Enemy || event.getAmount() <= 0) return;
        float mult = 1;
        int mask = traits(mob);
        if (mask != 0) mult *= DAMAGE;
        if ((mask & Trait.MIGHTY.bit()) != 0) mult *= MIGHTY_MULT;
        if (commanded(mob)) mult *= COMMAND_MULT;
        if (mult != 1) event.setAmount(event.getAmount() * mult);
    }

    /** True if a commander elite is near this monster (the commander counts for itself too). */
    public static boolean commanded(Mob mob) {
        if (has(mob, Trait.COMMANDER)) return true;
        for (Mob m : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(COMMAND_RANGE), m -> m.isAlive() && has(m, Trait.COMMANDER))) {
            if (m.distanceTo(mob) <= COMMAND_RANGE) return true;
        }
        return false;
    }

    /** After an elite's hit landed: drink it back, set alight, chill. */
    @SubscribeEvent
    public static void afterHit(LivingDamageEvent.Post event) {
        LivingEntity attacker = MobLevels.attacker(event.getSource());
        int mask = traits(attacker);
        if (mask == 0 || event.getEntity() == attacker) return;
        LivingEntity v = event.getEntity();
        if ((mask & Trait.VAMPIRE.bit()) != 0 && event.getNewDamage() > 0) {
            attacker.heal(event.getNewDamage() * VAMPIRE_SHARE);
            if (attacker.level() instanceof ServerLevel sl) ServerFx.of("elite_drain", v.position().add(0, v.getBbHeight() * 0.5, 0)).follow(attacker).send(sl);
        }
        if ((mask & Trait.BLAZE.bit()) != 0) v.igniteForSeconds(3);
        if ((mask & Trait.FROST.bit()) != 0) v.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0), attacker);
    }

    /** Regeneration out of the fight, and the guardian's shield. */
    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel sl)) return;
        int mask = traits(mob);
        if (mask == 0 || !mob.isAlive()) return;
        if ((mask & Trait.REGEN.bit()) != 0 && mob.tickCount % 20 == 0 && mob.getHealth() < mob.getMaxHealth()
                && mob.tickCount - mob.getLastHurtByMobTimestamp() >= REGEN_WAIT) {
            mob.heal((float) (mob.getMaxHealth() * REGEN_SHARE));
            ServerFx.of("elite_regen", mob.position()).follow(mob).send(sl);
        }
        if ((mask & Trait.GUARDIAN.bit()) != 0 && mob.getTarget() instanceof Player && (mob.tickCount + mob.getId()) % GUARD_EVERY == 0) {
            // the warning first (a blue ring on the ground), then the shields
            ServerFx.of("elite_guard_warn", mob.position()).follow(mob).a(GUARD_RANGE).b(GUARD_WARN).send(sl);
            sl.playSound(null, mob.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.8F, 1.6F);
            com.jo112ss.rpgmod.job.SkillTasks.later(GUARD_WARN, () -> {
                if (!mob.isAlive()) return;
                List<Mob> shielded = new ArrayList<>();
                for (Mob m : sl.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(GUARD_RANGE), m -> m.isAlive() && m instanceof Enemy && m.distanceTo(mob) <= GUARD_RANGE)) {
                    int amp = Math.max(0, Math.min(255, (int) Math.round(m.getMaxHealth() * GUARD_SHARE / 4.0) - 1));
                    m.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, amp), mob);
                    shielded.add(m);
                }
                ServerFx.of("elite_guard", mob.position()).follow(mob).a(GUARD_RANGE).points(shielded).send(sl);
                sl.playSound(null, mob.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.8F, 1.4F);
            });
        }
    }

    /** A splitting elite falls apart into two weak halves of its kind. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel sl) || !has(mob, Trait.SPLIT)) return;
        int lv = MobLevels.level(mob);
        for (int s = -1; s <= 1; s += 2) {
            Entity e = mob.getType().create(sl, EntitySpawnReason.MOB_SUMMONED);
            if (!(e instanceof Mob half)) continue;
            Vec3 side = new Vec3(-mob.getLookAngle().z, 0, mob.getLookAngle().x).normalize().scale(0.6 * s);
            half.snapTo(mob.getX() + side.x, mob.getY(), mob.getZ() + side.z, mob.getYRot(), 0);
            half.addTag(HALF);
            half.getPersistentData().putBoolean(com.jo112ss.rpgmod.mob.boss.BossEvents.CHILD, true); // no gems or potions from the halves
            if (lv > 0) half.setData(ModRegistries.MOB_LEVEL, lv);
            sl.addFreshEntity(half);
            AttributeInstance size = half.getAttribute(Attributes.SCALE);
            if (size != null) size.addPermanentModifier(new AttributeModifier(id("elite_half_scale"), -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            half.setHealth(half.getMaxHealth() * 0.3F);
            if (mob.getTarget() != null) half.setTarget(mob.getTarget());
        }
        ServerFx.of("elite_split", mob.position()).send(sl);
    }

    /** Drop chance factors for gems and potions. */
    public static final double GEM_MULT = 3, POTION_MULT = 2;
}
