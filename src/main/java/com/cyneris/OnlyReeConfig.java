package com.cyneris;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("onlyree")
public interface OnlyReeConfig extends Config {
    @ConfigItem(
            keyName = "xpMultiplier",
            name = "Xp multiplier",
            description = "The bonus xp multiplier (from season game mode for example) that should be factored when calculating the hit",
            position = 1
    )
    default double xpMultiplier() {
        return 1;
    }

    @ConfigItem(
            keyName = "ignoreNpcIds",
            name = "Excluded NPCs",
            description = "Comma separated list of npc ids to ignore",
            position = 2
    )
    default String ignoreNpcIds() {
        return "";
    }
}
