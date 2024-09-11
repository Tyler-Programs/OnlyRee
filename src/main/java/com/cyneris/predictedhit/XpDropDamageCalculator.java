package com.cyneris.predictedhit;

/**
 *
 * Copyright (c) 2021, l2-
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import com.cyneris.predictedhit.npcswithscalingbonus.ChambersLayoutSolver;
import com.cyneris.predictedhit.npcswithscalingbonus.cox.CoXNPCs;
import com.cyneris.predictedhit.npcswithscalingbonus.toa.ToANPCs;
import com.cyneris.predictedhit.npcswithscalingbonus.tob.ToBNPCs;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
public class XpDropDamageCalculator
{
    private static final String NPC_JSON_FILE = "npcs.min.json";
    private static final HashMap<Integer, Double> XP_BONUS_MAPPING = new HashMap<>();
//    private static final HashMap<Integer, Double> UNKNOWN_XP_BONUS_MAPPING = new HashMap<>();
    private static final Pattern RAID_LEVEL_MATCHER = Pattern.compile("(\\d+)");
    private static final int RAID_LEVEL_WIDGET_ID = (481 << 16) | 42;
    private static final int ROOM_LEVEL_WIDGET_ID = (481 << 16) | 45;
    private static final int COX_SCALED_PARTY_SIZE_VARBIT = 9540;

    private int lastToARaidLevel = 0;
    private int lastToARaidPartySize = 1;
    private int lastToARaidRoomLevel = 0;

    private final Gson GSON;
    private final Client client;
    private final ChambersLayoutSolver chambersLayoutSolver;
    private final boolean shouldEstimateXpModifier = false;

    @Inject
    protected XpDropDamageCalculator(Gson gson, Client client, ChambersLayoutSolver chambersLayoutSolver)
    {
        this.GSON = gson;
        this.client = client;
        this.chambersLayoutSolver = chambersLayoutSolver;
    }

    public void populateMap()
    {
        XP_BONUS_MAPPING.clear();
        XP_BONUS_MAPPING.putAll(getNpcsWithXpBonus());
    }

    private int getCoxTotalPartySize()
    {
        return Math.max(1, client.getVarbitValue(COX_SCALED_PARTY_SIZE_VARBIT));
    }

    // Currently it checks a varbit for the amount of players in the raid.
    // Ideally this method returns how many non board scaling accounts started the raid.
    private int getCoxPlayersInRaid()
    {
        return Math.max(1, client.getVarbitValue(Varbits.RAID_PARTY_SIZE));
    }

    private int getToBPartySize()
    {
        int count = 0;
        for (int i = 330; i < 335; i++)
        {
            String jagexName = client.getVarcStrValue(i);
            if (jagexName != null)
            {
                String name = Text.removeTags(jagexName).replace('\u00A0', ' ').trim();
                if (!"".equals(name))
                {
                    count++;
                }
            }
        }
        return count;
    }

    private int getToAPartySize()
    {
        return 1 +
                (client.getVarbitValue(Varbits.TOA_MEMBER_1_HEALTH) != 0 ? 1 : 0) +
                (client.getVarbitValue(Varbits.TOA_MEMBER_2_HEALTH) != 0 ? 1 : 0) +
                (client.getVarbitValue(Varbits.TOA_MEMBER_3_HEALTH) != 0 ? 1 : 0) +
                (client.getVarbitValue(Varbits.TOA_MEMBER_4_HEALTH) != 0 ? 1 : 0) +
                (client.getVarbitValue(Varbits.TOA_MEMBER_5_HEALTH) != 0 ? 1 : 0) +
                (client.getVarbitValue(Varbits.TOA_MEMBER_6_HEALTH) != 0 ? 1 : 0) +
                (client.getVarbitValue(Varbits.TOA_MEMBER_7_HEALTH) != 0 ? 1 : 0);
    }

    private int getToARaidLevel()
    {
        return client.getVarbitValue(Varbits.TOA_RAID_LEVEL);
    }

    private int getToARoomLevel()
    {
        Widget levelWidget = client.getWidget(ROOM_LEVEL_WIDGET_ID);
        if (levelWidget != null && !levelWidget.isHidden())
        {
            try
            {
                return Integer.parseInt(Text.sanitize(levelWidget.getText()));
            }
            catch (Exception ignored) {}
        }
        return -1;
    }

    private int calculateHit(int hpXpDiff, double modifier, double configModifier)
    {
        if (Math.abs(configModifier) < 1e-6)
        {
            configModifier = 1e-6;
        }

        if (modifier < 1e-6)
        {
            return 0;
        }
        return (int) Math.round((hpXpDiff * (3.0d / 4.0d)) / modifier / configModifier);
    }

    public int calculateHitOnPlayer(int cmb, int hpXpDiff, double configModifier)
    {
        double modifier = Math.min(1.125d, 1 + Math.floor(cmb / 20.0d) / 40.0d);
        return calculateHit(hpXpDiff, modifier, configModifier);
    }

    public int calculateHitOnNpc(int id, int hpXpDiff, double configModifier)
    {
        double modifier = 1.0;
        if (CoXNPCs.isCOXNPC(id))
        {
            int scaledPartySize = getCoxTotalPartySize();
            int playersInRaid = getCoxPlayersInRaid();
            // Wrong. only follows the setting of the player's board
//			int raidType = client.getVarbitValue(6385) > 0 ? 1 : 0;
            int raidType = chambersLayoutSolver.isCM() ? 1 : 0;

            modifier = CoXNPCs.getModifier(id, scaledPartySize, playersInRaid, raidType);
            log.debug("COX modifier {} {} party size {} players in raid {} raid type {}", id, modifier, scaledPartySize, playersInRaid, raidType);
        }
        else if (ToBNPCs.isTOBNPC(id))
        {
            int partySize = getToBPartySize();
            modifier = ToBNPCs.getModifier(id, partySize);
            log.debug("TOB modifier {} {} part size {}", id, modifier, partySize);
        }
        else if (ToANPCs.isToANPC(id))
        {
            int partySize = getToAPartySize();
            int roomLevel = getToARoomLevel();
            int raidLevel = getToARaidLevel();
            // If we cannot determine any of the above; use last known settings.
            if (partySize < 0) partySize = lastToARaidPartySize; else lastToARaidPartySize = partySize;
            if (roomLevel < 0) roomLevel = lastToARaidRoomLevel; else lastToARaidRoomLevel = roomLevel;
            if (raidLevel < 0) raidLevel = lastToARaidLevel; else lastToARaidLevel = raidLevel;
            modifier = ToANPCs.getModifier(id, partySize, raidLevel, roomLevel);
            log.debug("TOA modifier {} {} party size {} raid level {} room level {}", id, modifier, partySize, raidLevel, roomLevel);
        }
        else if (XP_BONUS_MAPPING.containsKey(id))
        {
            modifier = XP_BONUS_MAPPING.get(id);
        }
        return calculateHit(hpXpDiff, modifier, configModifier);
    }

    // Don't do this in static block since we may want finer control of when it happens for a possibly long blocking
    // operation like this.
    private HashMap<Integer, Double> getNpcsWithXpBonus()
    {
        HashMap<Integer, Double> map1 = new HashMap<>();
        try
        {
            try (InputStream resource = XpDropDamageCalculator.class.getResourceAsStream(NPC_JSON_FILE))
            {
                BufferedReader reader = new BufferedReader(new InputStreamReader(resource,
                        StandardCharsets.UTF_8));
                Object jsonResult = GSON.fromJson(reader, Map.class);
                try
                {
                    Map<String, LinkedTreeMap<String, Double>> map = (Map<String, LinkedTreeMap<String, Double>>) jsonResult;
                    for (String id : map.keySet())
                    {
                        LinkedTreeMap<String, Double> result = map.get(id);
                        for (String key : result.keySet())
                        {
                            Double xpbonus = result.get(key);
                            xpbonus = (xpbonus + 100) / 100.0d;
                            map1.put(Integer.parseInt(id), xpbonus);
                        }
                    }
                }
                catch (ClassCastException castException)
                {
                    log.warn("Invalid json. Casting to expected hierarchy failed", castException);
                }
            }
        }
        catch (IOException e)
        {
            log.warn("Couldn't open NPC json file", e);
        }

        return map1;
    }
}