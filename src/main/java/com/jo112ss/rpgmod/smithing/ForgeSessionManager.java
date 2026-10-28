package com.jo112ss.rpgmod.smithing;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.network.ForgeActionPayload;
import com.jo112ss.rpgmod.network.ForgeSyncPayload;
import com.jo112ss.rpgmod.registry.ModRegistries;
import com.jo112ss.rpgmod.smithing.core.ForgeState;
import com.jo112ss.rpgmod.smithing.core.OptionRoller;
import com.jo112ss.rpgmod.smithing.core.OptionType;
import com.jo112ss.rpgmod.smithing.core.Quality;
import com.jo112ss.rpgmod.smithing.core.RolledOption;
import com.jo112ss.rpgmod.smithing.core.SmithingRules;
import com.jo112ss.rpgmod.smithing.core.TargetGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side owner of every forging session. One session per player, keyed by UUID, so several
 * players can forge at the same time without sharing state. All methods run on the server thread.
 *
 * <p>Safety rules:
 * <ul>
 *   <li>Actions are accepted only while the player's open menu is the one the session belongs to.</li>
 *   <li>Every bar stop carries a token; stale or repeated tokens are ignored by {@link ForgeState}.</li>
 *   <li>Materials are moved into the session's escrow exactly once when forging starts, and the
 *       session is completed exactly once ({@code delivered} flag), so nothing is duplicated.</li>
 *   <li>Closing the GUI, logging out or walking away finishes the forging with the current work
 *       (or refunds the materials if the heat was not set yet). The item goes to the inventory, or
 *       is dropped if it is full, before the player data is saved.</li>
 * </ul>
 */
public final class ForgeSessionManager {
    private ForgeSessionManager() {}

    private static final long MIN_ACTION_GAP_MILLIS = 40;
    private static final int MAX_ACTIONS_PER_SECOND = 20;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Bar tokens of each new session start from here, so they never repeat between sessions. */
    private static int nextTokenBase = new Random().nextInt(1 << 20);

    private static final class Session {
        final ForgeMenu menu;
        ForgeableWeapon weapon;
        ForgeState state;
        final List<ItemStack> escrow = new ArrayList<>();
        boolean delivered;
        Quality quality;
        ItemStack result = ItemStack.EMPTY;
        long lastActionMillis;
        long windowStartMillis;
        int actionsInWindow;

        Session(ForgeMenu menu) {
            this.menu = menu;
        }
    }

    // ------------------------------------------------------------------ menu lifecycle

    public static void onMenuOpened(ServerPlayer player, ForgeMenu menu) {
        Session old = SESSIONS.remove(player.getUUID());
        if (old != null) completeIfRunning(player, old);
        SESSIONS.put(player.getUUID(), new Session(menu));
        send(player, ForgeSyncPayload.select());
    }

