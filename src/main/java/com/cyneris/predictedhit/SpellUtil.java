package com.cyneris.predictedhit;

import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Set;

public class SpellUtil {
    /**
     * The currently selected autocast spell.
     * 0        = no spell selected.
     * <p>
     * 1 -> 4   = wind, water, earth, fire strike
     * 5 -> 8   = wind, water, earth, fire bolt
     * 9 -> 12  = wind, water, earth, fire blast
     * 13 -> 16 = wind, water, earth, fire wave
     * 48 -> 51 = wind, water, earth, fire surge
     * <p>
     * 31 -> 34 = Smoke, Shadow, Blood, Ice Rush
     * 35 -> 38 = Smoke, Shadow, Blood, Ice Burst
     * 39 -> 42 = Smoke, Shadow, Blood, Ice Blitz
     * 43 -> 46 = Smoke, Shadow, Blood, Ice Barrage
     * <p>
     * 53 -> 55 = Inferior, Superior, Dark Demonbane
     * 56 -> 58 = Ghostly, Skeletal, Undead Grasp
     * <p>
     * 47       = Iban's blast
     * 17       = Crumble Undead
     * 18       = Magic Dart
     * 19       = Claws of Guthix
     * 20       = Flames of Zamorak
     * 52       = Saradomin Strike
     */
    private static final Set<Integer> AOE_SPELL_IDS = new ImmutableSet.Builder<Integer>()
            .addAll(List.of(35, 36, 37, 38, 43, 44, 45, 46))
            .build();

    public static boolean isAoE(int spellId) {
        return AOE_SPELL_IDS.contains(spellId);
    }

}
