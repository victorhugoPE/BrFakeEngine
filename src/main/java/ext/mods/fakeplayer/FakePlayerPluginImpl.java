package ext.mods.fakeplayer;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import ext.mods.bridge.fakeplayer.IFakePlayerPlugin;
import ext.mods.fakeplayer.FakePlayerManager;
import ext.mods.fakeplayer.FakeSpawner;
import ext.mods.fakeplayer.network.FakeInternalServer;

public class FakePlayerPluginImpl implements IFakePlayerPlugin
{
	private static final Logger _log = Logger.getLogger(FakePlayerPluginImpl.class.getName());

	@Override
	public void onEnable()
	{
		_log.info("FakePlayer Plugin: CARREGANDO (v4.1 - Lista HTML)");
		try {
			FakePlayerManager.getInstance().initialise();
			FakeInternalServer.getInstance().start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDisable()
	{
		FakeInternalServer.getInstance().shutdown();
		FakePlayerManager.getInstance().despawnAll();
	}

	@Override
	public String getVersion()
	{
		return "4.1-Dashboard";
	}

	@Override
	public void spawnFake(int x, int y, int z)
	{
		FakeSpawner.performSpawn(x, y, z);
	}

	@Override
	public void spawnFakeParty(int x, int y, int z, int count)
	{
		for (int i = 0; i < count; i++) {
			FakeSpawner.performSpawn(x, y, z);
			try { Thread.sleep(50); } catch (Exception e) {}
		}
	}

	@Override
	public void deleteFake(int objectId)
	{
		FakePlayerManager.getInstance().despawnFakePlayer(objectId);
	}

	@Override
	public void deleteFakes()
	{
		FakePlayerManager.getInstance().despawnAll();
	}

	@Override
	public int getFakeCount()
	{
		return FakePlayerManager.getInstance().getFakePlayersCount();
	}

	@Override
	public Map<Integer, String> getFakePlayersList()
	{
		Map<Integer, String> list = new HashMap<>();
		for (FakePlayer fp : FakePlayerManager.getInstance().getFakePlayers())
		{
			list.put(fp.getObjectId(), fp.getName());
		}
		return list;
	}
}