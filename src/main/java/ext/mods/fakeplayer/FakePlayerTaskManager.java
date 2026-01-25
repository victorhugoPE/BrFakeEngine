package ext.mods.fakeplayer;

import java.util.ArrayList;
import java.util.List;

// Pool de Threads
import ext.mods.fakeplayer.pool.ThreadPool;
import ext.mods.fakeplayer.task.AITask;
import ext.mods.fakeplayer.task.AITaskRunner;

// Import necessário para o Realtime
import ext.mods.fakeplayer.network.FakeWebServer; 

public class FakePlayerTaskManager
{
	// Intervalo de execução da IA (Tickrate do Jogo - 0.7s)
	private final int aiTaskRunnerInterval = 700;
	
	// Intervalo de atualização da Web (Visualização - 1.0s)
	// Separamos isso para o envio de JSON não pesar na lógica de combate
	private final int broadcastInterval = 1000;
	
	// Quantos bots cada Thread de IA vai processar
	private final int _playerCountPerTask = 2000;
	
	private List<AITask> _aiTasks;
	
	public void initialise()
	{
		_aiTasks = new ArrayList<>();
		
		// 1. Inicia o Loop da Inteligência Artificial (Mecânica)
		ThreadPool.scheduleAtFixedRate(new AITaskRunner(), aiTaskRunnerInterval, aiTaskRunnerInterval);
		
		// 2. Inicia o Loop de Broadcast Web (Visualização em Tempo Real)
		// Isso garante que as barras de HP/MP no site se mexam sozinhas
		ThreadPool.scheduleAtFixedRate(new BroadcastTask(), broadcastInterval, broadcastInterval);
	}
	
	public void adjustTaskSize()
	{
		// Pega a contagem do Repositório para saber quantos bots existem
		int fakePlayerCount = FakePlayerRepository.getInstance().getCount();
		
		int tasksNeeded = calculateTasksNeeded(fakePlayerCount);
		
		// Recria as tarefas de IA baseadas na nova quantidade
		synchronized(this) {
			_aiTasks.clear();
			for (int i = 0; i < tasksNeeded; i++)
			{
				int from = i * _playerCountPerTask;
				int to = (i + 1) * _playerCountPerTask;
				_aiTasks.add(new AITask(from, to));
			}
		}
	}
	
	private int calculateTasksNeeded(int fakePlayerCount)
	{
		if (fakePlayerCount == 0) return 0;
		if (fakePlayerCount < _playerCountPerTask) return 1;
		return (fakePlayerCount + _playerCountPerTask) / _playerCountPerTask;
	}
	
	public int getPlayerCountPerTask()
	{
		return _playerCountPerTask;
	}
	
	public int getTaskCount()
	{
		return _aiTasks.size();
	}
	
	public List<AITask> getAITasks()
	{
		return _aiTasks;
	}
	
	/**
	 * Tarefa responsável por enviar os dados de todos os bots para o WebSocket.
	 * Roda em paralelo com a IA.
	 */
	private class BroadcastTask implements Runnable
	{
		@Override
		public void run()
		{
			try
			{
				// Se não tiver ninguém conectado no site, economiza CPU e não faz nada
				if (FakeWebServer.allChannels.isEmpty()) return;

				List<FakePlayer> activeBots = FakePlayerRepository.getInstance().getAll();
				
				for (FakePlayer fp : activeBots)
				{
					// Só envia dados se o bot estiver realmente spawnado no mundo
					if (fp != null && fp.isOnline())
					{
						FakeWebServer.broadcastBotStatus(fp);
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public static FakePlayerTaskManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		private static final FakePlayerTaskManager _instance = new FakePlayerTaskManager();
	}
}