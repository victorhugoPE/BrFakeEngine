package ext.mods.fakeplayer.task;

import java.util.List;

import ext.mods.commons.pool.ThreadPool;

import ext.mods.fakeplayer.FakePlayerTaskManager;

public class AITaskRunner implements Runnable
{
	@Override
	public void run()
	{
		FakePlayerTaskManager.getInstance().adjustTaskSize();
		List<AITask> aiTasks = FakePlayerTaskManager.getInstance().getAITasks();
		aiTasks.forEach(aiTask -> ThreadPool.execute(aiTask));
	}
}