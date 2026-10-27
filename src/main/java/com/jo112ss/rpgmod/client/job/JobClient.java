package com.jo112ss.rpgmod.client.job;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.JobData;
import com.jo112ss.rpgmod.network.JobActionPayload;
import com.jo112ss.rpgmod.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client entry points for the job system: key handling and access to the synced job data. */
@EventBusSubscriber(modid = RpgMod.MODID, value = Dist.CLIENT)
public final class JobClient {
    private JobClient() {}

    public static JobData data() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? new JobData() : mc.player.getData(ModRegistries.JOB_DATA);
    }

    public static void send(int action, int arg, String text) {
        ClientPacketDistributor.sendToServer(new JobActionPayload(action, arg, text == null ? "" : text));
    }

    /** Set when the server says we have no job; the screen opens once no other screen (e.g. world loading) is up. */
    private static boolean pendingSelect;

    public static void openJobSelect(boolean change) {
        Minecraft mc = Minecraft.getInstance();
        if (change) mc.gui.setScreen(new JobSelectScreen(true)); // a job change book was used
        else pendingSelect = true;
    }

    /** Holding where spells are set: the staff, or the lute for the bard's family (songs). */
    public static boolean holdingStaff() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        WeaponType want = data().songJob() ? WeaponType.LUTE : WeaponType.STAFF;
        return ModItems.typeOf(mc.player.getMainHandItem().getItem()) == want;
    }

    // ---- ki jobs: ki shots on the right button

    /** A ki job (monk, champion, god hand) with knuckles in hand. */
    public static boolean kiShooting() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && data().kiJob() && ModItems.typeOf(mc.player.getMainHandItem().getItem()) == WeaponType.KNUCKLE;
    }

    /** Ticks right click has been held charging a ki shot (-1 = not charging). */
    public static int kiCharge = -1;
    /** The current right-button press started on a door, a villager...: no ki shot until it is let go. */
    private static boolean kiPressUsed;

    /** Charges of the ki shot being held (one point of ki each, up to the ki there is). */
    public static int kiCharges() {
        return kiCharge < 0 ? 0 : com.jo112ss.rpgmod.job.Monk.charges(kiCharge, data().ki());
    }

    private static void tickKiCharge(Minecraft mc) {
        boolean down = mc.player != null && mc.gui.screen() == null && mc.options.keyUse.isDown();
        if (!down) kiPressUsed = false;
        boolean want = down && (kiCharge >= 0 || !kiPressUsed) && kiShooting();
        if (want && kiCharge < 0) {
            kiPressUsed = true; // decided once per press
            if (aimingAtUsable(mc) || mc.player.isShiftKeyDown() || aimingAtFriend(mc)) return; // the right button as usual
            send(JobActionPayload.KI_SHOT, 1, "");
            if (data().ki() <= 0) return; // the server clicks dully
            kiCharge = 0;
        } else if (want) {
            int before = kiCharges();
            kiCharge++;
            int now = kiCharges();
            if (now > before) mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 0.9F + 0.15F * now);
        } else if (kiCharge >= 0) {
            kiCharge = -1;
            send(JobActionPayload.KI_SHOT, 0, "");
        }
    }

    /** Aiming at a villager, an animal, a player...: right click keeps its usual meaning (trading, leads...). */
    private static boolean aimingAtFriend(Minecraft mc) {
        return mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult eh && !(eh.getEntity() instanceof net.minecraft.world.entity.monster.Enemy);
    }

    // ---- spellmaster: the left-click spell

    /** Ticks the left button has been held for the left-hand spell (-1 = not charging). */
    public static int leftCharge = -1;

    /** Spellmaster holding a staff with a left-click spell set. */
    public static boolean leftCasting() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !holdingStaff() || !data().isAdvancedAs("spellmaster")) return false;
        var l = mc.player.getMainHandItem().getOrDefault(ModRegistries.SPELL_LOADOUT.get(), com.jo112ss.rpgmod.job.SpellLoadout.EMPTY);
        return !l.leftSpell().isEmpty();
    }

    // ---- spellblade: sword magic on the right button

    /** Ticks right click has been held with the spellblade's sword (-1 = not charging). */
    public static int swordCharge = -1;
    /** The current right-button press started on a door, chest...: no sword magic until it is let go. */
    private static boolean swordPressUsed;

    public static boolean swordCasting() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && "spellblade".equals(data().jobId()) && ModItems.typeOf(mc.player.getMainHandItem().getItem()) == WeaponType.SWORD;
    }

    /** Aiming at something to use (a door, a chest, a button...): right click keeps its usual meaning there. */
    private static boolean aimingAtUsable(Minecraft mc) {
        if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh) || bh.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return false;
        var state = mc.level.getBlockState(bh.getBlockPos());
        var b = state.getBlock();
        return state.getMenuProvider(mc.level, bh.getBlockPos()) != null || b instanceof net.minecraft.world.level.block.DoorBlock
                || b instanceof net.minecraft.world.level.block.TrapDoorBlock || b instanceof net.minecraft.world.level.block.FenceGateBlock
                || b instanceof net.minecraft.world.level.block.ButtonBlock || b instanceof net.minecraft.world.level.block.LeverBlock
                || b instanceof net.minecraft.world.level.block.BedBlock || b instanceof net.minecraft.world.level.block.BaseEntityBlock;
    }

    private static void tickSwordCast(Minecraft mc) {
        boolean down = mc.player != null && mc.gui.screen() == null && mc.options.keyUse.isDown();
        if (!down) swordPressUsed = false;
        boolean want = down && (swordCharge >= 0 || !swordPressUsed) && swordCasting();
        if (want && swordCharge < 0) {
            swordPressUsed = true; // decided once per press
            if (aimingAtUsable(mc) || mc.player.isShiftKeyDown()) return; // sneak: the right button as usual (off hand, blocks...)
            send(JobActionPayload.SWORD_CAST, 1, "");
            int el = Math.floorMod(data().coatMode(), 3);
            if (data().mana() < com.jo112ss.rpgmod.job.SpellbladeMagic.COST[el][0]) return; // the server says why
            swordCharge = 0;
        } else if (want) {
            swordCharge++;
        } else if (swordCharge >= 0) {
            swordCharge = -1;
            send(JobActionPayload.SWORD_CAST, 0, "");
        }
    }

    /** Charging sword magic slows you down like drawing a bow or charging a staff (and stops the sprint). */
    @SubscribeEvent
    public static void onMovementInput(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event) {
        if ((swordCharge < 0 && kiCharge < 0) || event.getEntity().isPassenger()) return;
        var input = event.getInput();
        input.moveVector = input.moveVector.scale(swordCharge >= 0 ? SWORD_CHARGE_SPEED : KI_CHARGE_SPEED);
        event.getEntity().setSprinting(false);
    }

    /** Walking speed while charging sword magic (vanilla item use: 0.2). */
    public static final float SWORD_CHARGE_SPEED = 0.2F;
    /** Walking speed while charging a ki shot: a monk stays light on its feet. */
    public static final float KI_CHARGE_SPEED = 0.45F;

    private static void tickLeftCast(Minecraft mc) {
        boolean want = mc.player != null && mc.gui.screen() == null && leftCasting() && JobKeys.LEFT_SPELL.isDown();
        if (want && leftCharge < 0) {
            leftCharge = 0;
            send(JobActionPayload.LEFT_CAST, 1, "");
        } else if (want) {
            leftCharge++;
        } else if (leftCharge >= 0) {
            leftCharge = -1;
            send(JobActionPayload.LEFT_CAST, 0, "");
        }
    }

    /**
     * Left click with the spellmaster's staff charges the left-hand spell instead of attacking; right
     * click with the nightblade's off-hand dagger strikes with it.
     */
    @SubscribeEvent
    public static void onClickInput(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // on the attack button (the default), the left-hand spell takes it over; bound elsewhere, attacks stay
        if (event.isAttack() && BardClient.performing()) { // the bard's notes, not an attack
            event.setCanceled(true);
            event.setSwingHand(false);
            BardClient.click();
            return;
        }
        if (event.isAttack() && leftCasting() && JobKeys.LEFT_SPELL.getKey().equals(mc.options.keyAttack.getKey())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (event.isUseItem() && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND && swordCasting() && !aimingAtUsable(mc)
                && !mc.player.isShiftKeyDown()) {
            event.setCanceled(true); // the right button charges sword magic (see tickSwordCast)
            event.setSwingHand(false);
            return;
        }
        if (event.isUseItem() && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND && kiShooting() && !aimingAtUsable(mc)
                && !mc.player.isShiftKeyDown() && !aimingAtFriend(mc)) {
            event.setCanceled(true); // the right button charges a ki shot (see tickKiCharge)
            event.setSwingHand(false); // the server swings when the shot goes
            return;
        }
        if (event.isUseItem() && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND && com.jo112ss.rpgmod.job.DualWield.canDualWield(mc.player)) {
            var hit = mc.hitResult;
            if (hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) return; // doors, chests... as usual
            int id = hit instanceof net.minecraft.world.phys.EntityHitResult eh ? eh.getEntity().getId() : -1;
            event.setCanceled(true);
            event.setSwingHand(false);
            mc.player.swing(net.minecraft.world.InteractionHand.OFF_HAND, mc.player.getOffhandItem().getAttackAnimation(), false);
            send(JobActionPayload.OFFHAND_ATTACK, id, "");
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        tickLeftCast(mc);
        tickSwordCast(mc);
        tickKiCharge(mc);
        BardClient.tick();
        if (pendingSelect && mc.player != null && mc.gui.screen() == null && mc.player.tickCount > 20) {
            pendingSelect = false;
            if (!data().hasJob()) mc.gui.setScreen(new JobSelectScreen(false));
            return;
        }
        if (mc.player == null || mc.gui.screen() != null) {
            // drain clicks made while a screen was open
            for (var k : JobKeys.SKILLS) while (k.consumeClick()) { }
            return;
        }
        while (JobKeys.STATUS.consumeClick()) {
            mc.gui.setScreen(data().hasJob() ? new JobScreen(JobScreen.Tab.STATUS) : new JobSelectScreen(false));
            return;
        }
        for (int i = 0; i < JobKeys.SKILLS.length; i++) {
            while (JobKeys.SKILLS[i].consumeClick()) {
                if (data().hasJob()) send(JobActionPayload.USE_SKILL, i, "");
            }
        }
        while (JobKeys.SPELL_WHEEL.consumeClick()) {
            JobData d = data();
            if (d.castsSpells() && holdingStaff()) {
                mc.gui.setScreen(new SpellWheelScreen());
                return;
            }
        }
    }
}
