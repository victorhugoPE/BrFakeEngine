package ext.mods.fakeplayer.thirdclasses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ext.mods.gameserver.enums.items.ShotType;

import ext.mods.fakeplayer.FakePlayer;
import ext.mods.fakeplayer.ai.CombatAI;
import ext.mods.fakeplayer.ai.addon.IConsumableSpender;
import ext.mods.fakeplayer.ai.addon.IPotionUser;
import ext.mods.fakeplayer.helper.FakePlayerHelpers;
import ext.mods.fakeplayer.model.HealingSpell;
import ext.mods.fakeplayer.model.OffensiveSpell;
import ext.mods.fakeplayer.model.SupportSpell;

public class WindRiderAI extends CombatAI implements IConsumableSpender, IPotionUser
{
	private final int CPPotion = 5592;
	private final int HPPotion = 1540;
	private final int MPPotion = 728;
	
	private final List<OffensiveSpell> _offensiveSpells;

	public WindRiderAI(FakePlayer character)
	{
		super(character);
		
		List<OffensiveSpell> offensive = new ArrayList<>();
		offensive.add(new OffensiveSpell(263));
		offensive.add(new OffensiveSpell(12));
		offensive.add(new OffensiveSpell(410));
		offensive.add(new OffensiveSpell(102));
		offensive.add(new OffensiveSpell(321));
		offensive.add(new OffensiveSpell(344));
		offensive.add(new OffensiveSpell(358));
		_offensiveSpells = Collections.unmodifiableList(offensive);
	}
	
	@Override
	public void thinkAndAct()
	{
		super.thinkAndAct();
		setBusyThinking(true);
		applyDefaultBuffs();
		handleConsumable(_fakePlayer, CPPotion);
		handleConsumable(_fakePlayer, HPPotion);
		handleConsumable(_fakePlayer, MPPotion);
		handlePotions(_fakePlayer, CPPotion, HPPotion, MPPotion);
		handleShots();
		tryTargetNearestCreatureByTypeInRadius(FakePlayerHelpers.getTargetClass(), FakePlayerHelpers.getTargetRange());
		tryAttackingUsingFighterOffensiveSkill();
		setBusyThinking(false);
	}
	
	@Override
	protected ShotType getShotType()
	{
		return ShotType.SOULSHOT;
	}
	
	@Override
	protected int[][] getBuffs()
	{
		return FakePlayerHelpers.getFighterBuffs();
	}
	
	@Override
	protected List<OffensiveSpell> getOffensiveSpells()
	{
		return _offensiveSpells;
	}
	
	@Override
	protected List<HealingSpell> getHealingSpells()
	{
		return Collections.emptyList();
	}
	
	@Override
	protected List<SupportSpell> getSelfSupportSpells()
	{
		return Collections.emptyList();
	}
}