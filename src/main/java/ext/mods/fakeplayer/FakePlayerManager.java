package ext.mods.fakeplayer;

import java.util.List;
import java.util.stream.Collectors;

import ext.mods.gameserver.model.actor.Player;

import ext.mods.fakeplayer.data.EquipesData;
import ext.mods.fakeplayer.move.GiranMove;
import ext.mods.fakeplayer.pool.ThreadPool;

/**
 * FACADE (Fachada) Principal.
 * Ponto de entrada único para o sistema de Fakes.
 */
public class FakePlayerManager
{
	// Mantém o estado de inicialização
	private boolean _isInitialized = false;

	public void initialise()
	{
		if (_isInitialized) return;
		
		System.out.println("[FakePlayerManager] Iniciando carregamento...");

		// 1. Inicializa Pools (Kotlin/Java 21)
		ThreadPool.init();
		
		// 2. Inicializa IA Loop (TaskManager)
		FakePlayerTaskManager.getInstance().initialise();
	}
	
	/**
	 * Método legado mantido para compatibilidade.
	 * Agora apenas ajusta o TaskManager.
	 */
	public void addPlayerToTask(FakePlayer fp)
	{
		FakePlayerTaskManager.getInstance().adjustTaskSize();
	}

	/**
	 * Cria e spawna um novo fake.
	 * Delega para o LifecycleService.
	 */
	public FakePlayer spawnPlayer(int x, int y, int z)
	{
		return FakeLifecycleService.getInstance().createAndSpawn(x, y, z);
	}
	
	/**
	 * Spawna um grupo em linha/grid.
	 */
	public List<FakePlayer> spawnPlayerGroup(int x, int y, int z, int count)
	{
		java.util.List<FakePlayer> group = new java.util.ArrayList<>();
		int attempts = 0;
		int i = 0;
		
		while (group.size() < count && attempts < count * 2)
		{
			// Pequeno offset para não nascerem "fundidos"
			FakePlayer bot = spawnPlayer(x + (i * 45), y + (i * 45), z);
			attempts++;
			i++;
			if (bot != null)
			{
				group.add(bot);
			}
		}
		return group;
	}
	
	public void despawnFakePlayer(int objectId)
	{
		FakeLifecycleService.getInstance().despawn(objectId);
	}
	
	public void despawnAll()
	{
		FakeLifecycleService.getInstance().despawnAll();
	}
	
	public int getFakePlayersCount()
	{
		return FakePlayerRepository.getInstance().getCount();
	}
	
	public List<FakePlayer> getFakePlayers()
	{
		return FakePlayerRepository.getInstance().getAll();
	}
	
	public static FakePlayerManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		private static final FakePlayerManager _instance = new FakePlayerManager();
	}
}