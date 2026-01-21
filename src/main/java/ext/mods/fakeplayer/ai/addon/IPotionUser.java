package ext.mods.fakeplayer.ai.addon;

import ext.mods.gameserver.handler.IItemHandler;
import ext.mods.gameserver.handler.ItemHandler;
import ext.mods.gameserver.model.item.instance.ItemInstance;

import ext.mods.fakeplayer.FakePlayer;

public interface IPotionUser
{
	
	default void handlePotions(FakePlayer fakePlayer, int cpPotionId, int hpPotionId, int mpPotionId)
	{
		if (fakePlayer == null || fakePlayer.isDead() || fakePlayer.isAllSkillsDisabled())
			return;
		
		final double maxCp = fakePlayer.getStatus().getMaxCp();
		final double currentCp = fakePlayer.getStatus().getCp();
		
		final double maxHp = fakePlayer.getStatus().getMaxHp();
		final double currentHp = fakePlayer.getStatus().getHp();
		
		final double maxMp = fakePlayer.getStatus().getMaxMp();
		final double currentMp = fakePlayer.getStatus().getMp();
		
		if ((currentCp / maxCp) < 0.90)
			usePotion(fakePlayer, cpPotionId);
		
		if ((currentHp / maxHp) < 0.50)
			usePotion(fakePlayer, hpPotionId);
		
		if ((currentMp / maxMp) < 0.30)
			usePotion(fakePlayer, mpPotionId);
	}
	
	default void usePotion(FakePlayer fakePlayer, int itemId)
	{
		ItemInstance potion = fakePlayer.getInventory().getItemByItemId(itemId);
		if (potion != null)
		{
		    IItemHandler handler = ItemHandler.getInstance().getHandler(potion.getEtcItem());
		    if (handler != null)
		    {
		        handler.useItem(fakePlayer, potion, false);
		    }
		}

	}
}