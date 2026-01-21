package ext.mods.fakeplayer.pool;

import java.util.concurrent.ScheduledFuture;

/**
 * Wrapper de compatibilidade para o FakeEngine.
 * Delega tudo para o CoroutinePool (Kotlin).
 */
public final class ThreadPool
{
	public static void init()
	{
		CoroutinePool.init();
	}

	public static void execute(Runnable r)
	{
		CoroutinePool.execute(r);
	}

	public static ScheduledFuture<?> schedule(Runnable r, long delay)
	{
		try {
			return CoroutinePool.schedule(r, delay);
		} catch (Exception e) {
			return null;
		}
	}

	public static ScheduledFuture<?> scheduleAtFixedRate(Runnable r, long delay, long period)
	{
		try {
			return CoroutinePool.scheduleAtFixedRate(r, delay, period);
		} catch (Exception e) {
			return null;
		}
	}
	
	public static void executePathfinding(Runnable r)
	{
		CoroutinePool.executePathfinding(r);
	}
	
	public static int getPathfindingQueueSize()
	{
		return CoroutinePool.getPathfindingQueueSize();
	}

	public static int getPathfindingActiveCount()
	{
		return CoroutinePool.getPathfindingActiveCount();
	}

	public static void shutdown()
	{
		CoroutinePool.shutdown();
	}
}