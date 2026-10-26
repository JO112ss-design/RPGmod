package com.jo112ss.rpgmod.job;

import static com.jo112ss.rpgmod.job.SkillExecutor.*;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.SkillExecutor.Ctx;
import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.job.core.SkillEffect;
import com.jo112ss.rpgmod.job.core.SkillKind;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The spellblade's sword magic: hold right click with a sword to charge a spell of the sword's
 * element, release to cast it. Three stages ({@link #STAGE1}, {@link #STAGE2} and {@link #STAGE3} ticks held; let go sooner and it fizzles at no cost
 * held): each stage hits much harder and costs much more mana.
 * <ul>
 * <li>fire: a fireball that bursts where it hits (a small area);</li>
 * <li>ice: frost erupting over a wide area at the aimed spot, weaker, slowing;</li>
 * <li>thunder: lightning on the one enemy aimed at, the strongest single hit.</li>
 * </ul>
 */
public final class SpellbladeMagic {
    private SpellbladeMagic() {}

    /** Ticks held for the second and third stage. */
    public static final int STAGE1 = 16, STAGE2 = 36, STAGE3 = 56;
    /** Mana per element (fire, ice, thunder) and stage. */
    public static final int[][] COST = {{10, 24, 45}, {12, 28, 50}, {14, 32, 55}};
    /** Damage ratio (x attack + magic attack) per element and stage. */
    static final double[][] POWER = {{1.05, 1.68, 2.94}, {0.62, 0.98, 1.68}, {1.75, 2.8, 4.9}};
    /** Fireball burst radius and frost field radius per stage. */
    static final double[] FIRE_R = {1.6, 2.2, 3.0}, ICE_R = {3.5, 9.0, 11.7};
    static final double FIRE_RANGE = 28, ICE_RANGE = 16, THUNDER_RANGE = 24, FIREBALL_SPEED = 1.2;
    /** A pause after each cast before the next charge can start. */
    static final int RECAST = 8;

    /** Dev test: log every cast. */
    public static boolean testLog;

    private record Charge(long start, int element) {}

    private static final Map<UUID, Charge> CHARGING = new HashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    /** Can this player charge sword magic right now (spellblade, sword in hand)? */
    public static boolean canUse(ServerPlayer p) {
        JobData d = JobManager.data(p);
        return "spellblade".equals(d.jobId()) && p.isAlive() && !p.isSpectator()
                && ModItems.typeOf(p.getMainHandItem().getItem()) == WeaponType.SWORD;
    }

    /**
     * The three stages' charge times for this player: shortened by cast speed like a staff spell
     * (panel and forged options up to -60%, quick cast beyond).
     */
    public static int[] stageTicks(net.minecraft.world.entity.player.Player p) {
        double speed = Math.min(60, JobManager.castSpeedPercent(p));
        if (p.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.QUICK_CAST)) speed += 40;
        double f = 1 - Math.min(85, speed) / 100.0;
        return new int[] {(int) Math.round(STAGE1 * f), (int) Math.round(STAGE2 * f), (int) Math.round(STAGE3 * f)};
    }

    public static int stage(int[] at, int held) {
        return held >= at[2] ? 3 : held >= at[1] ? 2 : held >= at[0] ? 1 : 0;
    }

    /** Right click pressed with the sword: start charging (if a first-stage spell can be paid for). */
    public static void press(ServerPlayer p) {
        if (!canUse(p) || CHARGING.containsKey(p.getUUID())) return;
        ServerLevel level = (ServerLevel) p.level();
        long now = level.getGameTime();
        if (NEXT.getOrDefault(p.getUUID(), 0L) > now) return;
        int el = MoreJobSkills.element(p);
        if (JobManager.data(p).mana() < COST[el][0]) {
            p.sendSystemMessage(Component.translatable("rpgmod.job.no_mana").withStyle(ChatFormatting.RED), true);
            return;
        }
        CHARGING.put(p.getUUID(), new Charge(now, el));
        int color = MoreJobSkills.ELEMENT_COLORS[el];
        int[] at = stageTicks(p);
        ServerFx.of("spell_charge", p.position()).follow(p).b(at[2]).color(color).send(level);
        // a chime as each stage is reached
        SkillTasks.run(age -> {
            Charge ch = CHARGING.get(p.getUUID());
            if (ch == null || ch.start() != now || !canUse(p)) {
                if (ch != null && ch.start() == now) CHARGING.remove(p.getUUID()); // sword put away
                return true;
            }
            int reachedStage = age == at[2] ? 3 : age == at[1] ? 2 : age == at[0] ? 1 : 0;
            if (reachedStage > 0 && JobManager.data(p).mana() >= COST[el][reachedStage - 1]) { // no chime for a stage the mana does not cover
                level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, age == at[2] ? 1.8F : age == at[1] ? 1.5F : 1.2F);
                ServerFx.of("x_burst", p.position().add(0, 1.0, 0)).a(age == at[2] ? 1.2 : 0.8).b(el == 0 ? 0 : el == 1 ? 1 : 2).color(color).send(level);
            }
            return age > 600;
        });
    }

    /** Right click released: cast the stage reached (or the highest one the mana pays for). */
    public static void release(ServerPlayer p) {
        Charge ch = CHARGING.remove(p.getUUID());
        if (ch == null || !canUse(p)) return;
        ServerLevel level = (ServerLevel) p.level();
        int held = (int) (level.getGameTime() - ch.start());
        int el = ch.element();
        JobData d = JobManager.data(p);
        int st = stage(stageTicks(p), held);
        if (st == 0) { // not held long enough: nothing happens, nothing is paid
            p.sendSystemMessage(Component.translatable("rpgmod.job.fizzle").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        while (st > 1 && d.mana() < COST[el][st - 1]) st--;
        if (d.mana() < COST[el][st - 1]) {
            p.sendSystemMessage(Component.translatable("rpgmod.job.no_mana").withStyle(ChatFormatting.RED), true);
            return;
        }
        SkillDef def = new SkillDef("sword_magic_" + el, "spellblade", SkillKind.ACTIVE, SkillEffect.BOLT, 0, COST[el][st - 1],
                POWER[el][st - 1], 0, 0, List.of(), 0, "", MoreJobSkills.ELEMENT_COLORS[el], 0);
        Ctx c = new Ctx(p, level, def, JobManager.powerMultiplier(p, def), 1.0);
        double before = d.mana();
        Map<LivingEntity, Float> hp0 = new HashMap<>();
        if (testLog) for (LivingEntity e : enemies(p, p.position(), 30)) hp0.put(e, e.getHealth());
        boolean ok = switch (el) {
            case 0 -> fire(c, st);
            case 1 -> ice(c, st);
            default -> thunder(c, st);
        };
        if (!ok) return;
        d.setMana(d.mana() - COST[el][st - 1]);
        NEXT.put(p.getUUID(), level.getGameTime() + RECAST);
        JobManager.markCombat(p);
        JobManager.sync(p);
        MoreJobHooks.endMeditation(p);
        if (testLog) {
            RpgMod.LOGGER.info("[AUTOTEST] sword magic element {} held {} -> stage {} cost {} mana {} -> {} ratio {} x {}", el, held, st,
                    COST[el][st - 1], String.format("%.0f", before), String.format("%.0f", d.mana()), POWER[el][st - 1],
                    String.format("%.1f", c.power()));
            int stage = st;
            SkillTasks.later(40, () -> {
                float lost = 0;
                int n = 0;
                for (var en : hp0.entrySet()) {
                    float l = en.getValue() - en.getKey().getHealth();
                    if (l > 0.01) {
                        lost += l;
                        n++;
                    }
                }
                RpgMod.LOGGER.info("[AUTOTEST] sword magic element {} stage {} hit {} enemies for {}", el, stage, n, String.format("%.1f", lost));
            });
        }
    }

    public static void forget(UUID id) {
        CHARGING.remove(id);
        NEXT.remove(id);
    }

    /** Sword magic never crits (its charge is what makes it strong). */
    private static void noCrit(Ctx c, LivingEntity e, float damage) {
        JobEvents.noCrit = true;
        try {
            hurt(c, e, damage, magic(c));
        } finally {
            JobEvents.noCrit = false;
        }
    }

    // ------------------------------------------------------------------ the three spells

    /** A fireball flies to the first enemy or wall in the line of sight and bursts there. */
    private static boolean fire(Ctx c, int st) {
        ServerPlayer p = c.p();
        Vec3 dir = c.look();
        Vec3 start = c.hand().add(dir.scale(0.6));
        MoreJobSkills.Ray ray = MoreJobSkills.ray(c, dir, FIRE_RANGE);
        Vec3 to = ray.end();
        int flight = Math.max(2, (int) Math.ceil(start.distanceTo(to) / FIREBALL_SPEED));
        double r = FIRE_R[st - 1];
        ServerFx.of("n_fireball", start).vec(to).b(flight).a(0.3F + st * 0.2F).send(c.level());
        sound(c, SoundEvents.BLAZE_SHOOT, 1.2F - st * 0.1F);
        if (st == 3) sound(c, p.position(), SoundEvents.FIRECHARGE_USE, 1.0F, 0.6F);
        LivingEntity aimed = ray.hit();
        SkillTasks.later(flight, () -> {
            // a target that moved a little is still hit where it now is
            Vec3 at = aimed != null && aimed.isAlive() && aimed.position().distanceTo(to) < 3 ? NewJobSkills.mid(aimed) : to;
            List<LivingEntity> hit = enemies(p, at, r);
            for (LivingEntity e : hit) {
                double near = 1 - 0.4 * Math.min(1, horizontal(e.position(), at) / r);
                noCrit(c, e, c.dmg(near));
                e.igniteForSeconds(2 + st);
                pushAway(e, at, 0.3 + 0.15 * st, 0.25);
            }
            ServerFx.of("x_burst", at).a(r * 0.8).b(0).color(0xFF6A20).points(hit).send(c.level());
            ServerFx.of("x_ring", at.subtract(0, 0.5, 0)).a(r * 1.2).b(1).color(0xFF6A20).send(c.level());
            sound(c, at, SoundEvents.GENERIC_EXPLODE, 0.5F + 0.25F * st, 1.4F - 0.15F * st);
        });
        return true;
    }

    /** Frost bursts out of the ground over a wide area around the aimed spot. */
    private static boolean ice(Ctx c, int st) {
        ServerPlayer p = c.p();
        Vec3 o = aimPoint(c, ICE_RANGE);
        double r = ICE_R[st - 1];
        ServerFx.of("n_ice_field", o).a(r).b(st).send(c.level());
        sound(c, SoundEvents.PLAYER_HURT_FREEZE, 0.9F);
        sound(c, o, SoundEvents.GLASS_BREAK, 1.0F, 0.7F);
        sound(c, o, SoundEvents.POWDER_SNOW_BREAK, 1.2F, 0.6F);
        for (LivingEntity e : enemies(p, o, r)) {
            int delay = 1 + (int) (horizontal(e.position(), o) / 1.2);
            SkillTasks.later(delay, () -> {
                if (!e.isAlive()) return;
                noCrit(c, e, c.dmg(1));
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40 + 30 * st, st), p);
                e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + 40 * st));
            });
        }
        return true;
    }

    /** Lightning comes down on the enemy aimed at (or the aimed spot): one heavy hit. */
    private static boolean thunder(Ctx c, int st) {
        ServerPlayer p = c.p();
        LivingEntity t = lookTarget(c, THUNDER_RANGE, 8);
        if (t == null) {
            MoreJobSkills.Ray ray = MoreJobSkills.ray(c, c.look(), THUNDER_RANGE);
            t = ray.hit();
        }
        if (t == null) {
            fail(p, "rpgmod.job.no_target");
            return false;
        }
        LivingEntity target = t;
        ServerFx.of("n_thunder", target.position()).vec(c.hand()).a(st).follow(target).send(c.level());
        sound(c, target.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F + 0.2F * st, 1.2F - 0.1F * st);
        if (st == 3) sound(c, target.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1.0F);
        SkillTasks.later(2, () -> {
            if (!target.isAlive()) return;
            noCrit(c, target, c.dmg(1));
            if (st == 3) NewJobSkills.stun(c, target, 20);
        });
        return true;
    }
}
