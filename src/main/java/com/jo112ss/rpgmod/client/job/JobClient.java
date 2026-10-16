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

    public static void openJobSelect() {
        pendingSelect = true;
    }

    public static boolean holdingStaff() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && ModItems.typeOf(mc.player.getMainHandItem().getItem()) == WeaponType.STAFF;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (pendingSelect && mc.player != null && mc.gui.screen() == null && mc.player.tickCount > 20) {
            pendingSelect = false;
            if (!data().hasJob()) mc.gui.setScreen(new JobSelectScreen());
            return;
        }
        if (mc.player == null || mc.gui.screen() != null) {
            // drain clicks made while a screen was open
            for (var k : JobKeys.SKILLS) while (k.consumeClick()) { }
            return;
        }
        while (JobKeys.STATUS.consumeClick()) {
            mc.gui.setScreen(data().hasJob() ? new JobScreen(JobScreen.Tab.STATUS) : new JobSelectScreen());
            return;
        }
        for (int i = 0; i < JobKeys.SKILLS.length; i++) {
            while (JobKeys.SKILLS[i].consumeClick()) {
                if (data().hasJob()) send(JobActionPayload.USE_SKILL, i, "");
            }
        }
        while (JobKeys.SPELL_WHEEL.consumeClick()) {
            JobData d = data();
            if (d.hasJob() && d.jobDef().usesSpells() && holdingStaff()) {
                mc.gui.setScreen(new SpellWheelScreen());
                return;
            }
        }
    }
}
