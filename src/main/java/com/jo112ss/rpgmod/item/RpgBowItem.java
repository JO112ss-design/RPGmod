package com.jo112ss.rpgmod.item;

import com.jo112ss.rpgmod.job.JobManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.Level;

/**
 * A material bow. Its arrows deal a flat bonus on top of a normal bow (reached at full draw);
 * the bonus is applied when the arrow hits, see ForgeOptionEffects.
 */
public class RpgBowItem extends BowItem {
    private final WeaponMaterial material;

    public RpgBowItem(WeaponMaterial material, Properties properties) {
        super(properties);
        this.material = material;
    }

    public WeaponMaterial material() {
        return material;
    }

    /** Only jobs that use bows (ranger, gambler) can draw material bows. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!JobManager.canUseBow(player)) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("rpgmod.job.bow_restricted").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        return super.use(level, player, hand);
    }

    public int damageBonus() {
        return material.bowBonus;
    }

    /** Armour penetration (%) every bow of this material has: copper 10, iron 15, gold 20, diamond 25, netherite 30. */
    public int armorPenetration() {
        return 10 + 5 * material.ordinal();
    }

    /** Base armour penetration (%) of a bow stack, 0 for anything else. */
    public static double basePenetration(net.minecraft.world.item.ItemStack stack) {
        return stack != null && stack.getItem() instanceof RpgBowItem b ? b.armorPenetration() : 0;
    }
}
