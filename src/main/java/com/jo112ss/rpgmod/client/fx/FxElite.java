package com.jo112ss.rpgmod.client.fx;

import static com.jo112ss.rpgmod.client.fx.SkillFx.*;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.client.fx.Fx.S;
import com.jo112ss.rpgmod.client.fx.SkillFx.P;
import com.jo112ss.rpgmod.mob.Elite;
import com.jo112ss.rpgmod.mob.Elite.Trait;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Elite monsters on the client: a lasting aura in the colours of their traits (so you can read them
 * before the name is in view), and the trait effects (drain, regeneration, the guardian's warning
 * ring and shields, a split).
 */
@EventBusSubscriber(modid = RpgMod.MODID, value = Dist.CLIENT)
public final class FxElite {
    private FxElite() {}

    public static boolean play(P p) {
        switch (p.fx()) {
            case "elite_drain" -> drain(p);
            case "elite_regen" -> regen(p);
            case "elite_guard_warn" -> guardWarn(p);
            case "elite_guard" -> guard(p);
            case "elite_split" -> split(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static final double RANGE = 40;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        long t = mc.level.getGameTime();
        if (t % 3 != 0) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Mob m) || !m.isAlive() || m.isInvisible() || e.distanceToSqr(mc.player) > RANGE * RANGE) continue;
            int mask = Elite.traits(e);
            if (mask == 0) continue;
            aura(m, Elite.list(mask), t);
        }
    }

    /** A ring at the feet in the first trait's colour, and motes of each trait rising round the body. */
    private static void aura(Mob m, List<Trait> traits, long t) {
        Vec3 f = m.position();
        float w = m.getBbWidth();
        Trait main = traits.get(0);
        if ((t + m.getId()) % 15 == 0) {
            Fx.p(S.CIRCLE_HEX, f.x, f.y + 0.05, f.z).flat().color(FxUlt.light(main.color), main.color).size(w + 1.0F).alpha(0.75F, 0).angle(t * 0.05F).life(16).follow(m).spawn();
        }
        for (Trait tr : traits) {
            double a = Fx.r(0, Math.PI * 2), r = w * 0.6 + 0.2;
            Vec3 v = f.add(Math.cos(a) * r, Fx.r(0.1, m.getBbHeight()), Math.sin(a) * r);
            switch (tr) {
                case BLAZE -> FxUlt.flameAt(new Vec3(v.x, f.y + Fx.r(0, 0.3), v.z), 0xFFE0A0, tr.color, 0.4F, 10, null);
                case FROST -> Fx.p(S.SHARD, v).vel(0, -0.02, 0).color(0xFFFFFF, tr.color).size(0.18F, 0).randomAngle().life(12).spawn();
                case IRON -> Fx.p(S.HEX, v).color(0xFFFFFF, tr.color).size(0.25F, 0).alpha(0.8F, 0).life(10).follow(m).spawn();
                case SWIFT -> Fx.p(S.STREAK, v).fixed(Fx.axial(v, m.getDeltaMovement().lengthSqr() > 1e-4 ? m.getDeltaMovement() : new Vec3(0, 1, 0)))
                        .color(0xFFFFFF, tr.color).size(0.35F, 0.1F).life(6).spawn();
                case MIGHTY -> Fx.p(S.GLOW, v).vel(0, 0.04, 0).color(0xFF9070, tr.color).size(0.22F, 0).life(12).spawn();
                case REGEN -> Fx.p(S.CROSS, v).vel(0, 0.03, 0).color(0xFFFFFF, tr.color).size(0.18F, 0).life(14).spawn();
                case VAMPIRE -> Fx.p(S.GLOW, v).vel(0, -0.03, 0).color(0xFF4040, tr.color).size(0.16F, 0).life(14).spawn();
                case SPLIT -> Fx.p(S.TWINKLE, v).color(0xFFFFFF, tr.color).size(0.25F, 0).life(8).spawn();
                case COMMANDER -> {
                    Fx.p(S.CREST, m.position().add(0, m.getBbHeight() + 0.9, 0)).color(0xFFFFFF, tr.color).size(0.35F).alpha(0.9F, 0).life(4).follow(m).spawn();
                    if ((t + m.getId()) % 30 == 0) commandLinks(m, tr.color);
                }
                case WARDED -> Fx.p(Fx.rune((int) (t / 3)), v).color(0xFFFFFF, tr.color).size(0.22F, 0).life(12).follow(m).spawn();
                case STEADFAST -> Fx.p(S.GLOW, new Vec3(v.x, f.y + 0.1, v.z)).color(0xE0D0A0, tr.color).size(0.25F, 0).life(10).spawn();
                case GUARDIAN -> Fx.p(S.RING, v).color(0xFFFFFF, tr.color).size(0.2F, 0.4F).alpha(0.8F, 0).life(8).spawn();
            }
        }
    }

    /** Thin golden lines from a commander to the monsters it leads. */
    private static void commandLinks(Mob m, int c) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 from = m.position().add(0, m.getBbHeight() * 0.7, 0);
        for (Mob o : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(Elite.COMMAND_RANGE), o -> o != m && o.isAlive() && o instanceof net.minecraft.world.entity.monster.Enemy
                && o.distanceTo(m) <= Elite.COMMAND_RANGE)) {
            Vec3 to = o.position().add(0, o.getBbHeight() * 0.6, 0);
            line(from, to, 0.5, (v, k) -> Fx.p(S.GLOW, v).color(0xFFFFFF, c).size(0.1F, 0).alpha(0.7F, 0).life(8).delay((int) (k * 4)).spawn());
            Fx.p(S.CREST, to.add(0, 0.6, 0)).color(0xFFFFFF, c).size(0.25F, 0).life(12).delay(4).follow(o).spawn();
        }
    }

    private static void drain(P p) {
        Entity e = p.e();
        Vec3 from = p.pos();
        if (e == null) return;
        Vec3 to = e.position().add(0, e.getBbHeight() * 0.6, 0);
        line(from, to, 0.3, (v, k) -> Fx.p(S.GLOW, v).color(0xFF6060, Trait.VAMPIRE.color).size(0.18F, 0).life(8).delay((int) (k * 5)).spawn());
    }

    private static void regen(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        for (int i = 0; i < 3; i++) {
            Fx.p(S.CROSS, o.add(Fx.r(-0.4, 0.4), Fx.r(0.3, 1.6), Fx.r(-0.4, 0.4))).vel(0, 0.04, 0).color(0xFFFFFF, Trait.REGEN.color).size(0.25F, 0).life(16).follow(e).spawn();
        }
    }

    /** The guardian's warning: a blue circle filling in over the time it takes. */
    private static void guardWarn(P p) {
        Entity e = p.e();
        Vec3 o = feet(e, p.pos());
        float r = Math.max(2, p.a());
        int life = Math.max(10, (int) p.b());
        int c = Trait.GUARDIAN.color;
        ground(S.RING, o, r, c, life).color(FxUlt.light(c), c).alpha(0.9F, 0.9F).follow(e).spawn();
        ground(S.CIRCLE_HOLY, o, 0.5F, c, life).color(FxUlt.light(c), c).size(0.5F, r).alpha(0.4F, 0.8F).spin(0.05F).follow(e).spawn();
        Fx.p(S.CREST, o.add(0, (e != null ? e.getBbHeight() : 2) + 1.0, 0)).color(0xFFFFFF, c).size(0.3F, 0.8F).alpha(1, 0.6F).life(life).follow(e).spawn();
    }

    private static void guard(P p) {
        Vec3 o = feet(p.e(), p.pos());
        int c = Trait.GUARDIAN.color;
        flash(o.add(0, 1, 0), c, 2, 6);
        for (Vec3 v : p.pts()) {
            Fx.p(S.HEX, v).color(0xFFFFFF, c).size(0.4F, 1.4F).easeOut().alpha(0.9F, 0).life(12).spawn();
            line(o.add(0, 1, 0), v, 0.4, (a, k) -> Fx.p(S.GLOW, a).color(0xFFFFFF, c).size(0.14F, 0).life(8).delay((int) (k * 4)).spawn());
        }
    }

    private static void split(P p) {
        Vec3 o = p.pos();
        int c = Trait.SPLIT.color;
        flash(o.add(0, 1, 0), c, 1.8F, 6);
        burst(o.add(0, 0.8, 0), 20, 0.4, S.TWINKLE, 0xFFFFFF, c, 0.3F, 14, 0.1F);
        for (int s = -1; s <= 1; s += 2) Fx.p(S.SLASH, o.add(0, 1, 0)).color(0xFFFFFF, c).size(1.2F).angle(1.57F * s).alpha(1, 0).life(6).spawn();
    }

    static boolean isElite(LivingEntity e) {
        return Elite.is(e);
    }
}
