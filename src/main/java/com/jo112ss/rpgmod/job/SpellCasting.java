package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.JobStat;
import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.job.fx.ServerFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * The spellmaster's second hand: a spell charged by holding left click (the right-click spell keeps
 * using the staff as before), and fusion - when both hands hold a charged spell of two different
 * elements, releasing either one fires a fusion spell instead of the two.
 */
public final class SpellCasting {
    private SpellCasting() {}

    private record Left(String spell, long start, int need) {}

    private static final Map<UUID, Left> LEFT = new HashMap<>();

    /** Fusion elements of the spells. */
    private enum Element { FIRE, ICE, THUNDER }

    private static Element element(String spell) {
        return switch (spell) {
            case "flame_burst", "inferno" -> Element.FIRE;
            case "frost_nova", "blizzard", "absolute_zero" -> Element.ICE;
            case "thunder", "thunderstorm" -> Element.THUNDER;
            default -> null;
        };
    }

    /**
     * What a spell is worth in a fusion (its whole damage, not the per-hit ratio): the fusion deals
     * 1.2 x the sum of its two parts.
     */
    private static double worth(String spell) {
        return switch (spell) {
            case "flame_burst" -> 2.1;
            case "inferno" -> 4.0;
            case "frost_nova" -> 1.5;
            case "blizzard" -> 4.2;
            case "absolute_zero" -> 3.5;
            case "thunder" -> 3.8;
            case "thunderstorm" -> 5.0;
            default -> 0;
        };
    }

    public static final double FUSION_MULT = 1.2;

    /** The fusion of two spells, or null if they do not fuse. */
    public static String fusionOf(String a, String b) {
        Element x = element(a), y = element(b);
        if (x == null || y == null || x == y) return null;
        if (x != Element.THUNDER && y != Element.THUNDER) return "steam_burst";   // fire + ice
        if (x != Element.ICE && y != Element.ICE) return "plasma_ray";           // fire + thunder
        return "frost_tempest";                                                   // ice + thunder
    }

    private static ItemStack staff(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        return ModItems.typeOf(s.getItem()) == WeaponType.STAFF ? s : ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ left click

    /** Left click pressed: start charging the left-hand spell. */
    public static void leftPress(ServerPlayer p) {
        ItemStack staff = staff(p);
        if (staff.isEmpty()) return;
        SkillDef def = JobManager.leftSpell(p, staff);
        if (def == null || !JobManager.canCharge(p, def, true)) return;
        long now = p.level().getGameTime();
        int need = JobManager.castTicks(p, def);
        LEFT.put(p.getUUID(), new Left(def.id(), now, need));
        ServerLevel level = (ServerLevel) p.level();
        ServerFx.of("spell_charge", p.position()).follow(p).b(need).color(JobManager.spellColor(def.id())).send(level);
        UUID id = p.getUUID();
        SkillTasks.run(age -> {
            Left l = LEFT.get(id);
            if (l == null || l.start != now || !p.isAlive() || staff(p).isEmpty()) {
                if (l != null && l.start == now) LEFT.remove(id);
                return true;
            }
            if (age == need) level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.9F);
            return age > need + 1200;
        });
    }

    /** Left click released: fire the left-hand spell if it was charged long enough (or fuse it). */
    public static void leftRelease(ServerPlayer p) {
        Left l = LEFT.remove(p.getUUID());
        if (l == null) return;
        SkillDef def = JobDefs.skill(l.spell);
        if (def == null) return;
        long held = p.level().getGameTime() - l.start;
        if (held < l.need) {
            p.sendSystemMessage(Component.translatable("rpgmod.job.fizzle").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (tryFusion(p, def, readyRight(p))) {
            p.stopUsingItem();
            return;
        }
        boolean ok = JobManager.cast(p, JobManager.data(p), def);
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) RpgMod.LOGGER.info("[AUTOTEST] left cast {} held {} of {} -> {}", def.id(), held, l.need, ok);
    }

    /** The left-hand spell if it is fully charged right now. */
    static SkillDef readyLeft(ServerPlayer p) {
        Left l = LEFT.get(p.getUUID());
        if (l == null || p.level().getGameTime() - l.start < l.need) return null;
        return JobDefs.skill(l.spell);
    }

    /** The right-hand (staff use) spell if it is fully charged right now. */
    private static SkillDef readyRight(ServerPlayer p) {
        if (!p.isUsingItem() || !(p.getUseItem().getItem() instanceof com.jo112ss.rpgmod.item.RpgStaffItem)) return null;
        SkillDef def = JobManager.selectedSpell(p, p.getUseItem());
        return def != null && p.getTicksUsingItem() >= JobManager.castTicks(p, def) ? def : null;
    }

    static void clearLeft(ServerPlayer p) {
        LEFT.remove(p.getUUID());
    }

    public static void forget(UUID id) {
        LEFT.remove(id);
    }

    // ------------------------------------------------------------------ fusion

    /**
     * Fuses two charged spells if their elements combine: one fusion spell instead of the two, for
     * both spells' mana, and both go on cooldown. False (nothing spent) if they do not fuse.
     */
    public static boolean tryFusion(ServerPlayer p, SkillDef a, SkillDef b) {
        if (a == null || b == null || a.id().equals(b.id())) return false;
        String id = fusionOf(a.id(), b.id());
        SkillDef fusion = JobDefs.skill(id);
        if (fusion == null) return false;
        JobData d = JobManager.data(p);
        long now = p.level().getGameTime();
        if (d.cooldownEnd(a.id()) > now || d.cooldownEnd(b.id()) > now) return false;
        double cost = JobManager.manaCost(p, a) + JobManager.manaCost(p, b);
        if (d.mana() < cost) {
            p.sendSystemMessage(Component.translatable("rpgmod.job.no_mana").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        double ratio = FUSION_MULT * (worth(a.id()) + worth(b.id()));
        SkillDef use = fusion.withEffect(fusion.effect(), ratio, fusion.radius(), fusion.duration(), fusion.effects());
        double power = JobManager.powerMultiplier(p, fusion);
        if (!SkillExecutor.execute(p, use, power)) return false;
        double cdr = JobStats.compute(d).get(JobStat.COOLDOWN_REDUCTION) / 100.0;
        d.setMana(d.mana() - cost);
        d.pruneCooldowns(now);
        d.setCooldown(a.id(), now + Math.round(a.cooldown() * 20 * (1 - cdr)));
        d.setCooldown(b.id(), now + Math.round(b.cooldown() * 20 * (1 - cdr)));
        JobManager.sync(p);
        p.sendSystemMessage(Component.translatable("rpgmod.fusion.cast", Component.translatable("rpgmod.skill." + id))
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 0.6F);
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) {
            RpgMod.LOGGER.info("[AUTOTEST] fusion {} + {} -> {} ratio {} x power {} = {}, mana {}", a.id(), b.id(), id,
                    String.format("%.2f", ratio), String.format("%.1f", power), String.format("%.1f", ratio * power), String.format("%.0f", cost));
        }
        return true;
    }
}
