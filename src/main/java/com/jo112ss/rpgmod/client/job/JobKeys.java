package com.jo112ss.rpgmod.client.job;

import com.jo112ss.rpgmod.RpgMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Key bindings (changeable in Controls): skills Z / X, ultimate C, status K, spell wheel V. */
public final class JobKeys {
    private JobKeys() {}

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(RpgMod.MODID, "main"));

    public static final KeyMapping SKILL_1 = new KeyMapping("key.rpgmod.skill_1", InputConstants.Type.KEYSYM, InputConstants.KEY_Z, CATEGORY);
    public static final KeyMapping SKILL_2 = new KeyMapping("key.rpgmod.skill_2", InputConstants.Type.KEYSYM, InputConstants.KEY_X, CATEGORY);
    public static final KeyMapping ULTIMATE = new KeyMapping("key.rpgmod.ultimate", InputConstants.Type.KEYSYM, InputConstants.KEY_C, CATEGORY);
    public static final KeyMapping STATUS = new KeyMapping("key.rpgmod.status", InputConstants.Type.KEYSYM, InputConstants.KEY_K, CATEGORY);
    public static final KeyMapping SPELL_WHEEL = new KeyMapping("key.rpgmod.spell_wheel", InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY);

    public static final KeyMapping[] SKILLS = {SKILL_1, SKILL_2, ULTIMATE};

    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(SKILL_1);
        event.register(SKILL_2);
        event.register(ULTIMATE);
        event.register(STATUS);
        event.register(SPELL_WHEEL);
    }
}
