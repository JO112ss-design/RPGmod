package com.jo112ss.rpgmod.job;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Operator commands: /rpgjob info|reset|set|level|exp &lt;player&gt; ...
 * Normal players choose a job once; these are for testing and fixing mistakes.
 */
public final class JobCommands {
    private JobCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("rpgjob").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("info").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                    JobData data = JobManager.data(p);
                    c.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().name() + ": job=" + (data.hasJob() ? data.jobId() : "-")
                            + " level=" + data.level() + " exp=" + data.exp() + "/" + data.expToNext()
                            + " points=" + data.freePoints() + "/" + data.totalPoints()
                            + " advanced=" + data.isAdvanced() + " mastery=" + (data.hasJob() ? data.masteryPercent(data.jobId()) + "%" : "-")
                            + " mastered=" + data.masteredCount()), false);
                    return 1;
                })))
                .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                    JobManager.setJob(p, "");
                    JobManager.onLogin(p); // reopens the selection screen
                    c.getSource().sendSuccess(() -> Component.translatable("rpgmod.command.reset", p.getDisplayName()), true);
                    return 1;
                })))
                .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("job", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(JobType.values()).map(j -> j.id), b))
                                .executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                                    String job = StringArgumentType.getString(c, "job");
                                    if (JobType.byId(job) == null) {
                                        c.getSource().sendFailure(Component.literal("Unknown job: " + job));
                                        return 0;
                                    }
                                    JobManager.setJob(p, job);
                                    c.getSource().sendSuccess(() -> Component.literal("Set job of " + p.getGameProfile().name() + " to " + job), true);
                                    return 1;
                                }))))
                .then(Commands.literal("level").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 1000)).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            int lv = Math.min(IntegerArgumentType.getInteger(c, "level"), JobConfig.maxLevel());
                            JobData data = JobManager.data(p);
                            data.setLevel(lv);
                            if (data.ensureAdvanced()) JobManager.promote(p, data);
                            JobStats.apply(p, data);
                            JobManager.sync(p);
                            c.getSource().sendSuccess(() -> Component.literal("Level of " + p.getGameProfile().name() + " = " + lv), true);
                            return 1;
                        }))))
                .then(Commands.literal("advance").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    // straight to the advanced job (level 100+, the job mastered), no book
                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                    JobData data = JobManager.data(p);
                    if (!data.hasJob()) return 0;
                    if (data.level() < com.jo112ss.rpgmod.job.core.LevelCurve.ADVANCED_AT) data.setLevel(com.jo112ss.rpgmod.job.core.LevelCurve.ADVANCED_AT);
                    data.setMastery(data.jobId(), com.jo112ss.rpgmod.job.core.LevelCurve.masteryExp());
                    data.setAdvanced(true);
                    if (data.ensureAdvanced()) JobManager.promote(p, data);
                    JobStats.apply(p, data);
                    JobManager.sync(p);
                    c.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().name() + " is now " + data.displayJobId()), true);
                    return 1;
                })))
                .then(Commands.literal("mastery").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("job", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(JobType.values()).map(j -> j.id), b))
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100)).executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                                    String job = StringArgumentType.getString(c, "job");
                                    if (JobType.byId(job) == null) {
                                        c.getSource().sendFailure(Component.literal("Unknown job: " + job));
                                        return 0;
                                    }
                                    int pct = IntegerArgumentType.getInteger(c, "percent");
                                    JobData data = JobManager.data(p);
                                    data.setMastery(job, Math.round(com.jo112ss.rpgmod.job.core.LevelCurve.masteryExp() * pct / 100.0));
                                    JobStats.apply(p, data);
                                    JobManager.sync(p);
                                    c.getSource().sendSuccess(() -> Component.literal("Mastery of " + job + " for " + p.getGameProfile().name() + " = " + pct + "%"), true);
                                    return 1;
                                })))))
                .then(Commands.literal("exp").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            JobManager.grantExp(p, LongArgumentType.getLong(c, "amount"));
                            return 1;
                        })))));
    }
}
