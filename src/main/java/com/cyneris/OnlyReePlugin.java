package com.cyneris;

import com.cyneris.predictedhit.Hit;
import com.cyneris.predictedhit.XpDropDamageCalculator;
import com.cyneris.predictedhit.npcswithscalingbonus.ChambersLayoutSolver;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.FakeXpDrop;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "Only Ree"
)
public class OnlyReePlugin extends Plugin {
    public static final int[] SKILL_PRIORITY = new int[]{1, 5, 2, 6, 3, 7, 4, 15, 17, 18, 0, 16, 11, 14, 13, 9, 8, 10, 19, 20, 12, 22, 21};
    private static final Set<Integer> VOIDWAKERS = new ImmutableSet.Builder<Integer>()
            .addAll(ItemVariationMapping.getVariations(ItemID.VOIDWAKER))
            .build();
    private static final int LEVIATHAN_ID = 12214;
    private static final int VARDORVIS_ID = 12223;

    @Getter
    private final ArrayDeque<Hit> hitBuffer = new ArrayDeque<>();
    @Inject
    private XpDropDamageCalculator xpDropDamageCalculator;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ChambersLayoutSolver chambersLayoutSolver;
    private static final int[] previous_exp = new int[Skill.values().length - 1];
    private boolean resetXpTrackerLingerTimerFlag = false;

    // ---------------------------------------------------------------------------

    @Inject
    private Client client;
    @Inject
    private OnlyReeConfig config;

    private final Set<Integer> npcsToIgnore = new HashSet<>();


    @Override
    protected void startUp() throws Exception {
        if (client.getGameState() == GameState.LOGGED_IN) {
            // -------------- Testing ignore fields -----------------------
            addAllIgnoredNpcIds();
            // ------------------------------------------------------------
            clientThread.invokeLater(() ->
            {
                int[] xps = client.getSkillExperiences();
                System.arraycopy(xps, 0, previous_exp, 0, previous_exp.length);
            });
        } else {
            Arrays.fill(previous_exp, 0);
            npcsToIgnore.clear();
        }
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Example stopped!");
    }

    @Provides
    OnlyReeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(OnlyReeConfig.class);
    }

    @Subscribe
    protected void onConfigChanged(ConfigChanged configChanged) {
        if (configChanged.getGroup().equals("onlyree")) {
            npcsToIgnore.clear();
            addAllIgnoredNpcIds();
        }
    }

    @Subscribe
    protected void onFakeXpDrop(FakeXpDrop event) {
        if (event.getSkill() != Skill.HITPOINTS || event.getXp() >= 20000000) {
            return;
        }

        int currentXp = event.getXp();

        Player player = client.getLocalPlayer();
        int lastOpponentId = -1;
        Actor lastOpponent = null;
        if (player != null) {
            lastOpponent = player.getInteracting();
        }

        int hit = 0;
        if (lastOpponent instanceof Player) {
            lastOpponentId = lastOpponent.getCombatLevel();
            hit = xpDropDamageCalculator.calculateHitOnPlayer(lastOpponent.getCombatLevel(), currentXp, config.xpMultiplier());
        } else if (lastOpponent instanceof NPC) {
            lastOpponentId = ((NPC) lastOpponent).getId();

            // User is actively ignoring this NPC
            if (npcsToIgnore.contains(lastOpponentId)) {
                return;
            }

            // Special case for Awakened DT2 Bosses
            if ((lastOpponentId == LEVIATHAN_ID || lastOpponentId == VARDORVIS_ID)
                    && lastOpponent.getCombatLevel() > 1000) {
                lastOpponentId *= -1;
            }

            hit = xpDropDamageCalculator.calculateHitOnNpc(lastOpponentId, currentXp, config.xpMultiplier());

            if (hit >= 100) {
                client.playSoundEffect(2911); // REEEEEE
            }
        }
    }

    @Subscribe
    protected void onStatChanged(StatChanged event) {
        if (event.getSkill() != Skill.HITPOINTS) {
            return;
        }

        int currentXp = event.getXp();
        int previousXp = previous_exp[event.getSkill().ordinal()];
        if (previousXp > 0 && currentXp - previousXp > 0) {

            Player player = client.getLocalPlayer();
            int lastOpponentId = -1;
            Actor lastOpponent = null;
            if (player != null) {
                lastOpponent = player.getInteracting();
            }

            int hit = 0;
            if (lastOpponent instanceof Player) {
                lastOpponentId = lastOpponent.getCombatLevel();
                hit = xpDropDamageCalculator.calculateHitOnPlayer(lastOpponentId, currentXp - previousXp, config.xpMultiplier());
            } else if (lastOpponent instanceof NPC) {
                lastOpponentId = ((NPC) lastOpponent).getId();

                // User is actively ignoring this NPC
                if (npcsToIgnore.contains(lastOpponentId)) {
                    return;
                }

                hit = xpDropDamageCalculator.calculateHitOnNpc(lastOpponentId, currentXp - previousXp, config.xpMultiplier());
            }

            if (hit >= 100) {
                client.playSoundEffect(2911); // REEEEEE
            }
        }

        previous_exp[event.getSkill().ordinal()] = event.getXp();
    }

    @Subscribe
    protected void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN || gameStateChanged.getGameState() == GameState.HOPPING) {
            Arrays.fill(previous_exp, 0);
            resetXpTrackerLingerTimerFlag = true;
        }
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN && resetXpTrackerLingerTimerFlag) {
            resetXpTrackerLingerTimerFlag = false;
        }

        chambersLayoutSolver.onGameStateChanged(gameStateChanged);
    }


    // ------------------------------------------------------------------------------------------------
    private boolean isNumeric(String input) {
        log.info(input);
        return input.isEmpty() || input.isBlank() || !input.matches("$[0-9]*^");
    }

    private void addAllIgnoredNpcIds() {
        if (config.ignoreNpcIds().length() == 0) {
            return;
        }

        var idsAsStrings = config.ignoreNpcIds().split(",");
        var allIgnoredIds = Arrays.stream(idsAsStrings)
                .map(String::trim)
                .filter(this::isNumeric)
                .peek(str -> log.info("numeric found: " + str))
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
        log.info("Adding All ignored npc ids: " + allIgnoredIds + "\n");
        npcsToIgnore.addAll(allIgnoredIds);
    }
}
