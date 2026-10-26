package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.job.SkillExecutor.Ctx;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** The bard's own attack: a sound wave from the lute (right-click, {@link #COST} MP a wave). */
public final class BardAttack {
    private BardAttack() {}

    public static final double RATIO = 0.8, RANGE = 20, SPEED = 1.6;
    public static final int GAP = 12;
    /** MP a wave costs (the song jobs keep 100 MP, so the wave cannot be played without end). */
    public static final double COST = 2;

    public static void wave(ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        JobData data = JobManager.data(p);
        if (data.mana() < COST) {
            p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("rpgmod.job.no_mana").withStyle(net.minecraft.ChatFormatting.RED), true);
            p.getCooldowns().addCooldown(p.getMainHandItem(), GAP);
            return;
        }
        data.setMana(data.mana() - COST);
        JobManager.sync(p);
        // the context only carries the player and the damage type here: the ratio is set below
        Ctx c = new Ctx(p, level, JobDefs.skill("finale"), JobManager.magicAttack(p), 1.0);
        float damage = (float) (RATIO * JobManager.magicAttack(p));
        LivingEntity target = SkillExecutor.lookTarget(c, RANGE, 10);
        Vec3 start = c.hand();
        Vec3 dir = target != null ? target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(start).normalize() : c.look();
        ServerFx.of("n_wave", start).vec(dir.scale(SPEED)).a(RANGE).color(Bard.COLOR).follow(target).send(level);
        SkillExecutor.sound(c, SoundEvents.NOTE_BLOCK_HARP, 1.0F + p.getRandom().nextInt(3) * 0.25F);
        SkillExecutor.projectile(c, start, dir, SPEED, RANGE, target, 0.2, (e, at, d) -> {
            SkillExecutor.hurt(c, e, damage, SkillExecutor.magic(c));
            SkillExecutor.push(e, d, 0.3, 0.1);
            ServerFx.of("x_hit", at).vec(d).a(0.7).color(Bard.COLOR).send(level);
        }, (at, d, wall) -> { });
        p.getCooldowns().addCooldown(p.getMainHandItem(), GAP);
        JobManager.markCombat(p);
    }
}
