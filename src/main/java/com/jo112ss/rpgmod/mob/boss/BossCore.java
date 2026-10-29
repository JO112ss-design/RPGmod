package com.jo112ss.rpgmod.mob.boss;

import com.jo112ss.rpgmod.mob.MobConfig;
import com.jo112ss.rpgmod.mob.MobLevels;
import com.jo112ss.rpgmod.mob.core.MobLevelMath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What every boss shares, held by each boss (the King Slime is a slime, the others monsters, so this
 * is a part rather than a base class):
 * <ul>
 * <li>health: base x level growth x (1 + share per extra player fighting), past the vanilla cap
 * played by dividing hits (toughness)</li>
 * <li>the boss bar (name, level, "opening!", purple once enraged)</li>
 * <li>who it fights (the nearest player within 48 who can be hurt) and who took part (rewards)</li>
 * <li>keeping out of water, homing shots at someone out on it ({@link BossWater})</li>
 * <li>small helpers: ground under a point, throwing a player, facing</li>
 * </ul>
 */
public final class BossCore {
    public static final double TARGET_RANGE = 48, BAR_RANGE = 72;

    private final Mob mob;
    private final String key;
    private final DoubleSupplier baseHealth;
    /** Extra health per extra player (the dungeon bosses' setting; the abyssal wither has the dragon's). */
    private final DoubleSupplier perPlayer;
    private final BossEvent.BossBarColor color;
    /** Players who hurt it (rewarded with everyone near when it dies). */
    public final Set<UUID> participants = new HashSet<>();
    public final BossWater.Tracker water = new BossWater.Tracker();
    public final BossWater.Homing homing;
    private ServerBossEvent bar;
    private int onlineSeen = -1;
    private double healthTarget;
    private float toughness = 1;
    /** Tick until which the halfway gate still holds after the second phase began. */
    private int gateUntil = -1;
    /** How long the gate holds once the second phase has begun (its entrance plays out). */
    public static final int GATE_HOLD = 40;

    /**
     * @param key         lang key part ({@code king_slime}...): {@code entity.rpgmod.<key>}, {@code rpgmod.boss.<key>.defeated}
     * @param baseHealth  health at level 1 for one player (config)
     * @param color       boss bar colour (purple once enraged)
     * @param shotColor   colour of its homing shots
     */
    public BossCore(Mob mob, String key, DoubleSupplier baseHealth, BossEvent.BossBarColor color, int shotColor) {
        this(mob, key, baseHealth, MobConfig::bossPerPlayer, color, shotColor);
    }

    public BossCore(Mob mob, String key, DoubleSupplier baseHealth, DoubleSupplier perPlayer, BossEvent.BossBarColor color, int shotColor) {
        this.mob = mob;
        this.key = key;
        this.baseHealth = baseHealth;
        this.perPlayer = perPlayer;
        this.color = color;
        this.homing = new BossWater.Homing(shotColor);
    }

    public String key() {
        return key;
    }

    public int level() {
        return Math.max(1, MobLevels.level(mob));
    }

    // ------------------------------------------------------------------ health

    public float toughness() {
        return toughness;
    }

    /** The max health set by the last recompute (0 before the first). */
    public double healthTarget() {
        return healthTarget;
    }

    /** Max health from level and players; the current share of health is kept unless {@code fill}. */
    public void recomputeHealth(boolean fill) {
        if (!(mob.level() instanceof ServerLevel sl)) return;
        int online = com.jo112ss.rpgmod.dungeon.Dungeons.bossPlayers(mob, sl);
        onlineSeen = online;
        double hp = baseHealth.getAsDouble() * MobLevelMath.healthMultiplier(MobConfig.rules(), level()) * (1 + perPlayer.getAsDouble() * (online - 1));
        float ratio = mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 1;
        double applied = Math.min(hp, 1000);
        toughness = (float) (hp / applied);
        healthTarget = applied;
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(applied);
        mob.setHealth(fill ? mob.getMaxHealth() : Math.max(1, ratio * mob.getMaxHealth()));
    }

    // ------------------------------------------------------------------ every tick

    /**
     * Housekeeping, every server tick before the boss's own moves: the bar, health when players come or
     * go, homing shots in flight, the water tracker. {@code tag}: shown after the name on the bar
     * ({@link #opening()} in an opening), or null.
     */
    public void tick(ServerLevel sl, int phase, Component tag) {
        if (mob.tickCount % 10 == 0) updateBar(sl, phase, tag);
        if (mob.tickCount % 20 == 0 && healthTarget > 0 && com.jo112ss.rpgmod.dungeon.Dungeons.bossPlayers(mob, sl) != onlineSeen) recomputeHealth(false);
        homing.tick(sl, mob);
        water.tick(mob.getTarget());
    }

    // ------------------------------------------------------------------ boss bar

