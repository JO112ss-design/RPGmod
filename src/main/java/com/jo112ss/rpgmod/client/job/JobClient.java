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

    public static boolean holdingStaff() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && ModItems.typeOf(mc.player.getMainHandItem().getItem()) == WeaponType.STAFF;
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
