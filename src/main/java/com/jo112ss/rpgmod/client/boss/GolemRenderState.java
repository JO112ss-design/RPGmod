package com.jo112ss.rpgmod.client.boss;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class GolemRenderState extends LivingEntityRenderState {
    /** {@link com.jo112ss.rpgmod.mob.boss.Golem.State} ordinal. */
    public int mode;
    public float modeTicks;
    public int phase = 1;
    public boolean shield;
    public float attackAnim;
    public final BlockModelRenderState boulder = new BlockModelRenderState();
}
