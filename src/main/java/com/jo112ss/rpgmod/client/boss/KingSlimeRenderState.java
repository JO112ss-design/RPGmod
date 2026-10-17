package com.jo112ss.rpgmod.client.boss;

import net.minecraft.client.renderer.entity.state.SlimeRenderState;

public class KingSlimeRenderState extends SlimeRenderState {
    /** {@link com.jo112ss.rpgmod.mob.boss.KingSlime.State} ordinal. */
    public int mode;
    /** Ticks since the mode started (with partial tick). */
    public float modeTicks;
    public int phase = 1;
}
