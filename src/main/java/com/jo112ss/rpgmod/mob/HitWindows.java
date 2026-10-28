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
 * just hit takes, for half a second, only what a bigger hit has over the last one - from anyone.
 * Skills already ignore that cooldown (their damage types are in {@code bypasses_cooldown}), but
 * every skill hit started it again, so plain attacks - a swing, an arrow from a bow - right after
 * any skill hit, the player's own or a party member's, were mostly thrown away; and so were a
 * party's plain attacks on one another.
 *
 * <p>Now, for the hits that do not ignore it, each player has a cooldown of their own on each
 * monster, one per kind of hit (plain attacks, explosions...): the vanilla rule (half a second, only
 * the part over the last hit) still applies to one player's own plain attacks, but nobody else's
 * hits and no skill hit block them any more. Players hurting players, and monsters hurting
 * anything, are left to vanilla.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class HitWindows {
    private HitWindows() {}

    /** Ticks after a fresh hit in which the next hit of the same kind only gets its excess through (vanilla: 10). */
    public static final int WINDOW = 10;

    /** A player's last fresh hit of one kind on one monster. */
    private record Hit(long tick, float amount) {}

    private static final Map<LivingEntity, Map<String, Hit>> HITS = new WeakHashMap<>();

    /** Dev test: blocked and cut hits are counted; debug logs each decision. */
    public static int testBlocked, testCut;
    public static boolean debug;

    /** Which cooldown a hit counts against: the attacker and the kind of hit. */
    private static String key(UUID attacker, DamageSource src) {
        boolean plain = src.is(DamageTypes.PLAYER_ATTACK) && src.getDirectEntity() == src.getEntity()
                || src.getDirectEntity() instanceof AbstractArrow arrow && arrow.getWeaponItem() != null;
        return attacker + "/" + (plain ? "plain" : src.typeHolder().getRegisteredName());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || victim.level().isClientSide()) return;
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof Player attacker)) return;
        if (src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN)) return; // skills, venom, gems: no cooldown at all, as before
        float amount = event.getAmount();
        if (amount <= 0) return;
        long now = victim.level().getGameTime();
        Map<String, Hit> hits = HITS.computeIfAbsent(victim, k -> new HashMap<>());
        hits.values().removeIf(h -> now - h.tick() >= WINDOW);
        String key = key(attacker.getUUID(), src);
        Hit last = hits.get(key);
        if (debug) RpgMod.LOGGER.info("[AUTOTEST] hit window {} amount {} last {} vanilla cooldown {} canceled {}", key.substring(key.indexOf('/') + 1), amount, last,
                victim.damageCooldownTime, event.isCanceled());
        // vanilla's shared cooldown never blocks it: the player's own decides below
        victim.damageCooldownTime = 0;
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
