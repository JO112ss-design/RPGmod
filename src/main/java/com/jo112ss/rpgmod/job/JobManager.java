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
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------ lifecycle

    public static void onLogin(ServerPlayer player) {
        JobData d = data(player);
        if (d.panelOutdated()) {
            d.migrateLevelCap(JobConfig.maxLevel());
            d.resetPanel();
            player.sendSystemMessage(Component.translatable("rpgmod.job.panel_migrated").withStyle(ChatFormatting.GOLD));
        }
        JobStats.apply(player, d);
        d.setMana(Math.min(d.mana(), maxMana(d)));
        sync(player);
        if (!d.hasJob()) PacketDistributor.sendToPlayer(player, new OpenJobSelectPayload());
    }

    public static void onRespawn(ServerPlayer player) {
        JobData d = data(player);
        JobStats.apply(player, d);
        d.setMana(maxMana(d));
        player.setHealth(player.getMaxHealth());
        sync(player);
    }

    // ------------------------------------------------------------------ job choice

    /** A job can be chosen once; only an operator command resets it. */
    public static void chooseJob(ServerPlayer player, String jobId) {
        JobData d = data(player);
        if (d.hasJob() || JobType.byId(jobId) == null) return;
        setJob(player, jobId);
        JobType type = JobType.byId(jobId);
        player.sendSystemMessage(Component.translatable("rpgmod.job.chosen",
                Component.translatable("rpgmod.job." + jobId).withColor(type.color())));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    public static void setJob(ServerPlayer player, String jobId) {
        JobData d = data(player);
        d.setJob(jobId);
        d.setMana(maxMana(d));
        JobStats.apply(player, d);
        sync(player);
    }

    // ------------------------------------------------------------------ experience

    /** Mob killed by a player: the killer and nearby players get job experience. */
    public static void onKill(ServerPlayer killer, LivingEntity victim) {
        if (victim instanceof Player) return;
        double exp = victim.getMaxHealth() * JobConfig.EXP_MULTIPLIER.get();
        if (victim.is(Tags.EntityTypes.BOSSES)) exp *= JobConfig.BOSS_EXP_MULTIPLIER.get();
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
        if (gained > 0) {
            JobStats.apply(player, d);
            d.setMana(maxMana(d));
            player.sendSystemMessage(Component.translatable("rpgmod.job.level_up", d.level(), d.freePoints())
                    .withStyle(ChatFormatting.GOLD));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.0F);
            com.jo112ss.rpgmod.job.fx.ServerFx.of("level_up", player.position()).follow(player).color(d.jobDef().color()).send((ServerLevel) player.level());
        }
        sync(player);
    }

    // ------------------------------------------------------------------ skill panel / loadout

    public static void unlockNode(ServerPlayer player, int nodeId) {
        JobData d = data(player);
        SkillPanel panel = d.panel();
        if (panel == null) return;
        SkillPanel.Node node = panel.node(nodeId);
        if (node == null || !panel.isReachable(node, d.unlocked())) return;
        if (d.freePoints() < node.cost()) {
            tell(player, "rpgmod.job.not_enough_points");
            return;
        }
        d.unlock(nodeId);
        // a newly learnt skill goes into an empty slot automatically
        if (node.skill() != null) {
            SkillDef def = JobDefs.skill(node.skill());
            if (def != null && def.kind() == SkillKind.ACTIVE) {
                if (d.slot(0).isEmpty()) d.setSlot(0, def.id());
                else if (d.slot(1).isEmpty()) d.setSlot(1, def.id());
            } else if (def != null && def.kind() == SkillKind.ULTIMATE && d.slot(2).isEmpty()) {
                d.setSlot(2, def.id());
            }
        }
        JobStats.apply(player, d);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
        sync(player);
    }

    /** Resets the skill panel for vanilla experience levels (free in creative). */
    public static void resetPanel(ServerPlayer player) {
        JobData d = data(player);
        if (!d.hasJob() || d.unlocked().size() <= 1) return;
        int cost = JobConfig.RESET_COST_LEVELS.get();
        if (!player.hasInfiniteMaterials()) {
            if (player.experienceLevel < cost) {
                tell(player, "rpgmod.job.reset_need", cost);
                return;
            }
            player.giveExperienceLevels(-cost);
        }
        d.resetPanel();
        JobStats.apply(player, d);
        player.sendSystemMessage(Component.translatable("rpgmod.job.reset_done", d.freePoints()).withStyle(ChatFormatting.GOLD));
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

    /** Puts a learnt skill into slot 0/1 (actives) or 2 (ultimate); an empty id clears the slot. */
    public static void equipSkill(ServerPlayer player, int slot, String skillId) {
        JobData d = data(player);
        if (!d.hasJob() || slot < 0 || slot > 2) return;
        if (skillId.isEmpty()) {
            d.setSlot(slot, "");
            sync(player);
            return;
        }
        SkillDef def = JobDefs.skill(skillId);
        if (def == null || !d.knows(skillId)) return;
        SkillKind need = slot == 2 ? SkillKind.ULTIMATE : SkillKind.ACTIVE;
        if (def.kind() != need) return;
        if (slot < 2 && d.slot(1 - slot).equals(skillId)) d.setSlot(1 - slot, d.slot(slot)); // swap
        d.setSlot(slot, skillId);
        sync(player);
    }

    // ------------------------------------------------------------------ mana

    public static double maxMana(JobData d) {
        return JobStats.compute(d).get(JobStat.MAX_MANA);
    }

    public static void tickMana(ServerPlayer player) {
        JobData d = data(player);
        if (!d.hasJob()) return;
        Map<JobStat, Double> st = JobStats.compute(d);
        double max = st.get(JobStat.MAX_MANA);
        if (d.mana() < max) {
            d.setMana(Math.min(max, d.mana() + st.get(JobStat.MANA_REGEN)));
            sync(player);
        }
    }

    public static void restoreMana(ServerPlayer player, double amount) {
        JobData d = data(player);
        if (!d.hasJob()) return;
        d.setMana(Math.min(maxMana(d), d.mana() + amount));
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
        if (!d.hasJob() || slot < 0 || slot > 2 || !player.isAlive() || player.isSpectator()) return;
        String id = d.slot(slot);
        SkillDef def = JobDefs.skill(id);
        if (def == null || !d.knows(id)) {
            tell(player, "rpgmod.job.no_skill");
            return;
        }
        if (def.id().equals("poison_coat") && player.isShiftKeyDown()) {
            // sneak + key: switch the coating instead of using the skill (free)
            d.setCoatMode(d.coatMode() + 1);
            sync(player);
            player.sendSystemMessage(Component.translatable("rpgmod.coat.switched",
                    Component.translatable("rpgmod.coat." + d.coatMode())).withStyle(ChatFormatting.GREEN), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.6F, 1.6F);
            return;
        }
        if (!holdsJobWeapon(player, d)) {
            tell(player, "rpgmod.job.need_weapon");
            return;
        }
        cast(player, d, def);
    }

    /** The staff's selected spell if the player is a wizard who knows it, else null. */
    public static SkillDef selectedSpell(Player player, ItemStack staff) {
        JobData d = data(player);
        if (!d.hasJob() || !d.jobDef().usesSpells()) return null;
        SpellLoadout loadout = staff.getOrDefault(ModRegistries.SPELL_LOADOUT.get(), SpellLoadout.EMPTY);
        SkillDef def = JobDefs.skill(loadout.selectedSpell());
        return def != null && d.knows(def.id()) ? def : null;
    }

    /** Can a charge start? (spell set, off cooldown, enough mana). Messages only when {@code notify}. */
    public static boolean canStartCast(Player player, ItemStack staff, boolean notify) {
        JobData d = data(player);
        if (!d.hasJob() || !d.jobDef().usesSpells()) return false;
        SkillDef def = selectedSpell(player, staff);
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
        if (d.mana() < def.mana()) {
            if (sp != null) tell(sp, "rpgmod.job.no_mana");
            return false;
        }
        return true;
    }

    /** Forged data of the weapon behind a hit: the bow for arrows, otherwise the main hand. */
    private static com.jo112ss.rpgmod.smithing.ForgedData weaponData(Player player, net.minecraft.world.damagesource.DamageSource source) {
        if (source != null && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow && arrow.getWeaponItem() != null) {
            return com.jo112ss.rpgmod.effects.ForgeOptionEffects.data(arrow.getWeaponItem());
        }
        return com.jo112ss.rpgmod.effects.ForgeOptionEffects.data(player.getMainHandItem());
    }

    /** Dev test only: force the RPG crit roll (null = normal). */
    public static Boolean testForceCrit;

    /** RPG crit rate in percent: job stat + forged weapon option. */
    public static double critChance(Player player, net.minecraft.world.damagesource.DamageSource source) {
        if (testForceCrit != null) return testForceCrit ? 100 : 0;
        double rate = JobStats.compute(data(player)).get(JobStat.CRIT_CHANCE);
        var fd = weaponData(player, source);
        if (fd != null) rate += fd.get(com.jo112ss.rpgmod.smithing.core.OptionType.CRIT_CHANCE);
        return Math.min(100, rate);
    }

    /** RPG crit multiplier: 1.5 + forged crit damage. */
    public static double critMultiplier(Player player, net.minecraft.world.damagesource.DamageSource source) {
        var fd = weaponData(player, source);
        return 1.5 + (fd == null ? 0 : fd.get(com.jo112ss.rpgmod.smithing.core.OptionType.CRIT_DAMAGE) / 100.0);
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

    /** Sum of a forged option over the held weapon and worn armor. */
    public static double gearOption(Player player, com.jo112ss.rpgmod.smithing.core.OptionType type) {
        double sum = 0;
        for (var slot : GEAR) {
            var fd = com.jo112ss.rpgmod.effects.ForgeOptionEffects.data(player.getItemBySlot(slot));
            if (fd != null) sum += fd.get(type);
        }
        return sum;
    }

    public static double castSpeedPercent(Player player) {
        return JobStats.compute(data(player)).get(JobStat.CAST_SPEED) + gearOption(player, com.jo112ss.rpgmod.smithing.core.OptionType.CAST_SPEED);
    }

    /** Magic attack of the held staff's material (0 when not holding a staff). */
    public static double staffMagic(Player player) {
        var item = player.getMainHandItem().getItem();
        if (ModItems.typeOf(item) != WeaponType.STAFF) return 0;
        var mat = ModItems.materialOf(item);
        return mat == null ? 0 : mat.staffMagic();
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
            double bow = 5 + (held.getItem() instanceof com.jo112ss.rpgmod.item.RpgBowItem rb ? rb.damageBonus() : 0);
            var fd = com.jo112ss.rpgmod.effects.ForgeOptionEffects.data(held);
            atk += bow * (1 + (fd == null ? 0 : fd.get(com.jo112ss.rpgmod.smithing.core.OptionType.ATTACK_DAMAGE) / 100.0));
        } else if (held.is(net.minecraft.world.item.Items.BOW) || held.is(net.minecraft.world.item.Items.CROSSBOW)) {
            atk += 5;
        }
        return atk;
    }

    /** Magic attack used by spells and caster skills: (1 + staff + own magic attack) x (1 + forged magic power %). */
    public static double magicAttack(Player player) {
        double own = JobStats.compute(data(player)).get(JobStat.MAGIC_ATTACK);
        double gear = gearOption(player, com.jo112ss.rpgmod.smithing.core.OptionType.MAGIC_POWER);
        return (1 + staffMagic(player) + own) * (1 + gear / 100.0);
    }

    /** The attack a skill scales with. */
    public static double skillAttack(Player player, SkillDef def) {
        return JobDefs.isMagic(def) ? magicAttack(player) : physicalAttack(player);
    }

    /** Skill power stat as a multiplier (1.2 = +20%). */
    public static double skillBoost(Player player) {
        return 1 + JobStats.compute(data(player)).get(JobStat.SKILL_POWER) / 100.0;
    }

    /**
     * What a skill's power ratio is multiplied by: its attack (weapon + own, or staff + own magic)
     * times the skill power stat. Damage = ratio x this; crits, armour etc. apply after.
     */
    public static double powerMultiplier(Player player, SkillDef def) {
        return skillAttack(player, def) * skillBoost(player);
    }

    /** Colour of each spell's charging circle and casting light. */
    public static int spellColor(String id) {
        return switch (id) {
            case "flame_burst" -> 0xFF7A2A;
            case "frost_nova" -> 0x8FE6FF;
            case "thunder" -> 0xFFF07A;
            case "arcane_shield" -> 0x7FD8FF;
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
        return cast(player, data(player), def);
    }

    private static boolean cast(ServerPlayer player, JobData d, SkillDef def) {
        long now = player.level().getGameTime();
        long ready = d.cooldownEnd(def.id());
        if (ready > now) {
            tell(player, "rpgmod.job.cooldown", String.format("%.1f", (ready - now) / 20.0));
            return false;
        }
        if (d.mana() < def.mana()) {
            tell(player, "rpgmod.job.no_mana");
            return false;
        }
        Map<JobStat, Double> st = JobStats.compute(d);
        double power = powerMultiplier(player, def);
        if (!SkillExecutor.execute(player, def, power)) return false;
        double cdr = st.get(JobStat.COOLDOWN_REDUCTION) / 100.0;
        d.setMana(d.mana() - def.mana());
        d.pruneCooldowns(now);
        d.setCooldown(def.id(), now + Math.round(def.cooldown() * 20 * (1 - cdr)));
        sync(player);
        return true;
    }

    // ------------------------------------------------------------------ wizard spell loadout

    private static ItemStack heldStaff(ServerPlayer player) {
        ItemStack s = player.getMainHandItem();
        return ModItems.typeOf(s.getItem()) == WeaponType.STAFF ? s : ItemStack.EMPTY;
    }

    public static void setSpell(ServerPlayer player, int slot, String spellId) {
        JobData d = data(player);
        ItemStack staff = heldStaff(player);
        if (staff.isEmpty() || !d.hasJob() || !d.jobDef().usesSpells() || slot < 0 || slot >= SpellLoadout.SLOTS) return;
        if (!spellId.isEmpty()) {
            SkillDef def = JobDefs.skill(spellId);
            if (def == null || def.kind() != SkillKind.SPELL || !d.knows(spellId)) return;
        }
        SpellLoadout l = staff.getOrDefault(ModRegistries.SPELL_LOADOUT.get(), SpellLoadout.EMPTY);
        staff.set(ModRegistries.SPELL_LOADOUT.get(), l.withSpell(slot, spellId));
    }

    public static void selectSpell(ServerPlayer player, int slot) {
        ItemStack staff = heldStaff(player);
        if (staff.isEmpty() || slot < 0 || slot >= SpellLoadout.SLOTS) return;
        SpellLoadout l = staff.getOrDefault(ModRegistries.SPELL_LOADOUT.get(), SpellLoadout.EMPTY);
        staff.set(ModRegistries.SPELL_LOADOUT.get(), l.withSelected(slot));
        String spell = l.spell(slot);
        if (!spell.isEmpty()) {
            player.sendSystemMessage(Component.translatable("rpgmod.job.spell_selected", Component.translatable("rpgmod.skill." + spell))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    public static List<String> knownSpells(JobData d) {
        return d.knownSkills(SkillKind.SPELL);
    }
}
