package com.jo112ss.rpgmod.job;

import static com.jo112ss.rpgmod.job.SkillExecutor.*;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.SkillExecutor.Ctx;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.registry.ModEffects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The reworked ultimates that used to look like another job's: each now does something of its
 * own (one ally made untouchable, damage turned back into health, a fortress that hits back, a
 * symphony played as attacks, every afterglow at once, sealing notes, a field of flowers, a great
 * beam, a spinning charge, stopped time, shadow copies, five shockwave swings).
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class UltSkills {
    private UltSkills() {}

    /** Dev test: logs what the ultimates do. */
    public static boolean testLog;

    static void log(String msg, Object... args) {
        if (testLog) RpgMod.LOGGER.info("[AUTOTEST] ult " + msg, args);
    }

    // ------------------------------------------------------------------ paladin: divine aegis

    /** One ally (the one looked at, else yourself) cannot be hurt for a while and is healed in full. */
    static boolean divineAegis(Ctx c) {
        ServerPlayer p = c.p();
        ServerPlayer t = lookAlly(c, c.def().radius());
        if (t == null) t = p;
        int life = ticks(c.def().duration());
        t.addEffect(new MobEffectInstance(ModEffects.HOLY_AEGIS, life, 0), p);
        HealthScale.heal(t, t.getMaxHealth());
        ServerFx fx = ServerFx.of("u_divine_aegis", t.position()).follow(t).b(c.def().duration());
        if (t != p) fx.vec(new Vec3(1, 0, 0)).point(c.chest());
        fx.send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 1.3F);
        sound(c, t.position(), SoundEvents.TOTEM_USE, 0.6F, 1.4F);
        log("divine_aegis on {} for {}t", t == p ? "self" : t.getName().getString(), life);
        return true;
    }

    /** The ally player nearest the crosshair (within 12 degrees and sight), or null. */
    static ServerPlayer lookAlly(Ctx c, double range) {
        ServerPlayer p = c.p();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        ServerPlayer best = null;
        double bestDot = Math.cos(Math.toRadians(12));
        for (ServerPlayer a : allies(p, range)) {
            if (a == p) continue;
            Vec3 to = a.position().add(0, a.getBbHeight() * 0.5, 0).subtract(eye);
            double dot = to.normalize().dot(look);
            if (dot > bestDot && canSee(c.level(), eye, a.getEyePosition(), p)) {
                bestDot = dot;
                best = a;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ healer: guardian light

    /** Share of each blow given back, and over how long (in six steps). */
    public static final float REWIND_SHARE = 0.5F;
    public static final int REWIND_TICKS = 60;

    /** The party around: half of every blow they take comes back as health over the next 3s. */
    static boolean guardianLight(Ctx c) {
        ServerPlayer p = c.p();
        int life = ticks(c.def().duration());
        List<ServerPlayer> allies = allies(p, c.def().radius());
        for (ServerPlayer a : allies) a.addEffect(new MobEffectInstance(ModEffects.REWIND, life, 0), p);
        ServerFx.of("u_guardian_light", p.position()).follow(p).a(c.def().radius()).b(c.def().duration()).points(others(p, allies)).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 1.5F);
        sound(c, p.position(), SoundEvents.TOTEM_USE, 0.5F, 1.6F);
        return true;
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer v) || !v.hasEffect(ModEffects.REWIND)) return;
        float dealt = event.getInflictedDamage();
        if (dealt <= 0) return;
        float step = dealt * REWIND_SHARE / 6;
        ServerLevel level = (ServerLevel) v.level();
        SkillTasks.run(age -> {
            if (!v.isAlive()) return true;
            if (age > 0 && age % (REWIND_TICKS / 6) == 0) {
                HealthScale.heal(v, step);
                ServerFx.of("u_rewind", v.position()).follow(v).send(level);
            }
            return age >= REWIND_TICKS;
        });
        log("rewind {} takes {} -> {} back", v.getName().getString(), String.format("%.1f", dealt), String.format("%.1f", dealt * REWIND_SHARE));
    }

    // ------------------------------------------------------------------ sentinel: fortress

    /** Share of the party's blows the fortress takes, and how hard what it took comes back. */
    public static final float FORTRESS_CUT = 0.3F, FORTRESS_RATE = 3F;
    private static final Map<UUID, UUID> FORT_OWNER = new HashMap<>();
    private static final Map<UUID, float[]> FORT_STORE = new HashMap<>();

    /** 10s: the party around takes 30% less, the sentinel stands firm; what was kept off bursts out at the end. */
    static boolean fortress(Ctx c) {
        ServerPlayer p = c.p();
        int t = ticks(c.def().duration());
        List<ServerPlayer> allies = allies(p, c.def().radius());
        FORT_STORE.put(p.getUUID(), new float[1]);
        for (ServerPlayer a : allies) {
            a.addEffect(new MobEffectInstance(ModEffects.FORTRESS, t, 0), p);
            FORT_OWNER.put(a.getUUID(), p.getUUID());
        }
        p.addEffect(new MobEffectInstance(ModEffects.IMMOVABLE, t, 0), p);
        ServerFx.of("u_fortress", p.position()).follow(p).a(c.def().radius()).b(c.def().duration()).points(others(p, allies)).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 0.9F);
        sound(c, p.position(), SoundEvents.ANVIL_LAND, 0.6F, 0.8F);
        SkillTasks.later(t, () -> fortressBurst(c));
        return true;
    }

    /** A blow on someone under the fortress: 30% of it is kept off and stored. Returns what is left. */
    public static float fortressCut(ServerPlayer v, float amount) {
        float cut = amount * FORTRESS_CUT;
        UUID owner = FORT_OWNER.get(v.getUUID());
        float[] store = owner == null ? null : FORT_STORE.get(owner);
        if (store != null) store[0] += cut;
        return amount - cut;
    }

    private static void fortressBurst(Ctx c) {
        ServerPlayer p = c.p();
        FORT_OWNER.values().removeIf(o -> o.equals(p.getUUID()));
        float[] store = FORT_STORE.remove(p.getUUID());
        if (store == null || !p.isAlive()) return;
        double r = c.def().radius();
        float dmg = Math.min(c.dmg(1) + store[0] * FORTRESS_RATE, c.dmg(10));
        List<LivingEntity> hit = enemies(p, p.position(), r);
        for (LivingEntity e : hit) {
            hurt(c, e, dmg, melee(c));
            pushAway(e, p.position(), 0.9, 0.3);
        }
        ServerFx.of("u_fortress_burst", p.position()).a(r).points(hit).send(c.level());
        sound(c, p.position(), SoundEvents.TRIDENT_THUNDER, 0.8F, 1.2F);
        sound(c, p.position(), SoundEvents.ANVIL_LAND, 0.8F, 0.6F);
        log("fortress burst stored {} -> {} each on {}", String.format("%.1f", store[0]), String.format("%.1f", dmg), hit.size());
    }

    // ------------------------------------------------------------------ maestro: symphony

    /** 12s: every note played well sends a shockwave through the enemies around (the performance starts if it has not). */
    static boolean symphony(Ctx c) {
        ServerPlayer p = c.p();
        if (!Bard.performing(p) && !Bard.perform(c)) return false;
        int life = ticks(c.def().duration());
        p.addEffect(new MobEffectInstance(ModEffects.SYMPHONY, life, 0), p);
        ServerFx.of("u_symphony", p.position()).follow(p).a(c.def().radius()).b(c.def().duration()).send(c.level());
        sound(c, SoundEvents.PLAYER_LEVELUP, 1.0F);
        return true;
    }

    /** A note judged during the symphony (from {@link Bard#judge}). */
    static void symphonyNote(ServerPlayer p, int grade, int points) {
        SkillDef def = JobDefs.skill("symphony");
        Ctx c = new Ctx(p, (ServerLevel) p.level(), def, JobManager.powerMultiplier(p, def), 1.0);
        double r = Bard.radius(points) * 2; // the ultimate reaches twice as far as the songs
        List<LivingEntity> hit = enemies(p, p.position(), r);
        float dmg = c.dmg(grade == Bard.PERFECT ? 1.0 : 0.6);
        for (LivingEntity e : hit) {
            hurt(c, e, dmg, magic(c));
            pushAway(e, p.position(), 0.25, 0.08);
        }
        ServerFx.of("u_symphony_note", p.position()).follow(p).a(r).b(grade).points(hit).send(c.level());
        log("symphony note grade {} hits {} for {}", grade, hit.size(), String.format("%.1f", dmg));
    }

    /** Dev test: one perfect note of the symphony. */
    public static void symphonyNoteForTest(ServerPlayer p) {
        symphonyNote(p, Bard.PERFECT, JobManager.data(p).songPoints());
    }

    // ------------------------------------------------------------------ spellsinger: grand concerto, sealing chord

    /** Every song set on the lute leaves its afterglow on the party at once, as if played perfectly. */
    static boolean grandConcerto(Ctx c) {
        ServerPlayer p = c.p();
        int n = Bard.lingerAll(p);
        if (n == 0) {
            fail(p, "rpgmod.bard.no_song");
            return false;
        }
        double r = Bard.radius(JobData.MAX_SONG_POINTS) * Bard.CONCERTO_REACH;
        ServerFx.of("u_grand_concerto", p.position()).follow(p).a(r).send(c.level());
        sound(c, SoundEvents.PLAYER_LEVELUP, 1.1F);
        for (float pitch : new float[] {0.84F, 1.0F, 1.26F, 1.5F}) sound(c, p.position(), SoundEvents.NOTE_BLOCK_CHIME, 0.5F, pitch);
        log("grand_concerto afterglow of {} songs", n);
        return true;
    }

    /** Silenced enemies cannot hurt anyone for this long. */
    public static final int SILENCE_TICKS = 60;

    /** Ends the performance: one seeking note per point (at least 3) flies at the enemies around and silences them. */
    static boolean sealingChord(Ctx c) {
        ServerPlayer p = c.p();
        JobData d = JobManager.data(p);
        int points = Bard.performing(p) ? d.songPoints() : 0;
        List<LivingEntity> foes = new ArrayList<>(enemies(p, p.position(), c.def().radius()));
        foes.removeIf(e -> !canSee(c.level(), p.getEyePosition(), e.position().add(0, e.getBbHeight() * 0.5, 0), p));
        if (foes.isEmpty()) {
            fail(p, "rpgmod.job.no_target");
            return false;
        }
        foes.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        Bard.stop(p);
        d.setSongPoints(0);
        JobManager.sync(p);
        int n = Math.max(3, points);
        for (int k = 0; k < n; k++) {
            LivingEntity t = foes.get(k % foes.size());
            float pitch = 1.0F + (k % 8) * 0.12F;
            SkillTasks.later(k * 2, () -> {
                if (!t.isAlive() || !p.isAlive()) return;
                Vec3 start = c.hand().add(p.getRandom().nextGaussian() * 0.3, p.getRandom().nextFloat() * 0.6, p.getRandom().nextGaussian() * 0.3);
                Vec3 dir = t.position().add(0, t.getBbHeight() * 0.55, 0).subtract(start).normalize().add(0, 0.25, 0);
                ServerFx.of("n_wave", start).vec(dir.normalize().scale(1.1)).a(c.def().radius() + 6).color(0xB070E0).follow(t).send(c.level());
                projectile(c, start, dir, 1.1, c.def().radius() + 6, t, 0.35, (e, at, dd) -> {
                    hurt(c, e, c.dmg(1), magic(c));
                    e.addEffect(new MobEffectInstance(ModEffects.SILENCED, SILENCE_TICKS, 0), p);
                    ServerFx.of("u_seal_hit", at).follow(e).b(SILENCE_TICKS).send(c.level());
                }, (at, dd, wall) -> { });
                sound(c, p.position(), SoundEvents.NOTE_BLOCK_BELL, 0.7F, pitch);
            });
        }
        ServerFx.of("u_sealing", p.position()).send(c.level());
        sound(c, p.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.5F);
        log("sealing_chord {} notes at {} enemies (points {})", n, foes.size(), points);
        return true;
    }

    // ------------------------------------------------------------------ god hand: blooming fists

    /** A field of flowers: allies hitting from inside heal the party in it; it bursts at the end. */
    private record Bloom(ServerPlayer owner, Ctx c, Vec3 at, double r, long end, long[] lastHeal) {}

    private static final List<Bloom> BLOOMS = new ArrayList<>();
    /** Health (share of max) every ally in the field gets per hit landed from it; how often at most. */
    public static final float BLOOM_HEAL = 0.015F;
    public static final int BLOOM_GAP = 5;

    static boolean bloomingFists(Ctx c) {
        ServerPlayer p = c.p();
        Vec3 at = p.position();
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        Bloom b = new Bloom(p, c, at, r, c.level().getGameTime() + life, new long[1]);
        BLOOMS.add(b);
        ServerFx.of("u_bloom_field", at).a(r).b(life).send(c.level());
        sound(c, SoundEvents.CHERRY_LEAVES_PLACE, 0.8F);
        sound(c, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.3F);
        SkillTasks.run(age -> {
            if (age < life) return false;
            BLOOMS.remove(b);
            List<LivingEntity> hit = enemies(p, at, r);
            for (LivingEntity e : hit) {
                hurt(c, e, c.dmg(1), melee(c));
                pushAway(e, at, 0.5, 0.35);
            }
            ServerFx.of("u_bloom_burst", at).a(r).points(hit).send(c.level());
            sound(c, at, SoundEvents.CHERRY_LEAVES_BREAK, 1.0F, 0.8F);
            sound(c, at, SoundEvents.GENERIC_EXPLODE, 0.5F, 1.5F);
            log("blooming_fists burst on {}", hit.size());
            return true;
        });
        return true;
    }

    private static void bloomHit(ServerPlayer attacker) {
        if (BLOOMS.isEmpty()) return;
        long now = attacker.level().getGameTime();
        for (Bloom b : BLOOMS) {
            if (b.owner().level() != attacker.level() || attacker.position().distanceTo(b.at()) > b.r() + 0.5) continue;
            if (now - b.lastHeal()[0] < BLOOM_GAP) continue;
            b.lastHeal()[0] = now;
            List<ServerPlayer> in = attacker.level().getEntitiesOfClass(ServerPlayer.class, new AABB(b.at(), b.at()).inflate(b.r() + 1),
                    a -> a.isAlive() && a.position().distanceTo(b.at()) <= b.r() + 0.5);
            for (ServerPlayer a : in) HealthScale.heal(a, a.getMaxHealth() * BLOOM_HEAL);
            ServerFx.of("u_bloom_heal", attacker.position().add(0, 1.0, 0)).points(in).send((ServerLevel) attacker.level());
            log("bloom heal {} allies", in.size());
        }
    }

    // ------------------------------------------------------------------ spellmaster: great beam

    /** 3s: a thick beam from the hand along the look (it follows the aim), hitting each enemy on it every half second. */
    static boolean greatBeam(Ctx c) {
        ServerPlayer p = c.p();
        int life = ticks(c.def().duration());
        double range = c.def().radius();
        int hits = Math.max(1, life / 10);
        Map<Integer, Long> last = new HashMap<>();
        int[] total = {0};
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, life, 3, false, false), p);
        ServerFx.of("u_beam_start", p.position()).follow(p).b(life).send(c.level());
        sound(c, SoundEvents.BEACON_ACTIVATE, 1.6F);
        sound(c, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 0.8F, 1.4F);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            if (age % 2 == 0) {
                Vec3 start = c.hand();
                Vec3 look = p.getLookAngle();
                BlockHitResult block = c.level().clip(new ClipContext(start, start.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                Vec3 end = block.getType() == HitResult.Type.MISS ? start.add(look.scale(range)) : block.getLocation();
                List<LivingEntity> on = new ArrayList<>();
                for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(2), e -> isEnemy(p, e))) {
                    Vec3 mid = e.position().add(0, e.getBbHeight() * 0.5, 0);
                    if (distanceToSegment(mid, start, end) <= 1.3 + e.getBbWidth() / 2) on.add(e);
                }
                long now = c.level().getGameTime();
                List<LivingEntity> hit = new ArrayList<>();
                for (LivingEntity e : on) {
                    Long t = last.get(e.getId());
                    if (t != null && now - t < 10) continue;
                    last.put(e.getId(), now);
                    hurt(c, e, c.dmg(1.0 / hits), magic(c));
                    hit.add(e);
                    total[0]++;
                }
                ServerFx.of("u_beam", start).vec(end).points(hit).send(c.level());
                if (age % 10 == 0) sound(c, p.position(), SoundEvents.BEACON_AMBIENT, 1.2F, 2.0F);
            }
            if (age >= life) log("great beam {} hits of {} each", total[0], String.format("%.1f", c.dmg(1.0 / hits)));
            return age >= life;
        });
        return true;
    }

    static double distanceToSegment(Vec3 x, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len < 1e-6 ? 0 : Math.max(0, Math.min(1, x.subtract(a).dot(ab) / len));
        return x.distanceTo(a.add(ab.scale(t)));
    }

    // ------------------------------------------------------------------ lancer: dragon fang

    /** 3s spinning forward with the spear: enemies are caught and carried along, and thrown up at the end. */
    static boolean dragonFang(Ctx c) {
        ServerPlayer p = c.p();
        int life = ticks(c.def().duration());
        double r = c.def().radius();
        Set<LivingEntity> carried = new LinkedHashSet<>();
        ServerFx.of("u_dragon_fang", p.position()).follow(p).a(r).b(c.def().duration()).send(c.level());
        sound(c, SoundEvents.ENDER_DRAGON_FLAP, 1.0F);
        SkillTasks.run(age -> {
            if (!p.isAlive()) return true;
            Vec3 f = c.flat();
            if (age < life) {
                Vec3 v = p.getDeltaMovement();
                launch(p, new Vec3(f.x * 0.42, Math.min(v.y, 0.1), f.z * 0.42));
                if (age % 4 == 0) {
                    for (LivingEntity e : enemies(p, p.position(), r)) {
                        hurt(c, e, c.dmg(0.06), melee(c));
                        carried.add(e);
                    }
                    sound(c, p.position(), SoundEvents.TRIDENT_RIPTIDE_1, 0.6F, 1.2F + (age % 12) * 0.03F);
                }
                // the caught ones are swept along in front
                Vec3 front = p.position().add(f.scale(1.6));
                for (LivingEntity e : carried) {
                    if (!e.isAlive() || Venom.isBoss(e) || e.distanceTo(p) > 6) continue;
                    Vec3 to = front.add(e.position().subtract(front).scale(0.6));
                    if (c.level().noCollision(e, e.getBoundingBox().move(to.subtract(e.position())))) e.setPos(to.x, e.getY(), to.z);
                    e.setDeltaMovement(Vec3.ZERO);
                }
                return false;
            }
            List<LivingEntity> thrown = new ArrayList<>();
            for (LivingEntity e : carried) {
                if (!e.isAlive() || e.distanceTo(p) > r + 3) continue;
                hurt(c, e, c.dmg(0.4), melee(c));
                NewJobSkills.airborne(c, e, 1.0);
                thrown.add(e);
            }
            ServerFx.of("u_dragon_fang_end", p.position()).vec(f).points(thrown).send(c.level());
            sound(c, p.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2F, 0.8F);
            log("dragon_fang carried {} thrown {}", carried.size(), thrown.size());
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ wizard: time stop

    private record Frozen(boolean wasNoAi, float[] stored, Ctx c) {}

    private static final Map<Integer, Frozen> FROZEN = new HashMap<>();
    static final String FROZEN_TAG = "rpgmod_time_stopped";
    /** What the stopped enemies took comes in at the end this much stronger. */
    public static final float STOP_BONUS = 1.2F;
    private static boolean releasing;

    /** The enemies around stop dead for 3s (bosses only slow down); the damage they take while stopped all lands at the end, +20%. */
    static boolean timeStop(Ctx c) {
        ServerPlayer p = c.p();
        double r = c.def().radius();
        int life = ticks(c.def().duration());
        Vec3 o = p.position();
        List<LivingEntity> hit = enemies(p, o, r);
        int stopped = 0;
        for (LivingEntity e : hit) {
            if (Venom.isBoss(e) || !(e instanceof Mob m)) {
                apply(e, c.def(), p);
                continue;
            }
            if (FROZEN.containsKey(e.getId())) continue;
            FROZEN.put(e.getId(), new Frozen(m.isNoAi(), new float[1], c));
            m.setNoAi(true);
            m.addTag(FROZEN_TAG);
            m.setDeltaMovement(Vec3.ZERO);
            m.addEffect(new MobEffectInstance(ModEffects.TIME_STOP, life, 0), p);
            stopped++;
            SkillTasks.later(life, () -> unfreeze(m, true));
        }
        ServerFx.of("u_time_stop", o).a(r).b(life).points(hit).send(c.level());
        sound(c, SoundEvents.BELL_RESONATE, 0.5F);
        sound(c, o, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1.0F, 0.5F);
        log("time_stop stopped {} of {}", stopped, hit.size());
        return true;
    }

    private static void unfreeze(Mob m, boolean land) {
        Frozen f = FROZEN.remove(m.getId());
        if (f == null) return;
        if (!f.wasNoAi()) m.setNoAi(false);
        m.removeTag(FROZEN_TAG);
        m.removeEffect(ModEffects.TIME_STOP);
        if (!land || !m.isAlive() || f.stored()[0] <= 0) return;
        float dmg = f.stored()[0] * STOP_BONUS;
        releasing = true;
        try {
            m.hurtServer(f.c().level(), magic(f.c()), dmg);
        } finally {
            releasing = false;
        }
        ServerFx.of("u_time_release", m.position().add(0, m.getBbHeight() * 0.5, 0)).send(f.c().level());
        f.c().level().playSound(null, m.blockPosition(), SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.7F);
        log("time_stop release {} stored {} -> {}", m.getType().toShortString(), String.format("%.1f", f.stored()[0]), String.format("%.1f", dmg));
    }

    /** A mob left stopped by a restart is let go. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob m) || !m.entityTags().contains(FROZEN_TAG)) return;
        if (FROZEN.containsKey(m.getId())) return;
        m.removeTag(FROZEN_TAG);
        m.setNoAi(false);
    }

    // ------------------------------------------------------------------ assassin: shadow dance (shadow copies)

    /** Share of each normal hit that each of the two shadows copies. */
    public static final float CLONE_SHARE = 0.5F;

    /** 10s: two shadows copy every normal attack you land (half its damage each). */
    static boolean shadowDance(Ctx c) {
        ServerPlayer p = c.p();
        p.addEffect(new MobEffectInstance(ModEffects.SHADOW_CLONE, ticks(c.def().duration()), 0), p);
        ServerFx.of("u_shadow_dance", p.position()).follow(p).a(c.def().duration()).send(c.level());
        sound(c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F);
        return true;
    }

    private static void cloneStrikes(ServerPlayer p, LivingEntity target, float amount) {
        SkillDef def = JobDefs.skill("shadow_dance");
        Ctx c = new Ctx(p, (ServerLevel) p.level(), def, 1, 1.0);
        Vec3 side = SkillExecutor.Fx3.right(c.flat());
        for (int k = 1; k <= 2; k++) {
            int s = k == 1 ? -1 : 1;
            SkillTasks.later(k * 3, () -> {
                if (!target.isAlive() || !p.isAlive()) return;
                Vec3 at = target.position().add(side.scale(1.2 * s)).subtract(c.flat().scale(0.8));
                ServerFx.of("u_shadow_strike", target.position().add(0, target.getBbHeight() * 0.55, 0)).vec(at).send(c.level());
                hurt(c, target, amount * CLONE_SHARE, melee(c));
                sound(c, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 1.4F);
            });
        }
        log("shadow clones copy {}", String.format("%.1f", amount));
    }

    // ------------------------------------------------------------------ fighter: war god (berserk)

    /** 15s: the next five normal hits each send a great shockwave slash ahead. */
    static boolean warGod(Ctx c) {
        ServerPlayer p = c.p();
        p.addEffect(new MobEffectInstance(ModEffects.WAR_GOD, ticks(c.def().duration()), 4), p);
        ServerFx.of("u_war_god", p.position()).follow(p).a(c.def().duration()).send(c.level());
        sound(c, SoundEvents.RAVAGER_ROAR, 0.8F);
        sound(c, p.position(), SoundEvents.FIRECHARGE_USE, 0.8F, 0.6F);
        return true;
    }

    private static void warGodSwing(ServerPlayer p) {
        MobEffectInstance cur = p.getEffect(ModEffects.WAR_GOD);
        if (cur == null) return;
        p.removeEffect(ModEffects.WAR_GOD);
        if (cur.getAmplifier() > 0) p.addEffect(new MobEffectInstance(ModEffects.WAR_GOD, cur.getDuration(), cur.getAmplifier() - 1), p);
        SkillDef def = JobDefs.skill("berserk");
        Ctx c = new Ctx(p, (ServerLevel) p.level(), def, JobManager.powerMultiplier(p, def), 1.0);
        SkillTasks.later(1, () -> {
            double r = def.radius();
            List<LivingEntity> hit = cone(c, r, 35);
            for (LivingEntity e : hit) {
                hurt(c, e, c.dmg(1), melee(c));
                push(e, c.flat(), 0.8, 0.25);
            }
            ServerFx.of("u_war_god_swing", c.chest()).vec(c.flat()).a(r).points(hit).send(c.level());
            sound(c, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
            sound(c, p.position(), SoundEvents.GENERIC_EXPLODE, 0.5F, 1.3F);
            log("war god swing {} left, hits {} for {}", cur.getAmplifier(), hit.size(), String.format("%.1f", c.dmg(1)));
        });
    }

    // ------------------------------------------------------------------ hooks

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingFirst(LivingIncomingDamageEvent event) {
        LivingEntity v = event.getEntity();
        if (v.hasEffect(ModEffects.HOLY_AEGIS)) {
            event.setCanceled(true);
            return;
        }
        // stopped or silenced monsters hurt no one
        if (event.getSource().getEntity() instanceof LivingEntity from && from != v
                && (from.hasEffect(ModEffects.TIME_STOP) || from.hasEffect(ModEffects.SILENCED))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingLast(LivingIncomingDamageEvent event) {
        LivingEntity v = event.getEntity();
        Frozen f = releasing ? null : FROZEN.get(v.getId());
        if (f != null && event.getAmount() > 0) { // stopped in time: kept for the end
            f.stored()[0] += event.getAmount();
            event.setCanceled(true);
            return;
        }
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p) || src.getDirectEntity() != p || v == p || event.getAmount() <= 0) return;
        if (!SkillExecutor.isEnemy(p, v)) return;
        bloomHit(p);
        if (!src.is(DamageTypes.PLAYER_ATTACK)) return;
        if (p.hasEffect(ModEffects.SHADOW_CLONE)) cloneStrikes(p, v, event.getAmount());
        if (p.hasEffect(ModEffects.WAR_GOD)) warGodSwing(p);
    }

    public static void forget(UUID id) {
        FORT_STORE.remove(id);
        FORT_OWNER.remove(id);
    }
}
