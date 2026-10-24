package com.jo112ss.rpgmod.compat;

import com.jo112ss.rpgmod.RpgMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Players lying downed with the Resurrection mod: monsters leave them alone. They are not picked as
 * targets (see the player mixin), and monsters already after one let go and look for someone else.
 * Without that mod installed nothing here does anything.
 */
@EventBusSubscriber(modid = RpgMod.MODID)
public final class Downed {
    private Downed() {}

    private static final Identifier ID = Identifier.fromNamespaceAndPath("resurrection", "downed");
    private static AttachmentType<?> type;
    private static boolean looked;

    private static AttachmentType<?> type() {
        if (!looked) {
            looked = true;
            type = NeoForgeRegistries.ATTACHMENT_TYPES.getValue(ID);
            if (type != null) RpgMod.LOGGER.info("Resurrection found: monsters ignore downed players");
        }
        return type;
    }

    /** True while {@code e} is a player lying downed (Resurrection mod). */
    public static boolean is(Entity e) {
        if (!(e instanceof Player p)) return false;
        AttachmentType<?> t = type();
        return t != null && p.hasData(t) && Boolean.TRUE.equals(p.getData(t));
    }

    /** Monsters that were already after a player who just went down give up on them. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.tickCount % 10 != 0 || !(p.level() instanceof ServerLevel sl) || !is(p)) return;
        for (Mob m : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(128), m -> m.getTarget() == p)) {
            m.setTarget(null);
        }
    }
}
