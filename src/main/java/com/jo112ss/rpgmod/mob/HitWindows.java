package com.jo112ss.rpgmod.mob;

import com.jo112ss.rpgmod.RpgMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Players' hits on monsters do not share the monster's hit cooldown. In vanilla a monster that was
 * just hit takes, for half a second, only what a bigger hit has over the last one - from anyone -
 * so a party of six beating one boss threw most of its damage away, and even one player's own
 * plain attacks ate the skill hits right after them.
 *
 * <p>Now each player has a cooldown of their own on each monster, and plain attacks (a swing, an
 * arrow from a bow) and skills keep separate ones: the vanilla rule (half a second, only the part
 * over the last hit gets through) still applies inside one player's skills - the multi-hit skills
 * were made around it - but nobody else's hits, and no plain attack, block them any more.
 * Damage over time of a kind of its own (venom...) keeps its own cooldown too. Players hurting
 * players, and monsters hurting anything, are left to vanilla.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class HitWindows {
    private HitWindows() {}

    /** Ticks after a fresh hit in which the next hit of the same kind only gets its excess through (vanilla: 10). */
    public static final int WINDOW = 10;

    /** A player's last fresh hit of one kind on one monster. */
    private record Hit(long tick, float amount) {}

    private static final Map<LivingEntity, Map<String, Hit>> HITS = new WeakHashMap<>();

    /** Dev test: blocked and cut hits are counted. */
    public static int testBlocked, testCut;

    /** Which cooldown a hit counts against: the attacker and the kind of hit. */
    private static String key(UUID attacker, DamageSource src) {
        boolean plain = src.is(DamageTypes.PLAYER_ATTACK) && src.getDirectEntity() == src.getEntity()
                || src.getDirectEntity() instanceof AbstractArrow arrow && arrow.getWeaponItem() != null;
        String kind = plain ? "plain" : src.typeHolder().getRegisteredName();
        if (kind.equals("rpgmod:skill") || kind.equals("rpgmod:skill_magic")) kind = "skill";
        return attacker + "/" + kind;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || victim.level().isClientSide()) return;
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof Player attacker)) return;
        float amount = event.getAmount();
        if (amount <= 0) return;
        long now = victim.level().getGameTime();
        Map<String, Hit> hits = HITS.computeIfAbsent(victim, k -> new HashMap<>());
        hits.values().removeIf(h -> now - h.tick() >= WINDOW);
        String key = key(attacker.getUUID(), src);
        Hit last = hits.get(key);
        // vanilla's shared cooldown never blocks it: the player's own decides below
        ((com.jo112ss.rpgmod.mixin.EntityInvulnerableAccessor) victim).rpgmod$setInvulnerableTime(0);
        if (last == null) {
            hits.put(key, new Hit(now, amount));
            return;
        }
        // inside this player's own cooldown for this kind of hit: only what it has over the last one
        if (amount <= last.amount()) {
            testBlocked++;
            event.setCanceled(true);
            return;
        }
        testCut++;
        event.setAmount(amount - last.amount());
        hits.put(key, new Hit(last.tick(), amount));
    }
}