    /** "Opening!" on the boss bar. */
    public static Component opening() {
        return Component.translatable("rpgmod.boss.opening").withStyle(ChatFormatting.YELLOW);
    }

    private Component name(Component tag) {
        Component n = Component.translatable("entity.rpgmod." + key).append(Component.literal("  Lv " + level()));
        if (tag != null) n = n.copy().append(Component.literal("  ")).append(tag);
        return n;
    }

    public void updateBar(ServerLevel sl, int phase, Component tag) {
        if (bar == null) bar = new ServerBossEvent(UUID.randomUUID(), name(tag), phase >= 2 ? BossEvent.BossBarColor.PURPLE : color, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setName(name(tag));
        bar.setProgress(Mth.clamp(mob.getHealth() / mob.getMaxHealth(), 0, 1));
        for (ServerPlayer p : sl.players()) {
            boolean near = p.distanceToSqr(mob) < BAR_RANGE * BAR_RANGE;
            if (near && !bar.getPlayers().contains(p)) bar.addPlayer(p);
            else if (!near && bar.getPlayers().contains(p)) bar.removePlayer(p);
        }
        for (ServerPlayer p : List.copyOf(bar.getPlayers())) {
            if (p.level() != sl || p.isRemoved()) bar.removePlayer(p);
        }
    }

    /** Enraged (below half health): the bar turns purple. */
    public void enraged() {
        if (bar != null) bar.setColor(BossEvent.BossBarColor.PURPLE);
        gateUntil = mob.tickCount + GATE_HOLD;
    }

    /**
     * The halfway gate: health cannot be taken below half before the second phase has begun and its
     * entrance played out, so a burst in an opening never skips the second half's opening act.
     */
    public boolean halfGate(int phase) {
        return phase < 2 || mob.tickCount < gateUntil;
    }

    /** Players who see its bar (to tell them things). */
    public List<ServerPlayer> barPlayers() {
        return bar == null ? List.of() : List.copyOf(bar.getPlayers());
    }

    public void removeBar() {
        if (bar != null) bar.removeAllPlayers();
    }

    // ------------------------------------------------------------------ who it fights

    /** A player it may go after: alive, not watching, not in creative, not downed. */
    public static boolean valid(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative() && !com.jo112ss.rpgmod.compat.Downed.is(p);
    }

    /** Its target, or the nearest valid player within 48 blocks when it has none (or it is gone). */
    public LivingEntity target(ServerLevel sl) {
        LivingEntity t = mob.getTarget();
        if (t instanceof Player p && !valid(p)) t = null;
        if (t != null && !t.isAlive()) t = null;
        if (t == null || t.distanceToSqr(mob) > TARGET_RANGE * TARGET_RANGE) {
            Player best = null;
            double bd = TARGET_RANGE * TARGET_RANGE;
            for (ServerPlayer p : sl.players()) {
                if (!valid(p)) continue;
                double d = p.distanceToSqr(mob);
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
            t = best;
            mob.setTarget(best);
        }
        return t;
    }

    /** Valid players within {@code range}. */
    public List<ServerPlayer> fighters(ServerLevel sl, double range) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : sl.players()) if (valid(p) && p.distanceToSqr(mob) <= range * range) out.add(p);
        return out;
    }

    /** A hit taken: whoever dealt it takes part in the fight. */
    public void hurtBy(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer p) participants.add(p.getUUID());
    }

    /** It died: the bar goes, everyone who fought it or stood near gets their reward. */
    public void died(ServerLevel sl) {
        removeBar();
        BossEvents.reward(sl, mob, participants, key);
    }

    // ------------------------------------------------------------------ helpers

    /** Lands on the ground under a point (a player in the air or on a ledge), up to 12 down. */
    public static Vec3 groundBelow(ServerLevel sl, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        for (int i = 0; i < 12; i++) {
            if (!sl.getBlockState(pos.below()).getCollisionShape(sl, pos.below()).isEmpty()) break;
            pos = pos.below();
        }
        return new Vec3(at.x, pos.getY(), at.z);
    }

    /** Throws {@code e} away from {@code from} (and up); sent to the player too, who moves on their own computer. */
    public static void fling(LivingEntity e, Vec3 from, double out, double up) {
        Vec3 d = e.position().subtract(from).multiply(1, 0, 1);
        d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
        e.setDeltaMovement(e.getDeltaMovement().add(d.scale(out)).add(0, up, 0));
        e.syncVelocity = true;
    }

    /** Turns the whole body to face along {@code dir}. */
    public void face(Vec3 dir) {
        mob.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        mob.yBodyRot = mob.yHeadRot = mob.getYRot();
    }

    /** Flat direction to {@code t} (or ahead if right on top of it). */
    public Vec3 toward(LivingEntity t) {
        Vec3 d = t.position().subtract(mob.position()).multiply(1, 0, 1);
        return d.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, mob.getYRot()) : d.normalize();
    }
}
