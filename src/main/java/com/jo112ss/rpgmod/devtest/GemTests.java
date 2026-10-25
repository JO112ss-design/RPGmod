package com.jo112ss.rpgmod.devtest;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.gem.Gem;
import com.jo112ss.rpgmod.gem.GemDrops;
import com.jo112ss.rpgmod.gem.GemEffects;
import com.jo112ss.rpgmod.gem.GemItems;
import com.jo112ss.rpgmod.gem.GemRoller;
import com.jo112ss.rpgmod.gem.GemSpecial;
import com.jo112ss.rpgmod.gem.SocketMenu;
import com.jo112ss.rpgmod.gem.Sockets;
import com.jo112ss.rpgmod.job.JobManager;
import com.jo112ss.rpgmod.job.SkillTasks;
import com.jo112ss.rpgmod.registry.ModRegistries;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Dev test for gems and sockets (run from the client showcase as "gem:*" entries). */
final class GemTests {
    private GemTests() {}

    static void log(String msg, Object... args) {
        RpgMod.LOGGER.info("[AUTOTEST] gemtest " + msg, args);
    }

    static ItemStack gear(net.minecraft.world.item.Item item, Gem... gems) {
        ItemStack s = new ItemStack(item);
        com.jo112ss.rpgmod.gem.Sockets so = com.jo112ss.rpgmod.gem.Sockets.EMPTY;
        for (Gem g : gems) so = so.with(g);
        s.set(ModRegistries.SOCKETS.get(), so);
        return s;
    }

    static Husk dummy(ServerLevel level, ServerPlayer p, double dx, double dz, float hp) {
        Husk h = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
        h.setNoAi(true);
        h.setSilent(true);
        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        h.setHealth(hp);
        h.snapTo(p.getX() + dx, p.getY(), p.getZ() + dz, 0, 0);
        level.addFreshEntity(h);
        return h;
    }

    static void hit(ServerLevel level, ServerPlayer p, LivingEntity t, float dmg) {
        t.setInvulnerableTime(0);
        t.hurtServer(level, p.damageSources().playerAttack(p), dmg);
    }

    static void scene(ServerLevel level, ServerPlayer p, String name, List<? extends LivingEntity> targets) {
        switch (name) {
            case "checks" -> checks(level, p);
            case "socket" -> socket(level, p);
            case "procs" -> procs(level, p, targets);
            default -> log("unknown scene {}", name);
        }
    }

    // ------------------------------------------------------------------ logic checks

