package com.cyneris;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OnlyReePluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(OnlyReePlugin.class);
        RuneLite.main(args);
    }
}