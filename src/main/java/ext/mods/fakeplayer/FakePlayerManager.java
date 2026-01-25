package ext.mods.fakeplayer;

import java.util.List;
import java.util.ArrayList;

import ext.mods.gameserver.model.actor.Player;

import ext.mods.fakeplayer.data.EquipesData;
import ext.mods.fakeplayer.pool.ThreadPool;

/**
 * FACADE (Fachada) Principal.
 * Ponto de entrada único para o sistema de Fakes.
 * Gerencia o ciclo de vida, pools e acesso aos dados.
 */
public class FakePlayerManager
{
	// Mantém o estado de inicialização para evitar duplicidade
	private boolean _isInitialized = false;

	/**
	 * Inicializa todo o motor dos Fakes.
	 * Deve ser chamado apenas uma vez no boot do servidor.
	 */
	public void initialise()
	{
		if (_isInitialized) return;
		
		System.out.println("[FakePlayerManager] Iniciando carregamento do BrFakeEngine...");

		// 1. Inicializa Pools de Threads (Kotlin/Java 21 Virtual Threads)
		ThreadPool.init();
		
		// 2. Inicializa o Loop de Inteligência Artificial (TaskManager)
		FakePlayerTaskManager.getInstance().initialise();
		
		_isInitialized = true;
	}
	
	/**
	 * Método legado mantido para compatibilidade.
	 * Agora apenas ajusta o TaskManager para garantir que novos bots tenham threads.
	 */
	public void addPlayerToTask(FakePlayer fp)
	{
		FakePlayerTaskManager.getInstance().adjustTaskSize();
	}

	/**
	 * Cria e spawna um novo fake no mundo.
	 * Delega a criação complexa para o LifecycleService.
	 */
	public FakePlayer spawnPlayer(int x, int y, int z)
	{
		return FakeLifecycleService.getInstance().createAndSpawn(x, y, z);
	}
	
	/**
	 * Spawna um grupo de fakes em linha/grid.
	 * Útil para testes de carga ou preenchimento de áreas.
	 */
	public List<FakePlayer> spawnPlayerGroup(int x, int y, int z, int count)
	{
		List<FakePlayer> group = new ArrayList<>();
		int attempts = 0;
		int i = 0;
		
		// Tenta spawnar 'count' fakes, com limite de tentativas para não travar
		while (group.size() < count && attempts < count * 2)
		{
			// Pequeno offset (45 units) para não nascerem "fundidos" um dentro do outro
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
	
	/**
	 * Remove um fake específico do mundo.
	 */
	public void despawnFakePlayer(int objectId)
	{
		FakeLifecycleService.getInstance().despawn(objectId);
	}
	
	/**
	 * Remove TODOS os fakes do mundo (Limpeza total).
	 */
	public void despawnAll()
	{
		FakeLifecycleService.getInstance().despawnAll();
	}
	
	/**
	 * Retorna a quantidade total de fakes online.
	 */
	public int getFakePlayersCount()
	{
		return FakePlayerRepository.getInstance().getCount();
	}
	
	/**
	 * Retorna uma lista com todos os fakes online.
	 */
	public List<FakePlayer> getFakePlayers()
	{
		return FakePlayerRepository.getInstance().getAll();
	}
	
	/**
	 * Busca um FakePlayer específico pelo ObjectId.
	 * Necessário para o WebServer / Dashboard exibir detalhes individuais.
	 */
	public FakePlayer getFakePlayer(int objectId)
	{
		return FakePlayerRepository.getInstance().get(objectId);
	}
	
	// ==========================================
	// Singleton Pattern
	// ==========================================
	public static FakePlayerManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		private static final FakePlayerManager _instance = new FakePlayerManager();
	}
}