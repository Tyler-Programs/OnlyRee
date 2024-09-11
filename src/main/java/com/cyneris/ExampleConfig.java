package com.cyneris;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("example")
public interface ExampleConfig extends Config
{
	@ConfigItem(
		keyName = "greeting",
		name = "Welcome Greeting",
		description = "The message to show to the user when they login"
	)
	default String greeting()
	{
		return "Hello";
	}

	@ConfigSection(
			name = "Predicted hit",
			description = "Settings relating to predicted hit",
			position = 3,
			closedByDefault = true
	)
	String predicted_hit = "predicted_hit";

	@ConfigItem(
			keyName = "xpMultiplier",
			name = "Xp multiplier",
			description = "The bonus xp multiplier (from season game mode for example) that should be factored when calculating the hit",
			position = 23,
			section = predicted_hit
	)
	default double xpMultiplier()
	{
		return 1;
	}
}
