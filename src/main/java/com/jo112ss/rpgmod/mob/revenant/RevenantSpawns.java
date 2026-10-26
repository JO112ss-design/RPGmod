package com.jo112ss.rpgmod.mob.revenant;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.mob.MobConfig;
import com.jo112ss.rpgmod.mob.revenant.Revenant.Role;
import com.jo112ss.rpgmod.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Where the fallen adventurers come from: some of the monsters that would spawn naturally in
 * overworld caves (below y 40, out of the sky's sight) come as a revenant instead. Mostly alone;
 * sometimes two, an attacker with a priest; now and then a whole party of four. The cave's monster
 * cap counts them like any other monster, so caves do not get more crowded.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class RevenantSpawns {
    private RevenantSpawns() {}

    public static final int MAX_Y = 40;
    /** Chances of a pair (attacker + priest) and of a party of four; the rest come alone. */
    public static final double DUO = 0.18, PARTY = 0.08;

    /** Dev test: replaces the configured chance. */
    public static Double forceChance;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onSpawn(FinalizeSpawnEvent event) {
        if (event.isSpawnCancelled() || event.getSpawnType() != EntitySpawnReason.NATURAL) return;
        var mob = event.getEntity();
        if (mob instanceof Revenant || !(mob instanceof Enemy) || mob instanceof net.minecraft.world.entity.monster.cubemob.Slime) return;
        ServerLevel level = event.getLevel().getLevel();
        if (level.dimension() != Level.OVERWORLD) return;
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (pos.getY() >= MAX_Y || level.canSeeSky(pos)) return;
        if (level.getRandom().nextDouble() >= (forceChance != null ? forceChance : MobConfig.revenantChance())) return;
        event.setSpawnCancelled(true);
        Vec3 at = new Vec3(event.getX(), event.getY(), event.getZ());
        SkillTasks.later(1, () -> spawnGroup(level, at, roll(level.getRandom())));
    }

    /** Who comes: one attacker alone, an attacker with a priest, or a party of four. */
    public static List<Role> roll(RandomSource r) {
        double x = r.nextDouble();
        List<Role> out = new ArrayList<>();
        if (x < PARTY) {
            out.add(Role.WARRIOR);
            out.add(Role.MAGE);
            out.add(r.nextBoolean() ? Role.ARCHER : Role.THIEF);
            out.add(Role.PRIEST);
        } else {
            out.add(attacker(r));
            if (x < PARTY + DUO) out.add(Role.PRIEST);
        }
        return out;
    }

    private static Role attacker(RandomSource r) {
        double x = r.nextDouble();
        return x < 0.3 ? Role.WARRIOR : x < 0.52 ? Role.MAGE : x < 0.76 ? Role.ARCHER : Role.THIEF;
    }

    /** Spawns the group around {@code at} (the spot a monster was allowed to spawn on); returns them. */
    public static List<Revenant> spawnGroup(ServerLevel level, Vec3 at, List<Role> roles) {
        List<Revenant> out = new ArrayList<>();
        var type = ModRegistries.REVENANT.get();
        for (int i = 0; i < roles.size(); i++) {
            Revenant r = type.create(level, EntitySpawnReason.NATURAL);
            if (r == null) continue;
            Vec3 p = i == 0 ? at : spot(level, at, type, level.getRandom());
            r.snapTo(p.x, p.y, p.z, level.getRandom().nextFloat() * 360, 0);
            r.setRole(roles.get(i));
            r.finalizeSpawn(level, level.getCurrentDifficultyAt(r.blockPosition()), EntitySpawnReason.NATURAL, null);
            level.addFreshEntity(r);
            out.add(r);
        }
        return out;
    }

    /** A free spot near {@code at} with ground under it (or {@code at} itself). */
    private static Vec3 spot(ServerLevel level, Vec3 at, net.minecraft.world.entity.EntityType<?> type, RandomSource rnd) {
        for (int t = 0; t < 10; t++) {
            BlockPos p = BlockPos.containing(at.x + rnd.nextInt(5) - 2, at.y, at.z + rnd.nextInt(5) - 2);
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos q = p.above(dy);
                if (!level.getBlockState(q.below()).isFaceSturdy(level, q.below(), net.minecraft.core.Direction.UP)) continue;
                Vec3 v = new Vec3(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
                if (level.noCollision(type.getSpawnAABB(v.x, v.y, v.z)) && level.getFluidState(q).isEmpty()) return v;
            }
        }
        return at;
    }
}
