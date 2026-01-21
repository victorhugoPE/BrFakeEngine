package ext.mods.fakeplayer;

import java.util.ArrayList;
import java.util.List;

// Usa o nosso Pool Otimizado
import ext.mods.fakeplayer.pool.ThreadPool;
import ext.mods.fakeplayer.task.AITask;
import ext.mods.fakeplayer.task.AITaskRunner;

public class FakePlayerTaskManager
{
	// Intervalo de execução da IA (Tickrate)
	private final int aiTaskRunnerInterval = 700;
	
	// Quantos bots cada Thread/Task vai processar
	private final int _playerCountPerTask = 2000;
	
	private List<AITask> _aiTasks;
	
	public void initialise()
	{
		_aiTasks = new ArrayList<>();
		// Inicia o loop infinito usando o Pool de Corrotinas
		ThreadPool.scheduleAtFixedRate(new AITaskRunner(), aiTaskRunnerInterval, aiTaskRunnerInterval);
	}
	
	public void adjustTaskSize()
	{
		// ATUALIZAÇÃO CRÍTICA: Pega a contagem do Repositório, não do Manager
		int fakePlayerCount = FakePlayerRepository.getInstance().getCount();
		
		int tasksNeeded = calculateTasksNeeded(fakePlayerCount);
		
		// Recria as tarefas para garantir que os intervalos (from, to) estejam corretos
		_aiTasks.clear();
		
		for (int i = 0; i < tasksNeeded; i++)
		{
			int from = i * _playerCountPerTask;
			int to = (i + 1) * _playerCountPerTask;
			_aiTasks.add(new AITask(from, to));
		}
	}
	
	private int calculateTasksNeeded(int fakePlayerCount)
	{
		if (fakePlayerCount == 0)
			return 0;
			
		if (fakePlayerCount < _playerCountPerTask)
			return 1;
			
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
	
	public static FakePlayerTaskManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		private static final FakePlayerTaskManager _instance = new FakePlayerTaskManager();
	}
}