    private static void checks(ServerLevel level, ServerPlayer p) {
        // drop chance by level and rarity shares
        log("drop chance Lv1 {} Lv100 {} Lv150 {} Lv200 {} Lv300 {}", GemDrops.chance(1), GemDrops.chance(100), GemDrops.chance(150), GemDrops.chance(200), GemDrops.chance(300));
        RandomSource r = RandomSource.create(5);
        int[] normal = new int[3], boss = new int[3];
        int specialsOfRare = 0;
        for (int i = 0; i < 20000; i++) {
            normal[GemRoller.rollRarity(r, false)]++;
            boss[GemRoller.rollRarity(r, true)]++;
            Gem g = GemRoller.roll(Gem.RARE, r);
            if (g.special() != null) specialsOfRare++;
        }
        log("rarity in 20000: normal {}/{}/{} boss {}/{}/{}; rare gems that are special {}", normal[0], normal[1], normal[2], boss[0], boss[1], boss[2], specialsOfRare);
        Gem c = GemRoller.roll(Gem.COMMON, r), rr = GemRoller.stat(com.jo112ss.rpgmod.smithing.core.OptionType.CRIT_CHANCE, Gem.RARE, r);
        log("sample common {} {} / rare crit {} (forge range 2-5)", c.effect(), c.value(), rr.value());

        // capacities
        log("capacity copper helmet {} iron sword {} gold dagger {} diamond chest {} netherite hammer {} vanilla shield {} netherite shield {} leather {} arrow {} bow(diamond) {}",
                Sockets.capacity(new ItemStack(Items.COPPER_HELMET)), Sockets.capacity(new ItemStack(Items.IRON_SWORD)),
                Sockets.capacity(new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(com.jo112ss.rpgmod.item.WeaponType.DAGGER, com.jo112ss.rpgmod.item.WeaponMaterial.GOLD))),
                Sockets.capacity(new ItemStack(Items.DIAMOND_CHESTPLATE)),
                Sockets.capacity(new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(com.jo112ss.rpgmod.item.WeaponType.HAMMER, com.jo112ss.rpgmod.item.WeaponMaterial.NETHERITE))),
                Sockets.capacity(new ItemStack(Items.SHIELD)),
                Sockets.capacity(new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(com.jo112ss.rpgmod.item.WeaponType.SHIELD, com.jo112ss.rpgmod.item.WeaponMaterial.NETHERITE))),
                Sockets.capacity(new ItemStack(Items.LEATHER_HELMET)), Sockets.capacity(new ItemStack(Items.ARROW)),
                Sockets.capacity(new ItemStack(com.jo112ss.rpgmod.item.ModItems.get(com.jo112ss.rpgmod.item.WeaponType.BOW, com.jo112ss.rpgmod.item.WeaponMaterial.DIAMOND))));

        // socketing through the menu
        BlockPos pos = p.blockPosition().east(3);
        level.setBlockAndUpdate(pos, ModRegistries.SOCKET_TABLE.get().defaultBlockState());
        SocketMenu m = new SocketMenu(0, p.getInventory(), ContainerLevelAccess.create(level, pos));
        boolean creative = p.hasInfiniteMaterials();
        var mode = p.gameMode();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setExperienceLevels(12);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        m.testPut(sword, GemItems.stack(new Gem("crit_chance", Gem.COMMON, 5)));
        String p1 = m.problem();
        boolean ok1 = m.clickMenuButton(p, 0);
        int lv1 = p.experienceLevel;
        m.testPut(m.gear(), GemItems.stack(Gem.of(GemSpecial.IGNITE)));
        boolean ok2 = m.clickMenuButton(p, 0);
        m.testPut(m.gear(), GemItems.stack(Gem.of(GemSpecial.THUNDER)));
        String full = m.problem();
        boolean ok3 = m.clickMenuButton(p, 0);
        ItemStack done = m.gear().copy();
        ItemStack neth = gear(Items.NETHERITE_SWORD, Gem.of(GemSpecial.IGNITE));
        m.testPut(neth, GemItems.stack(Gem.of(GemSpecial.IGNITE)));
        String same = m.problem();
        p.setExperienceLevels(2);
        m.testPut(new ItemStack(Items.IRON_HELMET), GemItems.stack(Gem.of(GemSpecial.LIFESTEAL)));
        String noXp = m.problem();
        m.testPut(ItemStack.EMPTY, ItemStack.EMPTY);
        log("socket: first problem={} ok={} levels 12->{} ; second ok={} ; third problem={} ok={} ; gems {} ; same legendary={} ; no xp={}",
                p1, ok1, lv1, ok2, full, ok3, Sockets.gems(done).stream().map(Gem::effect).toList(), same, noXp);
        // netherite upgrade keeps gems (vanilla copies components): simulate with transmuteCopy
        ItemStack up = done.transmuteCopy(Items.NETHERITE_SWORD, 1);
        log("upgrade to netherite keeps {} gems, free {}", Sockets.gems(up).size(), Sockets.free(up));
        level.removeBlock(pos, false);

        // stat gems: crit chance from a sword gem and from a helmet gem
        ItemStack mainBefore = p.getMainHandItem().copy();
        ItemStack headBefore = p.getItemBySlot(EquipmentSlot.HEAD).copy();
        p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        double base = JobManager.critChance(p, null);
        p.setItemSlot(EquipmentSlot.MAINHAND, done);
        double withSword = JobManager.critChance(p, null);
        p.setItemSlot(EquipmentSlot.HEAD, gear(Items.IRON_HELMET, new Gem("crit_chance", Gem.RARE, 8), new Gem("max_mana", Gem.COMMON, 20)));
        double withHelmet = JobManager.critChance(p, null);
        double mana = JobManager.maxMana(p);
        log("crit chance {} -> sword gem {} -> + helmet gem {} ; max mana with helmet gem {}", base, withSword, withHelmet, mana);
        p.setItemSlot(EquipmentSlot.HEAD, gear(Items.IRON_HELMET, new Gem("max_health", Gem.RARE, 4), new Gem("attack_damage", Gem.COMMON, 10)));
        SkillTasks.later(3, () -> {
            log("helmet gems as attributes: max health {} attack {}", p.getAttributeValue(Attributes.MAX_HEALTH), String.format("%.2f", p.getAttributeValue(Attributes.ATTACK_DAMAGE)));
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            SkillTasks.later(3, () -> log("without: max health {} attack {}", p.getAttributeValue(Attributes.MAX_HEALTH), String.format("%.2f", p.getAttributeValue(Attributes.ATTACK_DAMAGE))));
        });

        // specials, every chance gem forced
        boolean wasInv = p.getAbilities().invulnerable;
        p.getAbilities().invulnerable = false;
        var diff = level.getDifficulty();
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        GemEffects.testAlways = true;
        JobManager.testForceCrit = false;
        Husk a = dummy(level, p, 3, 3, 500);
        p.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.NETHERITE_SWORD, Gem.of(GemSpecial.IGNITE), Gem.of(GemSpecial.FREEZE), Gem.of(GemSpecial.THUNDER)));
        hit(level, p, a, 4);
        var slow = a.getEffect(MobEffects.SLOWNESS);
        log("ignite/freeze/thunder: on fire={} slowness amp={} frozen ticks={}", a.getRemainingFireTicks() > 0, slow == null ? -1 : slow.getAmplifier(), a.getTicksFrozen());

        Husk b = dummy(level, p, -3, 3, 500);
        p.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.NETHERITE_SWORD, Gem.of(GemSpecial.VENOM), Gem.of(GemSpecial.CHILL), Gem.of(GemSpecial.LIFESTEAL)));
        p.setHealth(10);
        float hb = b.getHealth();
        hit(level, p, b, 10);
        float dealt = hb - b.getHealth();
        var chill = b.getEffect(MobEffects.SLOWNESS);
        log("venom/chill/lifesteal: venom={} slowness amp={} dealt {} health 10 -> {} (expected +{})", b.hasEffect(com.jo112ss.rpgmod.registry.ModEffects.VENOM),
                chill == null ? -1 : chill.getAmplifier(), dealt, p.getHealth(), dealt * 0.03);

        // desperation: same hit at full and at a third of health
        Husk d = dummy(level, p, 0, 5, 500);
        p.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.IRON_SWORD, Gem.of(GemSpecial.DESPERATION)));
        p.setHealth(p.getMaxHealth());
        float d0 = d.getHealth();
        hit(level, p, d, 10);
        float atFull = d0 - d.getHealth();
        p.setHealth(p.getMaxHealth() * 0.3F);
        d0 = d.getHealth();
        hit(level, p, d, 10);
        float low = d0 - d.getHealth();
        log("desperation: full health {} low health {} (x{})", atFull, low, String.format("%.2f", low / atFull));

        // mana drain
        var jd = JobManager.data(p);
        jd.setMana(0);
        Husk e = dummy(level, p, 2, 6, 500);
        p.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.IRON_SWORD, Gem.of(GemSpecial.MANA_DRAIN)));
        hit(level, p, e, 4);
        double m1 = jd.mana();
        hit(level, p, e, 4); // within half a second: no more
        log("mana drain: 0 -> {} (3% of {}), again at once -> {}", String.format("%.1f", m1), JobManager.maxMana(p), String.format("%.1f", jd.mana()));

        // thorns (chest) and phoenix (helmet) and acrobat (boots)
        ItemStack chestBefore = p.getItemBySlot(EquipmentSlot.CHEST).copy(), feetBefore = p.getItemBySlot(EquipmentSlot.FEET).copy();
        p.setItemSlot(EquipmentSlot.CHEST, gear(Items.IRON_CHESTPLATE, Gem.of(GemSpecial.THORNS)));
        p.setItemSlot(EquipmentSlot.HEAD, gear(Items.IRON_HELMET, Gem.of(GemSpecial.PHOENIX)));
        p.setItemSlot(EquipmentSlot.FEET, gear(Items.IRON_BOOTS, Gem.of(GemSpecial.ACROBAT)));
        p.setHealth(p.getMaxHealth());
        Husk f = dummy(level, p, -2, 6, 500);
        float pf = p.getHealth(), ff = f.getHealth();
        p.setInvulnerableTime(0);
        p.hurtServer(level, level.damageSources().mobAttack(f), 8);
        log("thorns: player took {} husk took {} (20%)", pf - p.getHealth(), ff - f.getHealth());
        p.setInvulnerableTime(0);
        p.setHealth(5);
        p.hurtServer(level, level.damageSources().magic(), 100);
        log("phoenix: 100 damage at 5 HP -> health {} alive {} next at {}", p.getHealth(), p.isAlive(), p.getPersistentData().getLongOr(GemEffects.PHOENIX_NEXT, 0) - level.getGameTime());
        p.setHealth(p.getMaxHealth());
        p.setInvulnerableTime(0);
        float h0 = p.getHealth();
        p.causeFallDamage(10, 1, level.damageSources().fall());
        float withBoots = h0 - p.getHealth();
        p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        p.setHealth(p.getMaxHealth());
        p.setInvulnerableTime(0);
        h0 = p.getHealth();
        p.causeFallDamage(10, 1, level.damageSources().fall());
        log("acrobat: 10-block fall {} with the gem, {} without", withBoots, h0 - p.getHealth());

        // tailwind: a kill gives speed
        Husk k = dummy(level, p, 1, 4, 2);
        p.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.IRON_SWORD, Gem.of(GemSpecial.TAILWIND)));
        hit(level, p, k, 50);
        var speed = p.getEffect(MobEffects.SPEED);
        log("tailwind: killed={} speed amp={} ticks={}", !k.isAlive(), speed == null ? -1 : speed.getAmplifier(), speed == null ? 0 : speed.getDuration());

        // shockwave: a crit hits the husks around
        JobManager.testForceCrit = true;
        Husk s0 = dummy(level, p, 0, 9, 500), s1 = dummy(level, p, 1.5, 9, 500), s2 = dummy(level, p, -1.5, 9.5, 500);
        p.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.IRON_SWORD, Gem.of(GemSpecial.SHOCKWAVE)));
        hit(level, p, s0, 10);
        JobManager.testForceCrit = null;
        SkillTasks.later(3, () -> log("shockwave: target {} around {} and {}", 500 - s0.getHealth(), 500 - s1.getHealth(), 500 - s2.getHealth()));

        // endless arrows
        ItemStack bow = gear(com.jo112ss.rpgmod.item.ModItems.get(com.jo112ss.rpgmod.item.WeaponType.BOW, com.jo112ss.rpgmod.item.WeaponMaterial.IRON), Gem.of(GemSpecial.ENDLESS_ARROWS));
        p.setItemSlot(EquipmentSlot.MAINHAND, bow);
        int arrows = p.getInventory().countItem(Items.ARROW);
        var arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(level, p, new ItemStack(Items.ARROW), bow);
        arrow.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.ALLOWED;
        level.addFreshEntity(arrow);
        log("endless arrows: arrows {} -> {} pickup now {}", arrows, p.getInventory().countItem(Items.ARROW), arrow.pickup);
        arrow.discard();

        // regeneration: every 5 s
        p.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.IRON_SWORD, Gem.of(GemSpecial.REGENERATION)));
        p.setHealth(10);
        SkillTasks.later(101, () -> {
            log("regeneration: 10 -> {} after 5 s (2% of {} = {})", p.getHealth(), p.getMaxHealth(), p.getMaxHealth() * 0.02);
            GemEffects.testAlways = false;
            for (Husk h : List.of(a, b, d, e, f, s0, s1, s2)) h.discard();
            p.setItemSlot(EquipmentSlot.MAINHAND, mainBefore);
            p.setItemSlot(EquipmentSlot.HEAD, headBefore);
            p.setItemSlot(EquipmentSlot.CHEST, chestBefore);
            p.setItemSlot(EquipmentSlot.FEET, feetBefore);
            p.setHealth(p.getMaxHealth());
            p.getAbilities().invulnerable = wasInv;
            p.setGameMode(mode);
            p.onUpdateAbilities();
            level.getServer().setDifficulty(diff, true);
            p.setExperienceLevels(creative ? 0 : 30);
        });
    }

    // ------------------------------------------------------------------ screens and visuals

    private static void socket(ServerLevel level, ServerPlayer p) {
        BlockPos pos = p.blockPosition().west(2);
        level.setBlockAndUpdate(pos, ModRegistries.SOCKET_TABLE.get().defaultBlockState());
        p.setExperienceLevels(30);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> {
            SocketMenu m = new SocketMenu(id, inv, ContainerLevelAccess.create(level, pos));
            ItemStack sword = gear(Items.NETHERITE_SWORD, new Gem("attack_damage", Gem.COMMON, 6.4), Gem.of(GemSpecial.LIFESTEAL));
            m.testPut(sword, GemItems.stack(Gem.of(GemSpecial.THUNDER)));
            return m;
        }, net.minecraft.network.chat.Component.translatable("screen.rpgmod.socket")));
        log("socket screen opened");
    }

    private static void procs(ServerLevel level, ServerPlayer p, List<? extends LivingEntity> targets) {
        if (targets.isEmpty()) return;
        GemEffects.testAlways = true;
        p.setItemSlot(EquipmentSlot.MAINHAND, gear(Items.NETHERITE_SWORD, Gem.of(GemSpecial.THUNDER), Gem.of(GemSpecial.FREEZE), Gem.of(GemSpecial.IGNITE)));
        LivingEntity t = targets.get(0);
        // the dummies are fire resistant: show the burning on a fresh one
        Husk h = dummy(level, p, t.getX() - p.getX() + 1.6, t.getZ() - p.getZ(), 500);
        SkillTasks.later(2, () -> {
            hit(level, p, h, 4);
            hit(level, p, t, 4);
            GemEffects.testAlways = false;
        });
        SkillTasks.later(70, h::discard);
    }
}
