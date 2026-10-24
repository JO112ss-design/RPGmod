package com.jo112ss.rpgmod.client.job;

import com.jo112ss.rpgmod.RpgMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Key bindings (changeable in Controls): skills Z / X (/ B with the advanced job), ultimate C, status K, spell wheel V. */
public final class JobKeys {
    private JobKeys() {}

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(RpgMod.MODID, "main"));

    public static final KeyMapping SKILL_1 = new KeyMapping("key.rpgmod.skill_1", InputConstants.Type.KEYBOARD, InputConstants.KEY_Z, CATEGORY);
    public static final KeyMapping SKILL_2 = new KeyMapping("key.rpgmod.skill_2", InputConstants.Type.KEYBOARD, InputConstants.KEY_X, CATEGORY);
    public static final KeyMapping ULTIMATE = new KeyMapping("key.rpgmod.ultimate", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, CATEGORY);
    public static final KeyMapping SKILL_3 = new KeyMapping("key.rpgmod.skill_3", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, CATEGORY);
    public static final KeyMapping STATUS = new KeyMapping("key.rpgmod.status", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY);
    public static final KeyMapping SPELL_WHEEL = new KeyMapping("key.rpgmod.spell_wheel", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY);
    /** Spellmaster: hold to charge the left-hand spell (left click by default; then the staff does not attack). */
    public static final KeyMapping LEFT_SPELL = new KeyMapping("key.rpgmod.left_spell", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_LEFT, CATEGORY);

    /** Indexed by skill slot: 0 = skill 1, 1 = skill 2, 2 = ultimate, 3 = skill 3 (advanced job). */
    public static final KeyMapping[] SKILLS = {SKILL_1, SKILL_2, ULTIMATE, SKILL_3};

    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(SKILL_1);
        event.register(SKILL_2);
        event.register(ULTIMATE);
        event.register(SKILL_3);
        event.register(STATUS);
        event.register(SPELL_WHEEL);
        event.register(LEFT_SPELL);
    }
}
