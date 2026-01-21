package ext.mods.fakeplayer.ai.addon;

import ext.mods.gameserver.model.item.instance.ItemInstance;

import ext.mods.fakeplayer.FakePlayer;

public interface IConsumableSpender
{
	default void handleConsumable(FakePlayer fakePlayer, int consumableId)
	{
		if (fakePlayer.getInventory().getItemByItemId(consumableId) != null)
		{
			if (fakePlayer.getInventory().getItemByItemId(consumableId).getCount() <= 20)
			{
				fakePlayer.getInventory().addItem(consumableId, 500);
				
			}
		}
		else
		{
			fakePlayer.getInventory().addItem(consumableId, 500);
			ItemInstance consumable = fakePlayer.getInventory().getItemByItemId(consumableId);
			if (consumable.isEquipable())
				fakePlayer.getInventory().equipItem(consumable);
		}
	}
}