    public static void onMenuClosed(ServerPlayer player, ForgeMenu menu) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.menu != menu) return;
        SESSIONS.remove(player.getUUID());
        completeIfRunning(player, s);
    }

    /** Logout: the menu may not get a close callback before the player is saved, so settle here. */
    public static void onLogout(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) completeIfRunning(player, s);
    }

    /** Server stopping: settle everything still open. */
    public static void onServerStopping(net.minecraft.server.MinecraftServer server) {
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            Session s = SESSIONS.remove(id);
            if (p != null && s != null) completeIfRunning(p, s);
        }
        SESSIONS.clear();
    }

    private static void completeIfRunning(ServerPlayer player, Session s) {
        if (s.state != null && !s.delivered) {
            s.state.forceEnd();
            complete(player, s);
        }
    }

    // ------------------------------------------------------------------ input

    public static void handleAction(ServerPlayer player, ForgeActionPayload p) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || player.containerMenu != s.menu || !s.menu.stillValid(player)) return;

        long now = System.currentTimeMillis();
        if (!rateLimit(s, now)) return;

        SmithingRules rules = SmithingRules.current();
        boolean changed = false;
        switch (p.action()) {
            case ForgeActionPayload.START -> changed = start(player, s, p.arg(), rules, now);
            case ForgeActionPayload.STOP_HEAT -> {
                if (s.state != null) {
                    changed = s.state.stopHeating(p.token(), p.elapsedMillis(), now);
                    if (changed) player.level().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5F, 1.2F);
                }
            }
            case ForgeActionPayload.SELECT_PART -> changed = s.state != null && s.state.selectPart(p.arg(), now);
            case ForgeActionPayload.STRIKE -> {
                if (s.state != null && s.state.strike(p.token(), p.arg(), p.elapsedMillis(), now)) {
                    changed = true;
                    float pitch = switch (s.state.lastJudgement()) {
                        case PERFECT -> 1.25F;
                        case GOOD -> 1.0F;
                        case BAD -> 0.8F;
                        case MISS -> 0.6F;
                    };
                    player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, pitch);
                }
            }
            case ForgeActionPayload.COOL -> {
                if (s.state != null && s.state.cool(now)) {
                    changed = true;
                    player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.4F);
                }
            }
            case ForgeActionPayload.FINISH -> changed = s.state != null && s.state.finish();
            case ForgeActionPayload.RESET -> {
                if (s.state == null || s.state.phase() == ForgeState.Phase.FINISHED) {
                    s.state = null;
                    s.weapon = null;
                    s.delivered = false;
                    s.result = ItemStack.EMPTY;
                    s.quality = null;
                    changed = true;
                }
            }
            default -> { }
        }

        if (s.state != null && s.state.phase() == ForgeState.Phase.FINISHED && !s.delivered) {
            complete(player, s);
            changed = true;
        }
        if (changed) sync(player, s);
    }

    private static boolean rateLimit(Session s, long now) {
        if (now - s.lastActionMillis < MIN_ACTION_GAP_MILLIS) return false;
        if (now - s.windowStartMillis >= 1000) {
            s.windowStartMillis = now;
            s.actionsInWindow = 0;
        }
        if (++s.actionsInWindow > MAX_ACTIONS_PER_SECOND) return false;
        s.lastActionMillis = now;
        return true;
    }

    // ------------------------------------------------------------------ start / complete

    private static boolean start(ServerPlayer player, Session s, int weaponIndex, SmithingRules rules, long now) {
        if (s.state != null && s.state.phase() != ForgeState.Phase.FINISHED) return false;
        ForgeableWeapon weapon = ForgeableWeapons.byIndex(weaponIndex);
        if (weapon == null) return false;
        if (!hasMaterials(player.getInventory(), weapon)) {
            player.sendSystemMessage(Component.translatable("rpgmod.gui.missing").withStyle(ChatFormatting.RED), true);
            return false;
        }
        s.escrow.clear();
        takeMaterials(player.getInventory(), weapon, s.escrow);
        s.weapon = weapon;
        s.delivered = false;
        s.result = ItemStack.EMPTY;
        s.quality = null;
        rules = com.jo112ss.rpgmod.config.SmithingConfig.rulesFor(weapon.material());
        Random rnd = new Random(player.getRandom().nextLong());
        double[] targets = TargetGenerator.generate(rules, weapon.profile(), rnd);
        nextTokenBase += 10_000;
        s.state = new ForgeState(rules, targets, now, nextTokenBase);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.7F, 1.0F);
        return true;
    }

    public static boolean hasMaterials(Inventory inv, ForgeableWeapon weapon) {
        for (ForgeableWeapon.Material m : weapon.materials()) {
            if (count(inv, m.item().get()) < m.count()) return false;
        }
        return true;
    }

    private static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(item)) n += st.getCount();
        }
        return n;
    }

    private static void takeMaterials(Inventory inv, ForgeableWeapon weapon, List<ItemStack> into) {
        for (ForgeableWeapon.Material m : weapon.materials()) {
            int need = m.count();
            for (int i = 0; i < inv.getContainerSize() && need > 0; i++) {
                ItemStack st = inv.getItem(i);
                if (!st.is(m.item().get())) continue;
                ItemStack taken = inv.removeItem(i, Math.min(need, st.getCount()));
                need -= taken.getCount();
                into.add(taken);
            }
        }
        inv.setChanged();
    }

    /** Runs exactly once per forging: either refunds (aborted before heating) or creates the weapon. */
    private static void complete(ServerPlayer player, Session s) {
        if (s.delivered || s.state == null) return;
        s.delivered = true;

        if (s.state.endReason() == ForgeState.EndReason.ABORTED) {
            for (ItemStack st : s.escrow) give(player, st, false);
            s.escrow.clear();
            return;
        }
        s.escrow.clear(); // consumed

        Quality quality = s.state.quality();
        if (s.weapon.type() == com.jo112ss.rpgmod.item.WeaponType.ARROW) {
            deliverArrows(player, s, quality);
            return;
        }
        Random rnd = new Random(player.getRandom().nextLong());
        List<RolledOption> options = OptionRoller.roll(quality.optionCount, s.weapon.type().optionPool(), s.weapon.type().magical(),
                !com.jo112ss.rpgmod.item.UnbreakableGear.covers(s.weapon.type()), rnd);
        ItemStack stack = new ItemStack(s.weapon.result().get());
        Masterwork masterwork = Masterwork.maybe(s.weapon.type(), rnd); // one in five, whatever the quality
        ForgedData data = ForgedData.of(quality, options, player.getGameProfile().name(), masterwork);
        stack.set(ModRegistries.FORGED_DATA.get(), data);
        applyDurability(stack, data);
        ForgeNames.apply(stack, rnd); // a name of two random words, just for fun

        s.quality = quality;
        s.result = stack.copy();
        Component name = stack.getHoverName().copy().withColor(quality.color);
        Component q = Component.translatable("rpgmod.quality." + quality.id).withColor(quality.color);
        player.sendSystemMessage(Component.translatable("rpgmod.message.forged", name, q));
        player.level().playSound(null, player.blockPosition(),
                quality == Quality.LEGENDARY ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6F, 1.0F);
        if (masterwork != null) {
            player.sendSystemMessage(Component.translatable("rpgmod.message.masterwork", Masterwork.describe(masterwork)).withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7F, 1.4F);
        }
        give(player, stack, true);
        RpgMod.LOGGER.debug("{} forged {} ({}, error {})", player.getGameProfile().name(), s.weapon.id(), quality, s.state.errorRatio());
    }

    /** Arrows: plain vanilla arrows, more of them the better the work (4 / 8 / 16 / 32). */
    public static int arrowCount(Quality quality) {
        return 4 << quality.ordinal();
    }

    private static void deliverArrows(ServerPlayer player, Session s, Quality quality) {
        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.ARROW, arrowCount(quality));
        s.quality = quality;
        s.result = stack.copy();
        Component name = Component.literal(stack.getHoverName().getString() + " ×" + stack.getCount()).withColor(quality.color);
        Component q = Component.translatable("rpgmod.quality." + quality.id).withColor(quality.color);
        player.sendSystemMessage(Component.translatable("rpgmod.message.forged", name, q));
        player.level().playSound(null, player.blockPosition(),
                quality == Quality.LEGENDARY ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6F, 1.0F);
        give(player, stack, true);
    }

    /** Sets the item's max durability from its default and the durability option. */
    public static void applyDurability(ItemStack stack, ForgedData data) {
        double pct = data.get(OptionType.DURABILITY);
        Integer base = stack.getItem().components().get(DataComponents.MAX_DAMAGE);
        if (base == null || base <= 0) return;
        if (pct <= 0) {
            stack.set(DataComponents.MAX_DAMAGE, base);
        } else {
            stack.set(DataComponents.MAX_DAMAGE, (int) Math.round(base * (1 + pct / 100.0)));
        }
    }

    private static void give(ServerPlayer player, ItemStack stack, boolean notifyIfDropped) {
        if (stack.isEmpty()) return;
        // Also used during logout: the player is still in the world and is saved right after.
        if (player.isAlive()) {
            player.getInventory().add(stack); // shrinks the stack by what fitted
            player.getInventory().setChanged();
        }
        if (!stack.isEmpty()) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
            if (notifyIfDropped && !player.hasDisconnected()) {
                player.sendSystemMessage(Component.translatable("rpgmod.message.inventory_full").withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    // ------------------------------------------------------------------ sync

    private static void sync(ServerPlayer player, Session s) {
        if (s.state == null) {
            send(player, ForgeSyncPayload.select());
            return;
        }
        ForgeState st = s.state;
        SmithingRules r = st.rules();
        int stage = switch (st.phase()) {
            case HEATING -> ForgeSyncPayload.STAGE_HEATING;
            case WORKING -> ForgeSyncPayload.STAGE_WORKING;
            case FINISHED -> ForgeSyncPayload.STAGE_FINISHED;
        };
        send(player, new ForgeSyncPayload(
                stage,
                ForgeableWeapons.indexOf(s.weapon),
                (float) st.temperature(),
                toFloats(st.values()),
                toFloats(st.targets()),
                st.selectedPart(),
                st.barRunning(),
                st.barToken(),
                (float) st.currentBarSpeed(),
                (float) r.perfectZone,
                (float) r.goodZone,
                (float) r.badZone,
                (float) r.initialTempMin,
                (float) r.initialTempMax,
                (float) r.endTemp,
                (float) st.estimatedPower(),
                (float) r.adjacentRatio,
                st.strikes(),
                st.lastJudgement() == null ? -1 : st.lastJudgement().ordinal(),
                st.lastPart(),
                (float) st.lastDamage(),
                st.endReason().ordinal(),
                s.quality == null ? -1 : s.quality.ordinal(),
                (float) st.errorRatio(),
                s.result));
    }

    private static float[] toFloats(double[] a) {
        float[] f = new float[a.length];
        for (int i = 0; i < a.length; i++) f[i] = (float) a[i];
        return f;
    }

    private static void send(ServerPlayer player, ForgeSyncPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
