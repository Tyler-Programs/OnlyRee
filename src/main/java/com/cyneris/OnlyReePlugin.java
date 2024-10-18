package com.cyneris;

import com.cyneris.predictedhit.Hit;
import com.cyneris.predictedhit.SpellUtil;
import com.cyneris.predictedhit.XpDropDamageCalculator;
import com.cyneris.predictedhit.npcswithscalingbonus.ChambersLayoutSolver;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

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
    private static final Set<Integer> CROSSBOWS = new ImmutableSet.Builder<Integer>() // All crossbows that can shoot addy bolts or higher
            .add(ItemID.ADAMANT_CROSSBOW)
            .addAll(ItemVariationMapping.getVariations(ItemID.RUNE_CROSSBOW))
            .addAll(ItemVariationMapping.getVariations(ItemID.DRAGON_CROSSBOW))
            .addAll(ItemVariationMapping.getVariations(ItemID.DRAGON_HUNTER_CROSSBOW))
            .addAll(ItemVariationMapping.getVariations(ItemID.ARMADYL_CROSSBOW))
            .addAll(ItemVariationMapping.getVariations(ItemID.ZARYTE_CROSSBOW))
            .build();

    private static final Set<Integer> AOE_RANGE_WEAPONS = new ImmutableSet.Builder<Integer>() // All range weapons that can hit multiple targets
            .addAll(ItemVariationMapping.getVariations(ItemID.VENATOR_BOW))
            .add(ItemID.CHINCHOMPA)
            .add(ItemID.RED_CHINCHOMPA)
            .add(ItemID.BLACK_CHINCHOMPA)
            .build();

    private static final Set<Integer> AUTOCASTING_WEAPONS = new ImmutableSet.Builder<Integer>() // Weapons that can autocast ancients
            .addAll(ItemVariationMapping.getVariations(ItemID.ANCIENT_STAFF))
            .addAll(ItemVariationMapping.getVariations(ItemID.ANCIENT_SCEPTRE))
            .addAll(ItemVariationMapping.getVariations(ItemID.BLOOD_ANCIENT_SCEPTRE))
            .addAll(ItemVariationMapping.getVariations(ItemID.ICE_ANCIENT_SCEPTRE))
            .addAll(ItemVariationMapping.getVariations(ItemID.SHADOW_ANCIENT_SCEPTRE))
            .addAll(ItemVariationMapping.getVariations(ItemID.SMOKE_ANCIENT_SCEPTRE))
            .addAll(ItemVariationMapping.getVariations(ItemID.AHRIMS_STAFF))
            .addAll(ItemVariationMapping.getVariations(ItemID.MASTER_WAND))
            .addAll(ItemVariationMapping.getVariations(ItemID.KODAI_WAND))
            .addAll(ItemVariationMapping.getVariations(ItemID.NIGHTMARE_STAFF))
            .addAll(ItemVariationMapping.getVariations(ItemID.ELDRITCH_NIGHTMARE_STAFF))
            .addAll(ItemVariationMapping.getVariations(ItemID.VOLATILE_NIGHTMARE_STAFF))
            .addAll(ItemVariationMapping.getVariations(ItemID.THAMMARONS_SCEPTRE))
            .addAll(ItemVariationMapping.getVariations(ItemID.ACCURSED_SCEPTRE))
            .addAll(ItemVariationMapping.getVariations(ItemID.BLUE_MOON_SPEAR))
            .build();

    private static final Set<Integer> QUIVERS = new ImmutableSet.Builder<Integer>()
            .addAll(ItemVariationMapping.getVariations(ItemID.DIZANAS_MAX_CAPE))
            .addAll(ItemVariationMapping.getVariations(ItemID.DIZANAS_QUIVER))
            .addAll(ItemVariationMapping.getVariations(ItemID.BLESSED_DIZANAS_QUIVER))
            .build();
    private static final int LEVIATHAN_ID = 12214;
    private static final int VARDORVIS_ID = 12223;
    private static final int AUTO_CAST_VARBIT_ID = 276;

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
    private boolean isAoeManualCasting = false; // Flag for manually barraging/bursting
    private int autocastingSpellId = 0;

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
            var weaponId = player.getPlayerComposition().getEquipmentId(KitType.WEAPON);

            // Check if we should ignore this damage due to config settings
            if (skipSoundEffect(player, weaponId)) return;

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
        isAoeManualCasting = false;
    }

    @Subscribe
    protected void onMenuOptionClicked(MenuOptionClicked event) {
        String menuOption = Text.removeTags(event.getMenuOption());
        String menuTarget = Text.removeTags(event.getMenuTarget());
        isAoeManualCasting = menuOption.contains("Cast")
                && (menuTarget.contains("Burst ->") || menuTarget.contains("Barrage ->"));
    }

    @Subscribe
    protected void onVarbitChanged(VarbitChanged event) {
        if (event.getVarbitId() == AUTO_CAST_VARBIT_ID) {
            autocastingSpellId = client.getVarbitValue(AUTO_CAST_VARBIT_ID);
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
                var weaponId = player.getPlayerComposition().getEquipmentId(KitType.WEAPON);

                // Check if we should ignore this damage due to config settings
                if (skipSoundEffect(player, weaponId)) return;

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
        isAoeManualCasting = false;
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

    private boolean skipSoundEffect(Player player, int weaponId) {
        // Natural ruby proc
        if (CROSSBOWS.contains(weaponId)) {
            var dizanaAmmoId = client.getVarpValue(VarPlayer.DIZANAS_QUIVER_ITEM_ID); // Ammo in the dizana's quiver
            var ammoItem = client.getItemContainer(InventoryID.EQUIPMENT).getItem(EquipmentInventorySlot.AMMO.getSlotIdx());
            if (ammoItem.getId() == ItemID.RUBY_BOLTS_E || ammoItem.getId() == ItemID.RUBY_DRAGON_BOLTS_E) {
                return true;
            } else {
                return QUIVERS.contains(player.getPlayerComposition().getEquipmentId(KitType.CAPE))
                        && (dizanaAmmoId == ItemID.RUBY_BOLTS_E || dizanaAmmoId == ItemID.RUBY_DRAGON_BOLTS_E);
            }

        }

        // AOE is disabled
        if (!config.triggerOnAoe()) {
            // Range weapons
            if (AOE_RANGE_WEAPONS.contains(weaponId)) return true;

            // Manual AoE spell
            if (isAoeManualCasting) return true;

            // Autocast AoE spell
            if (AUTOCASTING_WEAPONS.contains(weaponId) && SpellUtil.isAoE(autocastingSpellId))
                return true;
        }
        return false;
    }
}
