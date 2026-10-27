package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.RpgMod;
import com.jo112ss.rpgmod.mob.Elite;
import com.jo112ss.rpgmod.mob.boss.BloodMoon;
import com.jo112ss.rpgmod.mob.revenant.Revenant;
import com.jo112ss.rpgmod.mob.revenant.Revenant.Role;
import com.jo112ss.rpgmod.mob.revenant.RevenantSpawns;
import com.jo112ss.rpgmod.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Spawn eggs for the mod's own monsters, for trying them out: the four bosses, a fallen adventurer of
 * each job, and a whole party of them. Use one on a block like a vanilla egg.
 * <ul>
 * <li>a boss from an egg stays when the day comes (it does not go away like a blood-moon boss)</li>
 * <li>a fallen adventurer is never an elite by chance; used while sneaking it comes as an elite with three
 * traits (in a party, the first of them)</li>
 * </ul>
 * Levels follow the players nearby like any monster's.
 */
public class RpgSpawnEggItem extends Item {
    public enum Kind {
        KING_SLIME(0x5FD8FF, 0x2A7FC0), GOLEM(0x3A3A40, 0xFF4020), LICH(0x3A1E5A, 0x7FE8FF), SPIDER_QUEEN(0x17121F, 0xC060FF),
        WARRIOR(0x4E6B4A, 0xC0C8D8), MAGE(0x4E6B4A, 0x9A6AFF), ARCHER(0x4E6B4A, 0x6FBF5A), PRIEST(0x4E6B4A, 0xFFE6A0), THIEF(0x4E6B4A, 0x6A6A80),
        PARTY(0x4E6B4A, 0xE0B040);

        public final int base, spots;

        Kind(int base, int spots) {
            this.base = base;
            this.spots = spots;
        }

        public boolean boss() {
            return ordinal() <= SPIDER_QUEEN.ordinal();
        }

        /** The item's id: {@code king_slime_spawn_egg}, {@code revenant_mage_spawn_egg}, {@code revenant_party_spawn_egg}... */
        public String itemId() {
            String n = name().toLowerCase(java.util.Locale.ROOT);
            return (boss() ? n : "revenant_" + n) + "_spawn_egg";
        }

        Role role() {
            return switch (this) {
                case WARRIOR -> Role.WARRIOR;
                case MAGE -> Role.MAGE;
                case ARCHER -> Role.ARCHER;
                case PRIEST -> Role.PRIEST;
                case THIEF -> Role.THIEF;
                default -> null;
            };
        }

        EntityType<? extends Mob> bossType() {
            return switch (this) {
                case KING_SLIME -> ModRegistries.KING_SLIME.get();
                case GOLEM -> ModRegistries.GOLEM.get();
                case LICH -> ModRegistries.LICH.get();
                case SPIDER_QUEEN -> ModRegistries.SPIDER_QUEEN.get();
                default -> null;
            };
        }
    }

    /** Tag on a boss that came from an egg: it does not leave at dawn. */
    public static final String SUMMONED = "rpgmod_egg_summoned";

    private final Kind kind;

    public RpgSpawnEggItem(Item.Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        BlockPos at = state.getCollisionShape(level, pos).isEmpty() ? pos : pos.relative(context.getClickedFace());
        Player player = context.getPlayer();
        boolean elite = player != null && player.isShiftKeyDown();
        List<? extends Mob> spawned = spawn(sl, at, player, elite);
        if (spawned.isEmpty()) return InteractionResult.FAIL;
        context.getItemInHand().consume(1, player);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
        return InteractionResult.SUCCESS;
    }

    /** Spawns what this egg holds at {@code at}; returns what came. */
    public List<? extends Mob> spawn(ServerLevel sl, BlockPos at, Player user, boolean elite) {
        Vec3 v = new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        float yaw = user == null ? sl.getRandom().nextFloat() * 360
                : (float) (Mth.atan2(user.getZ() - v.z, user.getX() - v.x) * Mth.RAD_TO_DEG) - 90F;
        if (kind.boss()) {
            Mob boss = kind.bossType().create(sl, EntitySpawnReason.SPAWN_ITEM_USE);
            if (boss == null) return List.of();
            boss.snapTo(v.x, v.y, v.z, yaw, 0);
            // room to stand: step up a little out of the ground or a wall if needed
            for (int up = 0; up < 6 && !sl.noCollision(boss); up++) boss.snapTo(v.x, v.y + up + 1, v.z, yaw, 0);
            if (!sl.noCollision(boss)) boss.snapTo(v.x, v.y, v.z, yaw, 0);
            boss.addTag(SUMMONED);
            boss.setPersistenceRequired();
            boss.setYHeadRot(yaw);
            sl.addFreshEntity(boss);
            BloodMoon.arrivalFx(sl, boss);
            RpgMod.LOGGER.info("[spawn egg] {} at {}", BloodMoon.key(boss.getType()), boss.blockPosition());
            return List.of(boss);
        }
        List<Role> roles = new ArrayList<>();
        if (kind == Kind.PARTY) {
            roles.add(Role.WARRIOR);
            roles.add(Role.MAGE);
            roles.add(sl.getRandom().nextBoolean() ? Role.ARCHER : Role.THIEF);
            roles.add(Role.PRIEST);
        } else {
            roles.add(kind.role());
        }
        // sneaking: an elite (in a party, only the first of them, as in a dungeon)
        if (elite) Elite.forceNext = eliteMask(sl.getRandom(), 3);
        Elite.noChance = true; // otherwise never an elite: what the egg says is what comes
        try {
            List<Revenant> out = RevenantSpawns.spawnGroup(sl, v, roles, EntitySpawnReason.SPAWN_ITEM_USE);
            for (Revenant r : out) {
                r.setPersistenceRequired();
                r.setYRot(yaw);
                r.setYHeadRot(yaw);
                r.setYBodyRot(yaw);
            }
            return out;
        } finally {
            Elite.forceNext = null;
            Elite.noChance = false;
        }
    }

    /** {@code n} different elite traits at random. */
    public static int eliteMask(RandomSource rnd, int n) {
        List<Elite.Trait> pool = new ArrayList<>(List.of(Elite.Trait.values()));
        int mask = 0;
        for (int i = 0; i < n && !pool.isEmpty(); i++) mask |= pool.remove(rnd.nextInt(pool.size())).bit();
        return mask;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        if (kind.boss()) {
            builder.accept(Component.translatable("item.rpgmod.spawn_egg.boss_tip").withStyle(ChatFormatting.GRAY));
        } else {
            builder.accept(Component.translatable("item.rpgmod.spawn_egg.elite_tip").withStyle(ChatFormatting.GRAY));
        }
    }
}
