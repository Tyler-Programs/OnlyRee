package com.cyneris;

import com.cyneris.predictedhit.AttackStyle;
import com.cyneris.predictedhit.Hit;
import com.cyneris.predictedhit.XpDropDamageCalculator;
import com.cyneris.predictedhit.npcswithscalingbonus.ChambersLayoutSolver;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemClient;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.specialcounter.SpecialCounterUpdate;
import net.runelite.client.plugins.specialcounter.SpecialWeapon;

import java.awt.event.ItemEvent;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Set;

@Slf4j
@PluginDescriptor(
	name = "Only Ree"
)
public class ExamplePlugin extends Plugin
{
	public static final int[] SKILL_PRIORITY = new int[] {1, 5, 2, 6, 3, 7, 4, 15, 17, 18, 0, 16, 11, 14, 13, 9, 8, 10, 19, 20, 12, 22, 21};
	private static final Set<Integer> VOIDWAKERS = new ImmutableSet.Builder<Integer>()
			.addAll(ItemVariationMapping.getVariations(ItemID.VOIDWAKER))
			.build();
	private static final int LEVIATHAN_ID = 12214;
	private static final int VARDORVIS_ID = 12223;

	@Getter
	private final ArrayDeque<Hit> hitBuffer = new ArrayDeque<>();
	@Getter
	private AttackStyle attackStyle;
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
	private ExampleConfig config;
//	@Inject
//	private Player player;

//	@Inject
//	private ItemManager itemManager;



	@Override
	protected void startUp() throws Exception
	{
		log.info("Example started!");

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() ->
			{
				int[] xps = client.getSkillExperiences();
				System.arraycopy(xps, 0, previous_exp, 0, previous_exp.length);
			});
		}
		else
		{
			Arrays.fill(previous_exp, 0);
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Example stopped!");
	}

	@Provides
	ExampleConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExampleConfig.class);
	}

	@Subscribe
	protected void onFakeXpDrop(FakeXpDrop event)
	{
//		log.info("Received fake xp drop");
		int currentXp = event.getXp();
		if (event.getXp() >= 20000000)
		{
			// fake-fake xp drop?
			return;
		}

		Player player = client.getLocalPlayer();
		int lastOpponentId = -1;
		Actor lastOpponent = null;
		if (player != null)
		{
			lastOpponent = player.getInteracting();
		}
		if (event.getSkill() == net.runelite.api.Skill.HITPOINTS)
		{
			int hit = 0;
			if (lastOpponent instanceof Player)
			{
				lastOpponentId = lastOpponent.getCombatLevel();
				hit = xpDropDamageCalculator.calculateHitOnPlayer(lastOpponent.getCombatLevel(), currentXp, config.xpMultiplier());
			}
			else if (lastOpponent instanceof NPC)
			{
				lastOpponentId = ((NPC) lastOpponent).getId();

				// Special case for Awakened DT2 Bosses
				if ((lastOpponentId == LEVIATHAN_ID || lastOpponentId == VARDORVIS_ID)
						&& lastOpponent.getCombatLevel() > 1000)
				{
					lastOpponentId *= -1;
				}

				hit = xpDropDamageCalculator.calculateHitOnNpc(lastOpponentId, currentXp, config.xpMultiplier());
				// TRL -- Hit > 100
				if (hit >= 100) {
					log.info("Hit NPC with >= 100");
					client.playSoundEffect(2911); // REEEEEE
				}
			}
			log.info("Hit npc with fake hp xp drop xp:{} hit:{} npc_id:{}", currentXp, hit, lastOpponentId);
			// Might not be using the hitbuffer, since we don't show xp drops
			//			hitBuffer.add(new Hit(hit, lastOpponent, attackStyle));
		}

//		XpDrop xpDrop = new XpDrop(Skill.fromSkill(event.getSkill()), currentXp, matchPrayerStyle(Skill.fromSkill(event.getSkill())), true, lastOpponent);
//		queue.add(xpDrop);
	}

	@Subscribe
	protected void onStatChanged(StatChanged event)
	{
//		log.info("Received stat changed drop");
		int currentXp = event.getXp();
		int previousXp = previous_exp[event.getSkill().ordinal()];
		if (previousXp > 0 && currentXp - previousXp > 0)
		{
			Player player = client.getLocalPlayer();
			int lastOpponentId = -1;
			Actor lastOpponent = null;
			if (player != null)
			{
				lastOpponent = player.getInteracting();
			}
			if (event.getSkill() == net.runelite.api.Skill.HITPOINTS)
			{
				int hit = 0;
				if (lastOpponent instanceof Player)
				{
					lastOpponentId = lastOpponent.getCombatLevel();
					hit = xpDropDamageCalculator.calculateHitOnPlayer(lastOpponentId, currentXp - previousXp, config.xpMultiplier());
				}
				else if (lastOpponent instanceof NPC)
				{
					lastOpponentId = ((NPC) lastOpponent).getId();
					hit = xpDropDamageCalculator.calculateHitOnNpc(lastOpponentId, currentXp - previousXp, config.xpMultiplier());
				}
				log.info("Hit npc with hp xp drop xp:{} hit:{} npc_id:{}", currentXp - previousXp, hit, lastOpponentId);
				// May not need hitBUffer since we don't show xp drops
				//				hitBuffer.add(new Hit(hit, lastOpponent, attackStyle));
				// TRL -- Hit > 100
				if (hit >= 100) {
					log.info("Hit NPC with >= 100");
					client.playSoundEffect(2911); // REEEEEE
				}
			}

			// May not need skill priority
//			XpDrop xpDrop = new XpDrop(Skill.fromSkill(event.getSkill()), currentXp - previousXp, matchPrayerStyle(Skill.fromSkill(event.getSkill())), false, lastOpponent);
//			queue.add(xpDrop);
		}

		previous_exp[event.getSkill().ordinal()] = event.getXp();
	}

	@Subscribe
	protected void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN || gameStateChanged.getGameState() == GameState.HOPPING)
		{
			Arrays.fill(previous_exp, 0);
			resetXpTrackerLingerTimerFlag = true;
		}
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN && resetXpTrackerLingerTimerFlag)
		{
			resetXpTrackerLingerTimerFlag = false;
//			xpDropOverlayManager.setLastSkillSetMillis(System.currentTimeMillis());
		}

		chambersLayoutSolver.onGameStateChanged(gameStateChanged);
	}

	// ------------------------------------------------------------------------------------------------

