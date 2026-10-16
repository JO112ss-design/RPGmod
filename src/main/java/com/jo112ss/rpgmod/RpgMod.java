package com.jo112ss.rpgmod;

import com.jo112ss.rpgmod.config.SmithingConfig;
import com.jo112ss.rpgmod.network.ModNetwork;
import com.jo112ss.rpgmod.registry.ModRegistries;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(RpgMod.MODID)
public class RpgMod {
    public static final String MODID = "rpgmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RpgMod(IEventBus modBus, ModContainer container) {
        com.jo112ss.rpgmod.item.ModItems.init();
        ModRegistries.register(modBus);
        container.registerConfig(ModConfig.Type.SYNCED, SmithingConfig.SPEC);
        container.registerConfig(ModConfig.Type.SYNCED, com.jo112ss.rpgmod.job.JobConfig.SPEC, "rpgmod-jobs.toml");
        container.registerConfig(ModConfig.Type.SYNCED, com.jo112ss.rpgmod.mob.MobConfig.SPEC, "rpgmod-mobs.toml");
        modBus.addListener(ModNetwork::register);
        modBus.addListener(RpgMod::onConfig);
        modBus.addListener(RpgMod::onConfigReload);
        modBus.addListener(RpgMod::addCreative);
    }

    private static void onConfig(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SmithingConfig.SPEC) SmithingConfig.apply();
        com.jo112ss.rpgmod.mob.MobConfig.invalidate();
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SmithingConfig.SPEC) SmithingConfig.apply();
        com.jo112ss.rpgmod.mob.MobConfig.invalidate();
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistries.FORGING_ANVIL_ITEM);
        }
    }
}
