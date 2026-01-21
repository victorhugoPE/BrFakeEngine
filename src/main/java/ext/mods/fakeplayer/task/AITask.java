package ext.mods.fakeplayer.task;

import java.util.List;

import ext.mods.fakeplayer.FakePlayer;
import ext.mods.fakeplayer.FakePlayerManager;

public class AITask implements Runnable
{
	private int _from;
	private int _to;
	
	public AITask(int from, int to)
	{
		_from = from;
		_to = to;
	}
	
	@Override
	public void run()
	{
		adjustPotentialIndexOutOfBounds();
		List<FakePlayer> fakePlayers = FakePlayerManager.getInstance().getFakePlayers().subList(_from, _to);
		try
		{
			fakePlayers.stream().filter(fp -> fp.getAi() != null && !fp.getAi().isBusyThinking()).forEach(fp ->
			{
				try
				{
					fp.getAi().thinkAndAct();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			});
			
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		
	}
	
	private void adjustPotentialIndexOutOfBounds()
	{
		if (_to > FakePlayerManager.getInstance().getFakePlayersCount())
		{
			_to = FakePlayerManager.getInstance().getFakePlayersCount();
		}
	}
}
