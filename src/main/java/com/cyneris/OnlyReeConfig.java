package com.cyneris;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.util.HashSet;
import java.util.Set;

@ConfigGroup("example")
public interface OnlyReeConfig extends Config {
    @ConfigItem(
            keyName = "xpMultiplier",
            name = "Xp multiplier",
            description = "The bonus xp multiplier (from season game mode for example) that should be factored when calculating the hit",
            position = 23
    )
    default double xpMultiplier() {
        return 1;
    }

    @ConfigItem(
            keyName = "ignoreNpcIds",
            name = "Excluded NPCs",
            description = "The npc ids to ignore"
    )
    default Set<Integer> ignoreNpcIds() {
        return new HashSet<>();
    }
}
