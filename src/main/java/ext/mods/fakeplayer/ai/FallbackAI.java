package ext.mods.fakeplayer.ai;

import java.util.Collections;
import java.util.List;

import ext.mods.fakeplayer.FakePlayer;
import ext.mods.fakeplayer.model.HealingSpell;
import ext.mods.fakeplayer.model.OffensiveSpell;
import ext.mods.fakeplayer.model.SupportSpell;
import ext.mods.gameserver.enums.items.ShotType;

public class FallbackAI extends CombatAI
{
	public FallbackAI(FakePlayer character)
	{
		super(character);
	}
	
	@Override
	public void thinkAndAct()
	{
	
	}
	
	@Override
	protected ShotType getShotType()
	{
		return ShotType.SOULSHOT;
	}
	
	@Override
	protected int[][] getBuffs()
	{
		return new int[0][0];
	}
	
	@Override
	protected List<OffensiveSpell> getOffensiveSpells()
	{
		return Collections.emptyList();
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
