package com.jo112ss.rpgmod.mixin;

import com.jo112ss.rpgmod.mob.ai.PathBudget;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Monster path searches: limited nodes and not too often (see {@link PathBudget}). */
@Mixin(PathNavigation.class)
public abstract class PathThrottleMixin {
    @Shadow @Final protected Mob mob;
    @Shadow protected Path path;

    @Unique private long rpgmod$next;
    @Unique private double rpgmod$dist;
    /** The last path searched: recomputePath() clears the current one before asking again. */
    @Unique private Path rpgmod$last;

    @Shadow public abstract void setMaxVisitedNodesMultiplier(float multiplier);

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void rpgmod$throttle(Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange, float maxPathLength,
                                 CallbackInfoReturnable<Path> cir) {
        if (targets.isEmpty() || !PathBudget.applies(mob)) return;
        long now = mob.level().getGameTime();
        Path keep = path != null ? path : rpgmod$last;
        if (now < rpgmod$next && keep != null && !keep.isDone()) {
            cir.setReturnValue(keep); // keep walking the current path for now
            return;
        }
        double best = Double.MAX_VALUE;
        for (BlockPos p : targets) best = Math.min(best, p.distToCenterSqr(mob.position()));
        rpgmod$dist = Math.sqrt(best);
        setMaxVisitedNodesMultiplier(PathBudget.multiplier(mob, rpgmod$dist));
    }

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("RETURN"))
    private void rpgmod$after(Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange, float maxPathLength,
                              CallbackInfoReturnable<Path> cir) {
        if (targets.isEmpty() || !PathBudget.applies(mob)) return;
        Path p = cir.getReturnValue();
        if (p != null && p == path) return; // vanilla's own early return (same goal, path still running)
        rpgmod$last = p;
        rpgmod$next = mob.level().getGameTime() + PathBudget.interval(rpgmod$dist, p != null && p.canReach());
    }
}
