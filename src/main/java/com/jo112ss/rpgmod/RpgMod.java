package com.jo112ss.rpgmod;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(RpgMod.MODID)
public class RpgMod {
    public static final String MODID = "rpgmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RpgMod(IEventBus modBus, ModContainer container) {
    }
}
