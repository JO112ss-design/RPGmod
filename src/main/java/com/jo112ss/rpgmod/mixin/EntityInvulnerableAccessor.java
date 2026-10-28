package com.jo112ss.rpgmod.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The hit cooldown of an entity (see {@code HitWindows}: players' hits use their own). */
@Mixin(Entity.class)
public interface EntityInvulnerableAccessor {
    @Accessor("invulnerableTime")
    void rpgmod$setInvulnerableTime(int ticks);
}