//	@Subscribe
//	public void onGameStateChanged(GameStateChanged gameStateChanged)
//	{
//		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
//		{
//			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Example says " + config.greeting(), null);
//			client.playSoundEffect(2911); // REEEEEE
//		}
//	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged) {
		// Account for delayed hits in a mutli-hit special attack
//		if (varbitChanged.getVarpId() != VarPlayer.SPECIAL_ATTACK_PERCENT) {
//			return;
//		}
//
//		var specWeapon = usedSpecialWeapon();
//		if (specWeapon == null) {
//			log.debug("Unrecognized spec weapon.");
//			return;
//		}
//
//		Actor interactedActor = client.getLocalPlayer().getInteracting();
//		var target = interactedActor instanceof NPC ? (NPC) interactedActor : null;
//
//		var hitsplatTick = serverTicks + getHitDelay(specWeapon, target);
//
//		var playerWeaponId = player.getPlayerComposition().getEquipmentId(KitType.WEAPON);
//		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Example says weapon is " + playerWeaponId, null);
//		var itemComp = itemManager.getItemComposition(playerWeaponId);
//		ItemClient itemClient; // can get item stats
//		ItemEvent itemEvent;
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event) {
		// Maintain a map of npc -> weapon held (if spec wep)
		var target = event.getTarget();
		NPC npc;
		ItemComposition itemComposition;
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied hitsplatApplied) {
		var hitsplat = hitsplatApplied.getHitsplat();
		var actor = hitsplatApplied.getActor();
		if (hitsplat.getAmount() > 0 && hitsplat.isMine() && actor instanceof NPC) {
			NPC npc = (NPC) actor;
			hitsplat.getDisappearsOnGameCycle();
		}
		SpecialCounterUpdate tmp;
		//tmp.getWeapon().getHitDelay();
	}

	private SpecialWeapon usedSpecialWeapon()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return null;
		}

		Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (weapon == null)
		{
			return null;
		}

		for (SpecialWeapon specialWeapon : SpecialWeapon.values())
		{
			if (Arrays.stream(specialWeapon.getItemID()).anyMatch(id -> id == weapon.getId()))
			{
				return specialWeapon;
			}
		}
		return null;
	}

	private int getHitDelay(SpecialWeapon specialWeapon, Actor target)
	{
		if (target == null)
			return 1;

		Player player = client.getLocalPlayer();
		if (player == null)
			return 1;

		WorldPoint playerWp = player.getWorldLocation();
		if (playerWp == null)
			return 1;

		WorldArea targetArea = target.getWorldArea();
		if (targetArea == null)
			return 1;

		final int distance = targetArea.distanceTo(playerWp);
		final int serverCycles = specialWeapon.getHitDelay(distance);

		if (serverCycles != 1)
		{
			log.debug("Projectile distance {} server cycles {}", distance, serverCycles);
		}

		return serverCycles;
	}
}
