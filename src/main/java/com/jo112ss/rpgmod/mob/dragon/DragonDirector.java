package com.jo112ss.rpgmod.mob.dragon;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import com.jo112ss.rpgmod.mob.MobConfig;
import com.jo112ss.rpgmod.mob.MobLevels;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Turns the Ender Dragon into a three-stage boss on top of its vanilla flight phases. Every new
 * attack is announced first (a mark on the ground, a ring, a roar) so it can be seen and dodged.
 *
 * <ul>
 *   <li>Stage 1 (above 60% health): vanilla, plus volleys of dragon fireballs at marked spots.</li>
 *   <li>Stage 2 (60-25%): bigger volleys, a wing gust that throws players off the portal while it
 *       perches, homing shulker orbs and a few phantoms called from the void.</li>
 *   <li>Stage 3 (below 25%, enraged): everything faster, charges straight at a player, and dragon
 *       breath raining from the sky on marked spots.</li>
 * </ul>
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class DragonDirector {
    private DragonDirector() {}

    private static final class State {
        int stage = 1;
        int volley = 160, gust = 100, orbs = 260, minions = 400, charge = 200, rain = 200;
        boolean announced;
    }

    private static final Map<EnderDragon, State> STATES = new WeakHashMap<>();
    private static final int PURPLE = 0xB040FF, RED = 0xFF3050;

    public static int stage(EnderDragon d) {
        State s = STATES.get(d);
        return s == null ? 1 : s.stage;
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof EnderDragon dragon) || !(dragon.level() instanceof ServerLevel sl) || !MobConfig.dragon()) return;
        if (dragon.isDeadOrDying() || dragon.isNoAi() || dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING) return;
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : sl.players()) {
            if (p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceTo(dragon) < 160) players.add(p);
        }
        State s = STATES.computeIfAbsent(dragon, k -> new State());
        if (!s.announced) {
            s.announced = true;
            updateBar(dragon, s.stage);
        }
        if (players.isEmpty()) return;
        float frac = dragon.getHealth() / dragon.getMaxHealth();
        int stage = frac > 0.6F ? 1 : frac > 0.25F ? 2 : 3;
        if (stage > s.stage) {
            s.stage = stage;
            transition(sl, dragon, players, stage);
        }
        RandomSource r = dragon.getRandom();
        boolean sitting = dragon.getPhaseManager().getCurrentPhase().isSitting();
        boolean charging = dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.CHARGING_PLAYER;
        s.volley--;
        s.gust--;
        s.orbs--;
        s.minions--;
        s.charge--;
        s.rain--;
        if (!sitting && !charging && s.volley <= 0) {
            fireballVolley(sl, dragon, players, s.stage);
            s.volley = (s.stage == 1 ? 220 : s.stage == 2 ? 170 : 120) + r.nextInt(60);
        }
        if (s.stage >= 2 && sitting && s.gust <= 0) {
            wingGust(sl, dragon, s.stage);
            s.gust = (s.stage == 2 ? 130 : 90) + r.nextInt(30);
        }
        if (s.stage >= 2 && !sitting && s.orbs <= 0) {
            voidOrbs(sl, dragon, players, s.stage == 2 ? 3 : 5);
            s.orbs = (s.stage == 2 ? 300 : 220) + r.nextInt(80);
        }
        if (s.stage >= 2 && s.minions <= 0) {
            summon(sl, dragon, players, s.stage == 2 ? 2 : 3);
            s.minions = (s.stage == 2 ? 640 : 480) + r.nextInt(120);
        }
        if (s.stage >= 3 && !sitting && !charging && s.charge <= 0) {
            chargeAt(sl, dragon, players.get(r.nextInt(players.size())));
            s.charge = 280 + r.nextInt(80);
        }
        if (s.stage >= 3 && s.rain <= 0) {
            breathRain(sl, dragon, players);
            s.rain = 240 + r.nextInt(60);
        }
        if (s.stage >= 3 && dragon.tickCount % 100 == 0) {
            ServerFx.of("dragon_enrage", dragon.position()).follow(dragon).a(100).send(sl, 200);
        }
    }

    /** Development test hook: performs one attack right away. */
    public static void testAttack(EnderDragon dragon, String what) {
        if (!(dragon.level() instanceof ServerLevel sl)) return;
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : sl.players()) if (p.isAlive() && !p.isSpectator()) players.add(p);
        if (players.isEmpty()) return;
        int st = Math.max(2, stage(dragon));
        switch (what) {
            case "volley" -> fireballVolley(sl, dragon, players, st);
            case "gust" -> wingGust(sl, dragon, st);
            case "orbs" -> voidOrbs(sl, dragon, players, 3);
            case "summon" -> summon(sl, dragon, players, 2);
            case "charge" -> chargeAt(sl, dragon, players.get(0));
            case "rain" -> breathRain(sl, dragon, players);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ stages

    private static void transition(ServerLevel sl, EnderDragon dragon, List<ServerPlayer> players, int stage) {
        updateBar(dragon, stage);
        sl.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, stage == 3 ? 0.6F : 0.8F);
        ServerFx.of("dragon_stage", dragon.position()).follow(dragon).b(stage).color(stage == 3 ? RED : PURPLE).send(sl, 200);
        Component title = Component.translatable("rpgmod.dragon.stage" + stage);
        Component sub = Component.translatable("rpgmod.dragon.stage" + stage + ".sub");
        for (ServerPlayer p : players) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            if (stage == 3 && p.distanceTo(dragon) < 20) { // the rage bursts out
                Vec3 away = p.position().subtract(dragon.position()).multiply(1, 0, 1).normalize();
                p.push(away.x * 1.6, 0.6, away.z * 1.6);
            }
        }
        RpgMod.LOGGER.info("Ender Dragon entered stage {} ({} / {} health)", stage, dragon.getHealth(), dragon.getMaxHealth());
    }

    private static void updateBar(EnderDragon dragon, int stage) {
        var fight = dragon.getDragonFight();
        if (fight == null || fight.dragonEvent == null) return;
        fight.dragonEvent.setName(Component.translatable("rpgmod.dragon.bar", MobLevels.level(dragon) > 0 ? MobLevels.level(dragon) : MobLevels.DRAGON_LEVEL,
                Component.translatable("rpgmod.dragon.stage" + stage)));
        fight.dragonEvent.setColor(stage == 1 ? BossEvent.BossBarColor.PINK : stage == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
    }

    // ------------------------------------------------------------------ attacks

    private static Vec3 headPos(EnderDragon d) {
        return new Vec3(d.head.getX(), d.head.getY(0.5), d.head.getZ());
    }

    private static Vec3 ground(ServerLevel sl, double x, double z, double fallbackY) {
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        return new Vec3(x, y > sl.getMinY() ? y : fallbackY, z);
    }

    /** Marks spots around the players, then a fireball for each spot a second later. */
    private static void fireballVolley(ServerLevel sl, EnderDragon dragon, List<ServerPlayer> players, int stage) {
        RandomSource r = dragon.getRandom();
        int count = stage == 1 ? 3 : stage == 2 ? 4 : 6;
        List<Vec3> spots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ServerPlayer p = players.get(r.nextInt(players.size()));
            // the first spot leads the player's movement, the rest scatter around them
            Vec3 move = p.getKnownMovement().multiply(1, 0, 1).scale(20);
            double ox = i == 0 ? move.x : (r.nextDouble() - 0.5) * 10, oz = i == 0 ? move.z : (r.nextDouble() - 0.5) * 10;
            spots.add(ground(sl, p.getX() + ox, p.getZ() + oz, p.getY()));
        }
        int warn = 24;
        for (Vec3 at : spots) ServerFx.of("dragon_mark", at).a(3).b(warn + 30).color(PURPLE).send(sl, 200);
        sl.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0F, 1.2F);
        SkillTasks.later(warn, () -> {
            if (!dragon.isAlive()) return;
            Vec3 from = headPos(dragon);
            for (int i = 0; i < spots.size(); i++) {
                Vec3 at = spots.get(i);
                int delay = i * 4;
                SkillTasks.later(1 + delay, () -> {
                    if (!dragon.isAlive()) return;
                    Vec3 dir = at.subtract(from).normalize();
                    DragonFireball fb = new DragonFireball(sl, dragon, dir);
                    fb.snapTo(from.x + dir.x * 2, from.y + dir.y * 2, from.z + dir.z * 2, 0F, 0F);
                    sl.addFreshEntity(fb);
                    sl.levelEvent(null, 1017, dragon.blockPosition(), 0);
                });
            }
        });
    }

    /** While perched: a ring warns, then a beat of the wings throws everyone near off their feet. */
    private static void wingGust(ServerLevel sl, EnderDragon dragon, int stage) {
        double radius = stage == 2 ? 10 : 13;
        int warn = stage == 2 ? 28 : 20;
        Vec3 at = ground(sl, dragon.getX(), dragon.getZ(), dragon.getY());
        ServerFx.of("dragon_gust", at).a(radius).b(warn).color(PURPLE).send(sl, 200);
        sl.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0F, 0.6F);
        SkillTasks.later(warn, () -> {
            if (!dragon.isAlive()) return;
            sl.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 6.0F, 0.5F);
            for (ServerPlayer p : sl.players()) {
                if (p.isCreative() || p.isSpectator() || p.position().distanceTo(at) > radius) continue;
                Vec3 away = p.position().subtract(at).multiply(1, 0, 1);
                away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                p.push(away.x * 2.0, 0.75, away.z * 2.0);
                p.hurtServer(sl, sl.damageSources().mobAttack(dragon), 4.0F);
            }
        });
    }

    /** Homing orbs (shulker bullets) that lift whoever they hit into the air. */
    private static void voidOrbs(ServerLevel sl, EnderDragon dragon, List<ServerPlayer> players, int count) {
        RandomSource r = dragon.getRandom();
        for (int i = 0; i < count; i++) {
            ServerPlayer target = players.get(r.nextInt(players.size()));
            int delay = i * 6;
            SkillTasks.later(1 + delay, () -> {
                if (!dragon.isAlive() || !target.isAlive()) return;
                ShulkerBullet b = new ShulkerBullet(sl, dragon, target, Direction.Axis.Y);
                // start below the body, clear of the dragon's own hitboxes
                b.snapTo(dragon.getX() + (r.nextDouble() - 0.5) * 4, dragon.getY() - 4, dragon.getZ() + (r.nextDouble() - 0.5) * 4, 0F, 0F);
                sl.addFreshEntity(b);
                ServerFx.of("dragon_summon", b.position()).a(0.8).color(PURPLE).send(sl, 200);
            });
        }
        sl.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.HOSTILE, 4.0F, 0.5F);
    }

    /** Phantoms called out of the void near the players. */
    private static void summon(ServerLevel sl, EnderDragon dragon, List<ServerPlayer> players, int count) {
        RandomSource r = dragon.getRandom();
        for (int i = 0; i < count; i++) {
            ServerPlayer target = players.get(r.nextInt(players.size()));
            Phantom ph = EntityTypes.PHANTOM.create(sl, EntitySpawnReason.MOB_SUMMONED);
            if (ph == null) continue;
            double a = r.nextDouble() * Math.PI * 2;
            Vec3 at = target.position().add(Math.cos(a) * 12, 10 + r.nextInt(6), Math.sin(a) * 12);
            ph.snapTo(at.x, at.y, at.z, r.nextFloat() * 360, 0);
            ph.setTarget(target);
            sl.addFreshEntity(ph);
            ServerFx.of("dragon_summon", at).a(2).color(PURPLE).send(sl, 200);
        }
        sl.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.HOSTILE, 5.0F, 0.5F);
    }

    /** Enraged: a growl and a red line to the target, then a straight dive at them. */
    private static void chargeAt(ServerLevel sl, EnderDragon dragon, ServerPlayer target) {
        ServerFx.of("dragon_charge", headPos(dragon)).follow(dragon).vec(target.position()).b(target.getId()).a(20).send(sl, 200);
        sl.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 0.5F);
        SkillTasks.later(20, () -> {
            if (!dragon.isAlive() || !target.isAlive()) return;
            dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
            dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(target.position().add(0, 1, 0));
        });
    }

    /** Enraged: marks rain down around the players, then dragon breath falls from the sky onto them. */
    private static void breathRain(ServerLevel sl, EnderDragon dragon, List<ServerPlayer> players) {
        RandomSource r = dragon.getRandom();
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : players) {
            spots.add(ground(sl, p.getX(), p.getZ(), p.getY()));
            for (int i = 0; i < 4; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = 3 + r.nextDouble() * 6;
                spots.add(ground(sl, p.getX() + Math.cos(a) * d, p.getZ() + Math.sin(a) * d, p.getY()));
            }
        }
        int warn = 36;
        for (Vec3 at : spots) ServerFx.of("dragon_mark", at).a(2.5).b(warn + 14).color(RED).send(sl, 200);
        sl.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 1.4F);
        for (int i = 0; i < spots.size(); i++) {
            Vec3 at = spots.get(i);
            SkillTasks.later(warn - 14 + i % 4 * 3, () -> {
                if (!dragon.isAlive()) return;
                DragonFireball fb = new DragonFireball(sl, dragon, new Vec3(0, -1, 0));
                fb.snapTo(at.x, at.y + 18, at.z, 0F, 0F);
                fb.setDeltaMovement(0, -1.2, 0);
                sl.addFreshEntity(fb);
            });
        }
    }
}
