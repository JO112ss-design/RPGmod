package com.jo112ss.rpgmod.job;

import com.jo112ss.rpgmod.item.ModItems;
import com.jo112ss.rpgmod.item.WeaponType;
import com.jo112ss.rpgmod.job.core.JobDefs;
import com.jo112ss.rpgmod.job.core.JobStat;
import com.jo112ss.rpgmod.job.core.SkillDef;
import com.jo112ss.rpgmod.job.core.SkillKind;
import com.jo112ss.rpgmod.job.core.SkillPanel;
import com.jo112ss.rpgmod.network.OpenJobSelectPayload;
import com.jo112ss.rpgmod.registry.ModRegistries;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side job logic. Every request from a client goes through here and is validated against
 * the player's saved {@link JobData}; clients never change their own progress.
 */
public final class JobManager {
    private JobManager() {}

    public static JobData data(Player player) {
        return player.getData(ModRegistries.JOB_DATA);
    }

    public static void sync(ServerPlayer player) {
        player.syncData(ModRegistries.JOB_DATA);
        // what other players see on this player's name plate: job and level
        JobData d = data(player);
        String tag = d.hasJob() ? d.displayJobId() + ":" + d.displayLevel() : "";
        if (!tag.equals(player.getData(ModRegistries.JOB_TAG))) player.setData(ModRegistries.JOB_TAG, tag);
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------ lifecycle

    public static void onLogin(ServerPlayer player) {
        JobData d = data(player);
        d.migrateJobChange();
        if (d.panelOutdated()) {
            d.migrateLevelCap(JobConfig.maxLevel());
            d.resetPanel();
            player.sendSystemMessage(Component.translatable("rpgmod.job.panel_migrated").withStyle(ChatFormatting.GOLD));
        }
        if (d.advPanelOutdated()) {
            d.resetAdvPanel();
            player.sendSystemMessage(Component.translatable("rpgmod.job.adv_panel_migrated").withStyle(ChatFormatting.GOLD));
        }
        // an advanced job without its centre cell yet (older saves) gets it on login
        if (d.ensureAdvanced()) promote(player, d);
        JobStats.apply(player, d);
        d.setMana(Math.min(d.mana(), maxMana(player)));
        sync(player);
        if (!d.hasJob()) PacketDistributor.sendToPlayer(player, new OpenJobSelectPayload(false));
    }

    public static void onRespawn(ServerPlayer player) {
        JobData d = data(player);
        JobStats.apply(player, d);
        d.setMana(maxMana(player));
        player.setHealth(player.getMaxHealth());
        sync(player);
    }

    /**
     * Level 100: the advanced job. Announced to everyone, with a title for the player; the base
     * panel stays as it is and the advanced panel opens with its own points.
     */
    public static void promote(ServerPlayer player, JobData d) {
        JobDefs.Job adv = d.advancedDef();
        if (adv == null) return;
        Component name = Component.translatable("rpgmod.job." + adv.id()).withColor(adv.color());
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                Component.translatable("rpgmod.job.promoted_sub").withStyle(ChatFormatting.GOLD)));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(name.copy().withStyle(ChatFormatting.BOLD)));
        player.level().getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("rpgmod.job.promoted", player.getDisplayName(), name).withStyle(ChatFormatting.GOLD), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        com.jo112ss.rpgmod.job.fx.ServerFx.of("level_up", player.position()).follow(player).color(adv.color()).send((ServerLevel) player.level());
        JobStats.apply(player, d);
        sync(player);
    }

    // ------------------------------------------------------------------ job choice

    /** The first job (free); later changes need a job change book (see {@link #changeJob}). */
    public static void chooseJob(ServerPlayer player, String jobId) {
        JobData d = data(player);
        if (d.hasJob() || JobType.byId(jobId) == null) return;
        setJob(player, jobId);
        JobType type = JobType.byId(jobId);
        d.claimKit(jobId);
        giveStarterKit(player, type, true);
        player.sendSystemMessage(Component.translatable("rpgmod.job.chosen",
                Component.translatable("rpgmod.job." + jobId).withColor(type.color())));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    /**
     * Starting gear when a job is chosen: every weapon kind of the job in copper (the main one first:
     * wizards and healers a staff, rangers a bow, everyone else their first melee weapon), 64 arrows
     * for bow users, a forging anvil and a socket table.
     */
    private static void giveStarterKit(ServerPlayer player, JobType type, boolean full) {
        WeaponType weapon = switch (type) {
            case WIZARD, HEALER -> WeaponType.STAFF; // the healer's heals are cast from the staff
            case RANGER -> WeaponType.BOW;
            default -> type.weapons.stream().filter(w -> w != WeaponType.STAFF && w != WeaponType.BOW).findFirst().orElse(type.weapons.get(0));
        };
        List<ItemStack> kit = new java.util.ArrayList<>();
        kit.add(new ItemStack(ModItems.get(weapon, com.jo112ss.rpgmod.item.WeaponMaterial.COPPER)));
        for (WeaponType w : type.weapons) if (w != weapon) kit.add(new ItemStack(ModItems.get(w, com.jo112ss.rpgmod.item.WeaponMaterial.COPPER)));
        if (type.weapons.contains(WeaponType.BOW)) kit.add(new ItemStack(net.minecraft.world.item.Items.ARROW, 64));
        if (type == JobType.SENTINEL) kit.add(new ItemStack(ModItems.get(WeaponType.SHIELD, com.jo112ss.rpgmod.item.WeaponMaterial.COPPER))); // spear and shield
        if (full) { // the tables only with the very first job
            kit.add(new ItemStack(com.jo112ss.rpgmod.registry.ModRegistries.FORGING_ANVIL_ITEM.get()));
            kit.add(new ItemStack(com.jo112ss.rpgmod.registry.ModRegistries.SOCKET_TABLE_ITEM.get()));
        }
        List<String> names = kit.stream().map(s -> s.getCount() + " " + s.getItem()).toList();
        for (ItemStack stack : kit) {
            if (!player.getInventory().add(stack)) player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        player.sendSystemMessage(Component.translatable("rpgmod.job.starter_kit").withStyle(ChatFormatting.GREEN));
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) {
            com.jo112ss.rpgmod.RpgMod.LOGGER.info("[AUTOTEST] starter kit {}: {}", type.id, names);
        }
    }

    // ------------------------------------------------------------------ job change (job change book)

    /** The base job behind a job id: itself for a base job, its base for an advanced job (null if unknown). */
    public static JobType baseOf(String id) {
        JobType t = JobType.byId(id);
        if (t != null) return t;
        for (JobType b : JobType.BASE) {
            JobDefs.Job adv = JobDefs.advancedOf(b.id);
            if (adv != null && adv.id().equals(id)) return b;
        }
        return null;
    }

    /** Why this job cannot be picked with a book right now (a lang key), or null if it can. */
    public static String changeProblem(JobData d, String id) {
        JobType base = baseOf(id);
        if (base == null || !d.hasJob()) return "rpgmod.jobchange.unknown";
        boolean adv = JobType.byId(id) == null;
        if (d.displayJobId().equals(id)) return "rpgmod.jobchange.current";
        if (adv && (!d.mastered(base.id) || d.level() < com.jo112ss.rpgmod.job.core.LevelCurve.ADVANCED_AT)) return "rpgmod.jobchange.need_mastery";
        if (base.hybrid) { // both base jobs mastered
            for (String b : JobDefs.HYBRIDS.get(base.id)) if (!d.mastered(b)) return "rpgmod.jobchange.need_mastery";
        }
        return null;
    }

    /** The job change book the player would spend: the held one first, then any in the inventory. */
    public static ItemStack findBook(Player player) {
        for (net.minecraft.world.InteractionHand h : net.minecraft.world.InteractionHand.values()) {
            ItemStack s = player.getItemInHand(h);
            if (s.is(ModRegistries.JOB_BOOK.get())) return s;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModRegistries.JOB_BOOK.get())) return s;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Changes job with a job change book: any base job, or the advanced job of a mastered one.
     * Level, experience, mastery and skill books stay; each job keeps its own panels.
     */
    public static void changeJob(ServerPlayer player, String id) {
        JobData d = data(player);
        String problem = changeProblem(d, id);
        if (problem != null) {
            tell(player, problem);
            return;
        }
        ItemStack book = findBook(player);
        if (book.isEmpty() && !player.hasInfiniteMaterials()) {
            tell(player, "rpgmod.jobchange.no_book");
            return;
        }
        JobType base = baseOf(id);
        boolean adv = JobType.byId(id) == null;
        if (!player.hasInfiniteMaterials()) book.shrink(1);
        String from = d.displayJobId();
        d.switchTo(base.id, adv);
        if (d.claimKit(base.id)) giveStarterKit(player, base, false);
        JobStats.apply(player, d);
        d.setMana(Math.min(d.mana(), maxMana(player)));
        JobDefs.Job now = JobDefs.job(d.displayJobId());
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) {
            com.jo112ss.rpgmod.RpgMod.LOGGER.info("[AUTOTEST] job change {} -> {} (level {}, mastery {}%)", from, d.displayJobId(), d.level(), d.masteryPercent(base.id));
        }
        if (adv) {
            d.ensureAdvanced();
            promote(player, d);
            return;
        }
        player.sendSystemMessage((base.hybrid ? Component.translatable("rpgmod.jobchange.done_hybrid", Component.translatable("rpgmod.job." + now.id()).withColor(now.color()))
                : Component.translatable("rpgmod.jobchange.done", Component.translatable("rpgmod.job." + now.id()).withColor(now.color()),
                d.masteryPercent(base.id))).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.2F);
        com.jo112ss.rpgmod.job.fx.ServerFx.of("level_up", player.position()).follow(player).color(now.color()).send((ServerLevel) player.level());
        sync(player);
    }

    /** First time a job is mastered: a job change book, and everyone hears of it. */
    private static void onMastered(ServerPlayer player, JobData d) {
        if (!d.claimMasteryReward(d.jobId())) return;
        ItemStack book = new ItemStack(ModRegistries.JOB_BOOK.get());
        if (!player.getInventory().add(book)) player.drop(book, false, net.minecraft.util.Prediction.SERVER_ONLY);
        JobDefs.Job base = d.jobDef();
        Component name = Component.translatable("rpgmod.job." + base.id()).withColor(base.color());
        player.level().getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("rpgmod.jobchange.mastered_all", player.getDisplayName(), name).withStyle(ChatFormatting.GOLD), false);
        JobDefs.Job adv = JobDefs.advancedOf(base.id());
        player.sendSystemMessage(Component.translatable("rpgmod.jobchange.mastered", name,
                adv == null ? Component.empty() : Component.translatable("rpgmod.job." + adv.id()).withColor(adv.color())).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.2F);
        JobStats.apply(player, d); // mastered jobs add max health
    }

    public static void setJob(ServerPlayer player, String jobId) {
        JobData d = data(player);
        d.setJob(jobId);
        d.setMana(maxMana(player));
        JobStats.apply(player, d);
        sync(player);
    }

    // ------------------------------------------------------------------ experience

    /** Mob killed by a player: the killer and nearby players get job experience. */
    public static void onKill(ServerPlayer killer, LivingEntity victim) {
        if (victim instanceof Player) return;
        double exp = victim.getMaxHealth() * JobConfig.EXP_MULTIPLIER.get();
        // levelled monsters have three times vanilla's health scale: experience stays as it was
        if (com.jo112ss.rpgmod.mob.MobLevels.isLevelled(victim) && com.jo112ss.rpgmod.mob.MobLevels.level(victim) > 0) exp /= com.jo112ss.rpgmod.mob.core.MobLevelMath.HEALTH_SCALE;
        // the dungeon bosses pay in skill books; its job experience is that of its health alone
        if (victim.is(Tags.EntityTypes.BOSSES) && !(victim instanceof com.jo112ss.rpgmod.mob.boss.ModBoss)) exp *= JobConfig.BOSS_EXP_MULTIPLIER.get();
        long amount = Math.max(1, Math.round(exp));
        double r = JobConfig.SHARE_RADIUS.get();
        if (r <= 0) {
            grantExp(killer, amount);
            return;
        }
        for (ServerPlayer p : killer.level().getEntitiesOfClass(ServerPlayer.class, killer.getBoundingBox().inflate(r), Player::isAlive)) {
            grantExp(p, amount);
        }
    }

    public static void grantExp(ServerPlayer player, long amount) {
        JobData d = data(player);
        if (!d.hasJob()) return;
        int gained = d.addExp(amount, JobConfig.maxLevel());
        if (gained > 0 && d.ensureAdvanced()) promote(player, d);
        if (d.mastered(d.jobId())) onMastered(player, d);
        if (gained > 0) {
            JobStats.apply(player, d);
            d.setMana(maxMana(player));
            player.sendSystemMessage(Component.translatable("rpgmod.job.level_up", d.level(), d.freePoints())
                    .withStyle(ChatFormatting.GOLD));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.0F);
            com.jo112ss.rpgmod.job.fx.ServerFx.of("level_up", player.position()).follow(player).color(d.jobDef().color()).send((ServerLevel) player.level());
        }
        sync(player);
    }

    // ------------------------------------------------------------------ skill panel / loadout

    /** Buys a cell of the base panel ({@code adv} false) or of the advanced panel (advanced points). */
    public static void unlockNode(ServerPlayer player, int nodeId, boolean adv) {
        JobData d = data(player);
        SkillPanel panel = d.panel(adv);
        if (panel == null) return;
        SkillPanel.Node node = panel.node(nodeId);
        if (node == null || !panel.isReachable(node, d.unlocked(adv))) return;
        if (node.type() == SkillPanel.NodeType.BOOST && !d.knows(node.boostSkill())) { // a boost needs its skill first
            tell(player, "rpgmod.node.need_skill", Component.translatable("rpgmod.skill." + node.boostSkill()));
            return;
        }
        int need = adv || d.isHybrid() ? 0 : SkillPanel.masteryNeeded(node);
        if (d.masteryPercent(d.jobId()) < need) {
            tell(player, "rpgmod.job.need_mastery", need);
            return;
        }
        if (d.freePoints(adv) < node.cost()) {
            tell(player, "rpgmod.job.not_enough_points");
            return;
        }
        if (adv) d.unlockAdv(nodeId);
        else d.unlock(nodeId);
        // a newly learnt skill goes into an empty slot automatically
        if (node.skill() != null) {
            SkillDef def = JobDefs.skill(node.skill());
            if (def != null && def.kind() == SkillKind.ACTIVE) {
                if (d.slot(0).isEmpty()) d.setSlot(0, def.id());
                else if (d.slot(1).isEmpty()) d.setSlot(1, def.id());
                else if (d.hasSlot3() && d.slot(3).isEmpty()) d.setSlot(3, def.id());
            } else if (def != null && def.kind() == SkillKind.ULTIMATE && d.slot(2).isEmpty()) {
                d.setSlot(2, def.id());
            }
        }
        JobStats.apply(player, d);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
        sync(player);
    }

    /** Resets the skill panel for vanilla experience levels (free in creative). */
    public static void resetPanel(ServerPlayer player, boolean adv) {
        JobData d = data(player);
        if (!d.hasJob() || d.unlocked(adv).size() <= 1) return;
        int cost = JobConfig.RESET_COST_LEVELS.get();
        if (!player.hasInfiniteMaterials()) {
            if (player.experienceLevel < cost) {
                tell(player, "rpgmod.job.reset_need", cost);
                return;
            }
            player.giveExperienceLevels(-cost);
        }
        if (adv) d.resetAdvPanel();
        else d.resetPanel();
        JobStats.apply(player, d);
        player.sendSystemMessage(Component.translatable("rpgmod.job.reset_done", d.freePoints(adv)).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        sync(player);
    }

    private static final net.minecraft.resources.Identifier AFFINITY_ID =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.jo112ss.rpgmod.RpgMod.MODID, "job/weapon_affinity");

    /** Attack speed bonus while a job weapon is in the main hand (encourages using your job's weapons). */
    public static void updateAffinity(ServerPlayer player) {
        var inst = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        if (inst == null) return;
        JobData d = data(player);
        JobType type = d.jobType();
        WeaponType held = ModItems.typeOf(player.getMainHandItem().getItem());
        boolean on = type != null && held != null && type.weapons.contains(held);
        double amount = JobConfig.AFFINITY_ATTACK_SPEED.get();
        var current = inst.getModifier(AFFINITY_ID);
        if (on && amount > 0) {
            if (current == null || current.amount() != amount) {
                inst.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(AFFINITY_ID, amount,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else if (current != null) {
            inst.removeModifier(AFFINITY_ID);
        }
    }

    /** Material bows can only be drawn by jobs that use bows. */
    public static boolean canUseBow(Player player) {
        JobData d = data(player);
        return d.hasJob() && d.jobType().weapons.contains(WeaponType.BOW);
    }

    /** True for the active skill slots: 0, 1, and 3 once the advanced job is reached. */
    public static boolean activeSlot(JobData d, int slot) {
        return slot == 0 || slot == 1 || (slot == 3 && d.hasSlot3());
    }

    /** Puts a learnt skill into slot 0/1/3 (actives) or 2 (ultimate); an empty id clears the slot. */
    public static void equipSkill(ServerPlayer player, int slot, String skillId) {
        JobData d = data(player);
        if (!d.hasJob() || !(activeSlot(d, slot) || slot == 2)) return;
        if (skillId.isEmpty()) {
            d.setSlot(slot, "");
            sync(player);
            return;
        }
        SkillDef def = JobDefs.skill(skillId);
        if (def == null || !d.knows(skillId)) return;
        SkillKind need = slot == 2 ? SkillKind.ULTIMATE : SkillKind.ACTIVE;
        if (def.kind() != need) return;
        boolean fresh = true;
        for (int k = 0; k < JobData.SLOTS; k++) if (d.slot(k).equals(skillId)) fresh = false;
        // already in another active slot: the two swap places
        for (int k = 0; k < JobData.SLOTS; k++) {
            if (k != slot && k != 2 && d.slot(k).equals(skillId)) d.setSlot(k, d.slot(slot));
        }
        d.setSlot(slot, skillId);
        if (fresh) swapCooldown(player, d, skillId);
        sync(player);
    }

    /**
     * A skill just put into a slot needs a moment before it can be used, so a fight cannot be won
     * by swapping fresh skills in one after another. A longer cooldown it already had is kept.
     */
    private static void swapCooldown(Player player, JobData d, String skillId) {
        double secs = JobConfig.SPEC.isLoaded() ? JobConfig.SWAP_COOLDOWN.get() : 10;
        if (secs <= 0) return;
        long now = player.level().getGameTime();
        d.setCooldown(skillId, Math.max(d.cooldownEnd(skillId), now + Math.round(secs * 20)));
    }

    /**
     * Effect time of a skill grows with the job level (up to +50% at the top by default). Ultimates
     * keep their time, and enchanter buffs follow magic attack instead.
     */
    public static double levelDurationScale(Player player, SkillDef def) {
        if (player == null || def == null || def.duration() <= 0 || def.kind() == SkillKind.ULTIMATE) return 1;
        if ("enchanter".equals(def.job()) && def.effect() == com.jo112ss.rpgmod.job.core.SkillEffect.BUFF) return 1;
        JobData d = data(player);
        if (!d.hasJob()) return 1;
        double bonus = JobConfig.SPEC.isLoaded() ? JobConfig.DURATION_AT_MAX.get() : 0.5;
        // the same growth per level all the way, so the advanced levels keep stretching it (x2 at 199)
        return 1 + bonus * (d.level() - 1) / (double) (com.jo112ss.rpgmod.job.core.LevelCurve.DEFAULT_MAX_LEVEL - 1);
    }

    // ------------------------------------------------------------------ mana

    public static double maxMana(JobData d) {
        return JobStats.compute(d).get(JobStat.MAX_MANA);
    }

    /** Max mana including forged gear (max mana option on the held weapon and worn armour). */
    public static double maxMana(Player player) {
        return maxMana(data(player)) + gearOption(player, com.jo112ss.rpgmod.smithing.core.OptionType.MAX_MANA);
    }

    /** Mana per second: the job stat, raised by the gear's mana regeneration option (%). */
    public static double manaRegen(Player player) {
        double base = JobStats.compute(data(player)).get(JobStat.MANA_REGEN);
        return base * (1 + gearOption(player, com.jo112ss.rpgmod.smithing.core.OptionType.MANA_REGEN) / 100.0);
    }

    /** Game time of each player's last attack or hit taken (out-of-combat mana regeneration). */
    private static final Map<java.util.UUID, Long> LAST_COMBAT = new java.util.HashMap<>();

    public static void markCombat(Player player) {
        LAST_COMBAT.put(player.getUUID(), player.level().getGameTime());
    }

    public static void forgetCombat(Player player) {
        LAST_COMBAT.remove(player.getUUID());
    }

    /** True after {@code restRegenDelay} seconds without attacking or being hurt. */
    public static boolean outOfCombat(Player player) {
        Long last = LAST_COMBAT.get(player.getUUID());
        return last == null || player.level().getGameTime() - last >= JobConfig.REST_DELAY.get() * 20L;
    }

    public static void tickMana(ServerPlayer player) {
        JobData d = data(player);
        if (!d.hasJob()) return;
        double max = maxMana(player);
        boolean resting = outOfCombat(player);
        boolean changed = resting != d.resting();
        d.setResting(resting);
        if (d.mana() > max) { // took off gear with max mana
            d.setMana(max);
            changed = true;
        } else if (d.mana() < max) {
            // physical jobs: a third in combat (their MP comes from their hits), the usual out of it
            double regen = manaRegen(player) * (resting ? JobConfig.REST_MULT.get() : HitMana.physical(d) ? HitMana.NATURAL : 1);
            if (player.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.MANA_MEDITATE)) { // the spellblade meditating
                regen = regen * MoreJobSkills.MEDITATE_MULT + max * MoreJobSkills.MEDITATE_SHARE;
            }
            d.setMana(Math.min(max, d.mana() + regen));
            changed = true;
        }
        if (changed) sync(player);
    }

    public static void restoreMana(ServerPlayer player, double amount) {
        JobData d = data(player);
        if (!d.hasJob()) return;
        d.setMana(Math.min(maxMana(player), d.mana() + amount));
        sync(player);
    }

    // ------------------------------------------------------------------ casting

    public static boolean holdsJobWeapon(Player player, JobData d) {
        if (!JobConfig.REQUIRE_JOB_WEAPON.get()) return true;
        JobType type = d.jobType();
        WeaponType held = ModItems.typeOf(player.getMainHandItem().getItem());
        return type != null && held != null && type.weapons.contains(held);
    }

    /** Skill 1 / 2 / ultimate key. */
    public static void useSkill(ServerPlayer player, int slot) {
        JobData d = data(player);
        if (!d.hasJob() || !(activeSlot(d, slot) || slot == 2) || !player.isAlive() || player.isSpectator()) return;
        if (com.jo112ss.rpgmod.item.Shields.guarding(player)) { // guard or attack, not both at once
            tell(player, "rpgmod.job.guarding");
            return;
        }
        String id = d.slot(slot);
        SkillDef def = JobDefs.skill(id);
        if (def == null || !d.knows(id)) {
            tell(player, "rpgmod.job.no_skill");
            return;
        }
        if (def.id().equals("poison_coat") && player.isShiftKeyDown()) {
            // sneak + key: switch the coating instead of using the skill (free)
            d.setCoatMode(d.coatMode() + 1);
            int left = SkillExecutor.switchCoat(player, d.coatMode());
            sync(player);
            Component kind = Component.translatable("rpgmod.coat." + d.coatMode());
            player.sendSystemMessage((left > 0
                    ? Component.translatable("rpgmod.coat.switched_now", kind, (left + 19) / 20)
                    : Component.translatable("rpgmod.coat.switched", kind)).withStyle(ChatFormatting.GREEN), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.6F, 1.6F);
            return;
        }
        if (!holdsJobWeapon(player, d)) {
            tell(player, "rpgmod.job.need_weapon");
            return;
        }
        cast(player, d, def);
    }

    /** The staff's right-click spell if the player can cast it, else null. */
    public static SkillDef selectedSpell(Player player, ItemStack staff) {
        JobData d = data(player);
        if (!d.hasJob() || !d.castsSpells()) return null;
        if (d.songJob()) return null; // songs are played from the lute, never charged on a staff
        SpellLoadout loadout = staff.getOrDefault(ModRegistries.SPELL_LOADOUT.get(), SpellLoadout.EMPTY);
        return usable(d, JobDefs.skill(loadout.selectedSpell()));
    }

    /** The spellmaster's left-click spell on the staff, else null. */
    public static SkillDef leftSpell(Player player, ItemStack staff) {
        JobData d = data(player);
        if (!d.isAdvancedAs("spellmaster")) return null;
        SpellLoadout loadout = staff.getOrDefault(ModRegistries.SPELL_LOADOUT.get(), SpellLoadout.EMPTY);
        return usable(d, JobDefs.skill(loadout.leftSpell()));
    }

    private static SkillDef usable(JobData d, SkillDef def) {
        return def != null && d.castable(def) && d.knows(def.id()) ? def : null;
    }

    /** Can a charge start? (spell set, off cooldown, enough mana). Messages only when {@code notify}. */
    public static boolean canStartCast(Player player, ItemStack staff, boolean notify) {
        return canCharge(player, selectedSpell(player, staff), notify);
    }

    /** Can a charge of this spell start? Messages only when {@code notify}. */
    public static boolean canCharge(Player player, SkillDef def, boolean notify) {
        JobData d = data(player);
        if (!d.hasJob() || !d.castsSpells()) return false;
        if (com.jo112ss.rpgmod.item.Shields.guarding(player)) return false;
        ServerPlayer sp = notify && player instanceof ServerPlayer s ? s : null;
        if (def == null) {
            if (sp != null) tell(sp, "rpgmod.job.no_spell");
            return false;
        }
        long ready = d.cooldownEnd(def.id()) - player.level().getGameTime();
        if (ready > 0) {
            if (sp != null) tell(sp, "rpgmod.job.cooldown", String.format("%.1f", ready / 20.0));
            return false;
        }
        if (d.mana() < manaCost(player, def) && !canPayBlood(player, d, manaCost(player, def))) {
            if (sp != null) tell(sp, "rpgmod.job.no_mana");
            return false;
        }
        return true;
    }

    /** The weapon behind a hit: the bow for arrows, otherwise the main hand. */
    private static ItemStack weaponOf(Player player, net.minecraft.world.damagesource.DamageSource source) {
        if (source != null && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow && arrow.getWeaponItem() != null) {
            return arrow.getWeaponItem();
        }
        return player.getMainHandItem();
    }

    /** Dev test only: force the RPG crit roll (null = normal). */
    public static Boolean testForceCrit;

    /** RPG crit rate in percent: job stat + forged weapon option. */
    public static double critChance(Player player, net.minecraft.world.damagesource.DamageSource source) {
        if (testForceCrit != null) return testForceCrit ? 100 : 0;
        if (player.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.FORTUNE)) return 100; // roulette: every hit a crit
        if (player.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.AMBUSH)) return 100; // shadow hunter: the hit from hiding
        double rate = JobStats.compute(data(player)).get(JobStat.CRIT_CHANCE);
        var keen = player.getEffect(com.jo112ss.rpgmod.registry.ModEffects.KEEN); // berserker's roar
        if (keen != null) rate += 25 * (keen.getAmplifier() + 1);
        var rhapsody = player.getEffect(com.jo112ss.rpgmod.registry.ModEffects.RHAPSODY); // bard's song
        if (rhapsody != null) rate += Bard.RHAPSODY_CRIT * (rhapsody.getAmplifier() + 1);
        rate += com.jo112ss.rpgmod.gem.Gear.total(player, com.jo112ss.rpgmod.smithing.core.OptionType.CRIT_CHANCE, weaponOf(player, source));
        return Math.min(100, rate);
    }

    /** RPG crit multiplier: 1.5 + forged crit damage. */
    public static double critMultiplier(Player player, net.minecraft.world.damagesource.DamageSource source) {
        double critDmg = com.jo112ss.rpgmod.gem.Gear.total(player, com.jo112ss.rpgmod.smithing.core.OptionType.CRIT_DAMAGE, weaponOf(player, source));
        var fortune = player.getEffect(com.jo112ss.rpgmod.registry.ModEffects.FORTUNE);
        double luck = fortune == null ? 0 : 0.1 * (fortune.getAmplifier() + 1); // roulette: +10% per number
        return 1.5 + luck + critDmg / 100.0;
    }

    /** Charge time in ticks after cast speed (skill panel + forged options, capped at -60%). */
    public static int castTicks(Player player, SkillDef def) {
        double speed = Math.min(60, castSpeedPercent(player));
        if (player.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.QUICK_CAST)) speed += 40; // quick cast goes past the cap
        return (int) Math.round(def.castTime() * 20 * (1 - Math.min(85, speed) / 100.0));
    }

    private static final net.minecraft.world.entity.EquipmentSlot[] GEAR = {
            net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.HEAD,
            net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
            net.minecraft.world.entity.EquipmentSlot.FEET};

    /** Sum of an option (forged + socketed gems) over the held weapon, worn armour and an off-hand shield. */
    public static double gearOption(Player player, com.jo112ss.rpgmod.smithing.core.OptionType type) {
        return com.jo112ss.rpgmod.gem.Gear.total(player, type, null);
    }

    public static double castSpeedPercent(Player player) {
        return JobStats.compute(data(player)).get(JobStat.CAST_SPEED) + gearOption(player, com.jo112ss.rpgmod.smithing.core.OptionType.CAST_SPEED);
    }

    /** Magic attack of the held staff's material (0 when not holding a staff). */
    public static double staffMagic(Player player) {
        var item = player.getMainHandItem().getItem();
        var type = ModItems.typeOf(item);
        if (type == null || !type.magical()) return 0; // staffs and lutes
        var mat = ModItems.materialOf(item);
        return mat == null ? 0 : mat.staffMagic();
    }

    /** The held staff's material multiplier on the whole magic attack (1 without a staff). */
    public static double staffMultiplier(Player player) {
        var item = player.getMainHandItem().getItem();
        var type = ModItems.typeOf(item);
        if (type == null || !type.magical()) return 1;
        var mat = ModItems.materialOf(item);
        return mat == null ? 1 : mat.multiplier;
    }

    /**
     * Attack used by weapon skills: the player's attack attribute, which already adds up the base 1,
     * the held weapon, the job's own attack (level + panel), forged attack % and strength. Bows have
     * no melee attack, so a full-draw arrow (6, plus the bow material bonus) counts as theirs.
     */
    public static double physicalAttack(Player player) {
        double atk = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        ItemStack held = player.getMainHandItem();
        if (ModItems.typeOf(held.getItem()) == WeaponType.BOW) {
            var rb = held.getItem() instanceof com.jo112ss.rpgmod.item.RpgBowItem r ? r : null;
            double bow = 15 + (rb != null ? rb.damageBonus() : 0); // a full-draw arrow on the x3 scale
            atk += bow * (1 + com.jo112ss.rpgmod.gem.Gear.option(held, com.jo112ss.rpgmod.smithing.core.OptionType.ATTACK_DAMAGE) / 100.0) * attackUp(player);
            if (rb != null) atk *= rb.material().multiplier; // the bow's material multiplies the whole attack
        } else if (held.is(net.minecraft.world.item.Items.BOW) || held.is(net.minecraft.world.item.Items.CROSSBOW)) {
            atk += 15 * attackUp(player);
        }
        return atk;
    }

    /** The attack-up buff's multiplier (it is an attribute modifier, so the bow part outside the attribute needs it too). */
    public static double attackUp(Player player) {
        var e = player.getEffect(com.jo112ss.rpgmod.registry.ModEffects.ATTACK_UP);
        return e == null ? 1 : 1 + com.jo112ss.rpgmod.registry.ModEffects.ATTACK_UP_STEP * (e.getAmplifier() + 1);
    }

    /** Magic attack used by spells and caster skills: (1 + staff + own magic attack) x (1 + forged magic power %). */
    public static double magicAttack(Player player) {
        double own = JobStats.compute(data(player)).get(JobStat.MAGIC_ATTACK);
        double gear = gearOption(player, com.jo112ss.rpgmod.smithing.core.OptionType.MAGIC_POWER);
        double overload = player.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.OVERLOAD) ? 1.5 : 1; // spellmaster
        double gems = gearOption(player, com.jo112ss.rpgmod.smithing.core.OptionType.MAGIC_FLAT); // magic attack gems
        return (3 + staffMagic(player) + own + gems) * staffMultiplier(player) * (1 + gear / 100.0) * overload;
    }

    /**
     * Enchanter buffs (empower, haste, fire weapon, grand enchant) last longer the higher the
     * caster's magic attack: listed time x (magic attack / reference), clamped. 1 for everything else.
     */
    public static double buffDurationScale(Player player, SkillDef def) {
        if (player == null || def == null || !"enchanter".equals(def.job()) || def.effect() != com.jo112ss.rpgmod.job.core.SkillEffect.BUFF) return 1;
        boolean loaded = JobConfig.SPEC.isLoaded();
        double ref = loaded ? JobConfig.ENCHANT_BUFF_REF.get() : 10, lo = loaded ? JobConfig.ENCHANT_BUFF_MIN.get() : 0.5, hi = loaded ? JobConfig.ENCHANT_BUFF_MAX.get() : 3.0;
        return Math.max(lo, Math.min(Math.max(lo, hi), magicAttack(player) / ref));
    }

    /** Chance (0 to 50%) that luck lets a gamble be rolled again, keeping the better result. */
    public static double luckChance(Player player) {
        double per = JobConfig.SPEC.isLoaded() ? JobConfig.LUCK_PER_POINT.get() : 0.05;
        return Math.max(0, Math.min(0.5, player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.LUCK) * per));
    }

    /** True when luck grants a second roll this time. */
    public static boolean luckyReroll(Player player) {
        if (player.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.FORTUNE)) return true; // roulette prize: always
        return player.getRandom().nextDouble() < luckChance(player);
    }

    /** The attack a skill scales with. */
    public static double skillAttack(Player player, SkillDef def) {
        if ("spellblade".equals(def.job())) return spellbladeAttack(player);
        return JobDefs.isMagic(def) ? magicAttack(player) : physicalAttack(player);
    }

    /** The spellblade's skills: attack (weapon + own) plus its own magic attack. */
    public static double spellbladeAttack(Player player) {
        return physicalAttack(player) + JobStats.compute(data(player)).get(JobStat.MAGIC_ATTACK);
    }


    /**
     * What a skill's power ratio is multiplied by: its attack (weapon + own, or staff + own magic).
     * Damage = ratio x this; crits, armour etc. apply after.
     */
    public static double powerMultiplier(Player player, SkillDef def) {
        return skillAttack(player, def) * (1 + data(player).skillBoost(def.id(), com.jo112ss.rpgmod.job.core.SkillBoost.POWER));
    }

    /** A skill's cooldown in seconds for this player: its own, less cooldown reduction and its boost cells. */
    public static double cooldownSeconds(Player player, SkillDef def) {
        double cdr = JobStats.compute(data(player)).get(JobStat.COOLDOWN_REDUCTION) / 100.0;
        return def.cooldown() * (1 - cdr) * (1 - data(player).skillBoost(def.id(), com.jo112ss.rpgmod.job.core.SkillBoost.COOLDOWN));
    }


    /** Colour of each spell's charging circle and casting light. */
    public static int spellColor(String id) {
        return switch (id) {
            case "flame_burst" -> 0xFF7A2A;
            case "frost_nova" -> 0x8FE6FF;
            case "thunder" -> 0xFFF07A;
            case "arcane_shield" -> 0x7FD8FF;
            case "inferno" -> 0xFF5A1A;
            case "absolute_zero" -> 0x9FE8FF;
            case "thunderstorm" -> 0xFFF07A;
            case "stone_spikes" -> 0xC09060;
            case "radiant_lance", "holy_bolt", "holy_nova", "light_pillar", "purify" -> 0xFFE89A;
            case "heal", "group_heal" -> 0x7CFF9A;
            case "dark_orb" -> 0x8A4CFF;
            case "tornado" -> 0xA8F0D0;
            case "starfall" -> 0xC8B8FF;
            default -> 0xA98BFF;
        };
    }

    /** Charging: a magic circle drawn by the clients while the staff is held; a chime when the cast is ready. */
    public static void chargeTick(ServerPlayer player, ItemStack staff, int held, ServerLevel level) {
        SkillDef def = selectedSpell(player, staff);
        if (def == null) return;
        int need = castTicks(player, def);
        if (held == 1) {
            com.jo112ss.rpgmod.job.fx.ServerFx.of("spell_charge", player.position()).follow(player).b(need).color(spellColor(def.id())).send(level);
        }
        if (held == need) {
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.6F);
        }
    }

    /** Right-click released: fire the spell if it was charged long enough. */
    public static boolean releaseCast(ServerPlayer player, ItemStack staff, int held) {
        SkillDef def = selectedSpell(player, staff);
        if (def == null) return false;
        if (held < castTicks(player, def)) {
            player.sendSystemMessage(Component.translatable("rpgmod.job.fizzle").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        // the spellmaster holding a charged left-click spell too: the two fuse
        if (SpellCasting.tryFusion(player, def, SpellCasting.readyLeft(player))) {
            SpellCasting.clearLeft(player);
            return true;
        }
        return cast(player, data(player), def);
    }

    public static boolean cast(ServerPlayer player, JobData d, SkillDef def) {
        long now = player.level().getGameTime();
        if (!"mana_meditate".equals(def.id())) MoreJobHooks.endMeditation(player); // another skill breaks the meditation
        long ready = d.cooldownEnd(def.id());
        if (ready > now) {
            tell(player, "rpgmod.job.cooldown", String.format("%.1f", (ready - now) / 20.0));
            return false;
        }
        double cost = manaCost(player, def);
        if (d.mana() < cost && !canPayBlood(player, d, cost)) {
            tell(player, "rpgmod.job.no_mana");
            return false;
        }
        Map<JobStat, Double> st = JobStats.compute(d);
        double power = powerMultiplier(player, def);
        if (!SkillExecutor.execute(player, def, power)) return false;
        if (def.kind() == SkillKind.SPELL) echo(player, def, power);
        double cdr = st.get(JobStat.COOLDOWN_REDUCTION) / 100.0;
        if (d.mana() < cost) payBlood(player, d, cost);
        else d.setMana(d.mana() - cost);
        d.pruneCooldowns(now);
        d.setCooldown(def.id(), now + Math.round(def.cooldown() * 20 * (1 - cdr) * (1 - d.skillBoost(def.id(), com.jo112ss.rpgmod.job.core.SkillBoost.COOLDOWN))));
        sync(player);
        return true;
    }

    /** Berserker's blood price: the MP a skill is short of is paid with health, this share of max health per MP. */
    public static final double BLOOD_PRICE = 0.005;

    /** Health the berserker pays for the MP missing for this cost (0 for anyone else, or with MP enough). */
    public static double bloodPrice(Player player, JobData d, double cost) {
        if (!d.isAdvancedAs("berserker") || d.mana() >= cost) return 0;
        return (cost - d.mana()) * BLOOD_PRICE * player.getMaxHealth();
    }

    /** The price can be paid without dying (at least 1 health is left). */
    public static boolean canPayBlood(Player player, JobData d, double cost) {
        double price = bloodPrice(player, d, cost);
        return price > 0 && player.getHealth() - price >= 1;
    }

    private static void payBlood(ServerPlayer player, JobData d, double cost) {
        double price = bloodPrice(player, d, cost);
        d.setMana(0);
        player.setHealth((float) Math.max(1, player.getHealth() - price));
        com.jo112ss.rpgmod.job.fx.ServerFx.of("x_burst", player.position().add(0, player.getBbHeight() * 0.5, 0)).a(0.9).b(2).color(0xB0101A).send((ServerLevel) player.level());
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 0.8F, 0.7F);
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) com.jo112ss.rpgmod.RpgMod.LOGGER.info("[AUTOTEST] blood price {} health for {} MP short", String.format("%.1f", price), String.format("%.1f", cost));
    }

    /** Mana a skill costs right now (the spellmaster's overload makes spells 50% dearer). */
    public static double manaCost(Player player, SkillDef def) {
        if ("perform".equals(def.id()) || "spell_perform".equals(def.id())) return Bard.performCost(player); // the songs set on the lute
        if ("mana_share".equals(def.id())) return Math.ceil(maxMana(player) * MoreJobSkills.SHARE_COST); // a share of your own maximum
        boolean overload = def.kind() == SkillKind.SPELL && player.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.OVERLOAD);
        double cost = overload ? Math.ceil(def.mana() * 1.5) : def.mana();
        double cut = data(player).skillBoost(def.id(), com.jo112ss.rpgmod.job.core.SkillBoost.MANA);
        return cut > 0 ? Math.ceil(cost * (1 - cut) - 1e-9) : cost;
    }

    /** Spellmaster's double cast: the spell goes off a second time (free), using up one charge. */
    private static void echo(ServerPlayer player, SkillDef def, double power) {
        var holder = com.jo112ss.rpgmod.registry.ModEffects.DOUBLE_CAST;
        MobEffectInstance dc = player.getEffect(holder);
        if (dc == null) return;
        player.removeEffect(holder);
        if (dc.getAmplifier() > 0) player.addEffect(new MobEffectInstance(holder, dc.getDuration(), dc.getAmplifier() - 1), player);
        SkillTasks.later(8, () -> {
            if (!player.isAlive()) return;
            SkillExecutor.execute(player, def, power);
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.9F);
        });
        if (com.jo112ss.rpgmod.devtest.AutoTestServer.ENABLED) com.jo112ss.rpgmod.RpgMod.LOGGER.info("[AUTOTEST] double cast echo {} (charges left {})", def.id(), dc.getAmplifier());
    }

    // ------------------------------------------------------------------ wizard spell loadout

    /** Where spells are set: the staff, or for the bard's family the lute (songs). */
    private static ItemStack heldStaff(ServerPlayer player) {
        ItemStack s = player.getMainHandItem();
        WeaponType want = data(player).songJob() ? WeaponType.LUTE : WeaponType.STAFF;
        return ModItems.typeOf(s.getItem()) == want ? s : ItemStack.EMPTY;
    }

    public static void setSpell(ServerPlayer player, int slot, String spellId) {
        JobData d = data(player);
        ItemStack staff = heldStaff(player);
        if (staff.isEmpty() || !d.hasJob() || !d.castsSpells() || slot < 0 || slot >= d.spellSlots()) return;
        if (!spellId.isEmpty()) {
            SkillDef def = JobDefs.skill(spellId);
            if (!d.castable(def) || !d.knows(spellId)) return;
        }
        SpellLoadout l = staff.getOrDefault(ModRegistries.SPELL_LOADOUT.get(), SpellLoadout.EMPTY);
        boolean fresh = !spellId.isEmpty();
        for (int i = 0; i < SpellLoadout.SLOTS && fresh; i++) if (l.spell(i).equals(spellId)) fresh = false;
        staff.set(ModRegistries.SPELL_LOADOUT.get(), l.withSpell(slot, spellId));
        if (fresh) {
            swapCooldown(player, d, spellId);
            sync(player);
        }
    }

    public static void selectSpell(ServerPlayer player, int slot) {
        ItemStack staff = heldStaff(player);
        if (staff.isEmpty() || slot < 0 || slot >= SpellLoadout.BASE_SLOTS) return;
        SpellLoadout l = staff.getOrDefault(ModRegistries.SPELL_LOADOUT.get(), SpellLoadout.EMPTY);
        staff.set(ModRegistries.SPELL_LOADOUT.get(), l.withSelected(slot));
        String spell = l.spell(slot);
        if (!spell.isEmpty()) {
            player.sendSystemMessage(Component.translatable(data(player).songJob() ? "rpgmod.bard.song_selected" : "rpgmod.job.spell_selected", Component.translatable("rpgmod.skill." + spell))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    /** Spellmaster: the spell cast with left click. */
    public static void selectLeftSpell(ServerPlayer player, int slot) {
        ItemStack staff = heldStaff(player);
        JobData d = data(player);
        if (staff.isEmpty() || !(d.isAdvancedAs("spellmaster") || d.isAdvancedAs("maestro")) || !SpellLoadout.isLeft(slot) || slot >= d.spellSlots()) return;
        SpellLoadout l = staff.getOrDefault(ModRegistries.SPELL_LOADOUT.get(), SpellLoadout.EMPTY);
        staff.set(ModRegistries.SPELL_LOADOUT.get(), l.withSelectedLeft(slot));
        String spell = l.spell(slot);
        if (!spell.isEmpty()) {
            player.sendSystemMessage(Component.translatable(d.songJob() ? "rpgmod.bard.second_selected" : "rpgmod.job.left_spell_selected", Component.translatable("rpgmod.skill." + spell))
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** Everything this player can set on a staff (spells, and the bishop's heals). */
    public static List<String> knownSpells(JobData d) {
        List<String> out = new java.util.ArrayList<>();
        for (String s : d.knownSkills()) if (d.castable(JobDefs.skill(s))) out.add(s);
        return out;
    }
}
