package ext.mods.fakeplayer.pool

import kotlinx.coroutines.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder
import java.util.logging.Logger
import kotlin.math.max
import kotlin.math.min

/**
 * Pool de threads otimizado para o FakeEngine (Java 21).
 * Adaptado para funcionar de forma independente do GameServer.
 */
object CoroutinePool {
    
    private val LOGGER: Logger = Logger.getLogger(CoroutinePool::class.java.name)

    private var scheduledExecutors: Array<ScheduledThreadPoolExecutor>? = null
    private var instantExecutors: Array<ThreadPoolExecutor>? = null
    private var pathfindingExecutor: ThreadPoolExecutor? = null
    
    private var virtualExecutor: ExecutorService? = null
    
    private var forkJoinPool: ForkJoinPool? = null
    
    private val BackgroundScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    val PathfindingDispatcher: CoroutineDispatcher by lazy {
        pathfindingExecutor?.asCoroutineDispatcher() ?: Dispatchers.Default
    }
    
    // Contadores Atômicos
    private val totalTasksSubmitted = LongAdder()
    private val totalTasksCompleted = LongAdder()
    private val totalTasksRejected = LongAdder()
    private val totalExecutionTimeMs = AtomicLong(0)
    
    private const val MAX_DELAY_MS: Long = 2_000_000_000L

    /**
     * Inicializa os pools.
     */
    @JvmStatic 
    fun init() {
        try {
            if (scheduledExecutors != null) {
                return
            }

            val availableProcessors = Runtime.getRuntime().availableProcessors()
            
            // --- Configuração Automática (Sem depender do Config.java do servidor) ---
            val poolSize = availableProcessors
            
            // --- Scheduled Pools ---
            scheduledExecutors = Array(poolSize) { index ->
                val threadProvider = ThreadProvider("Fake-Scheduled-$index", Thread.NORM_PRIORITY, true)
                ScheduledThreadPoolExecutor(
                    1, // 1 core thread por executor no array
                    threadProvider as ThreadFactory
                ).apply {
                    setRemoveOnCancelPolicy(true)
                    setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
                }
            }
            
            // --- Instant Pools ---
            instantExecutors = Array(poolSize) { index ->
                val threadProvider = ThreadProvider("Fake-Instant-$index", Thread.NORM_PRIORITY, true)
                ThreadPoolExecutor(
                    1, // Core
                    4, // Max (elástico)
                    60L, TimeUnit.SECONDS,
                    LinkedBlockingQueue(5000),
                    threadProvider as ThreadFactory,
                    RejectedExecutionHandlerImpl()
                ).apply {
                    allowCoreThreadTimeOut(false)
                }
            }
            
            // --- Virtual Threads (Java 21) ---
            try {
                virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()
                LOGGER.info("Virtual Thread Executor: ATIVO")
            } catch (e: Exception) {
                LOGGER.warning("Virtual Threads não suportadas ou erro: ${e.message}")
            }
            
            // --- Pathfinding Pool (Dedicado para IA) ---
            val pfThreads = (availableProcessors / 2).coerceAtLeast(2)
            val pfProvider = ThreadProvider("Fake-Pathfinding", Thread.NORM_PRIORITY - 1, true)
            
            pathfindingExecutor = ThreadPoolExecutor(
                pfThreads, pfThreads,
                60L, TimeUnit.SECONDS,
                LinkedBlockingQueue(500),
                pfProvider as ThreadFactory,
                ThreadPoolExecutor.CallerRunsPolicy()
            ).apply {
                allowCoreThreadTimeOut(false)
                prestartAllCoreThreads()
            }
            
            // --- ForkJoin Pool ---
            forkJoinPool = ForkJoinPool(
                availableProcessors,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                true
            )
            
            LOGGER.info("CoroutinePool (FakeEngine) inicializado com $poolSize processadores.")
            
        } catch (e: Exception) {
            LOGGER.severe("ERRO CRÍTICO ao inicializar CoroutinePool: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Executa uma tarefa instantânea.
     */
    @JvmStatic 
    fun execute(r: Runnable) {
        val executors = instantExecutors
        if (executors == null || executors.isEmpty()) {
            try { r.run() } catch (e: Exception) { e.printStackTrace() }
            return
        }
        
        try {
            totalTasksSubmitted.increment()
            val startTime = System.nanoTime()
            
            // Load Balancing
            val selectedPool = executors[ThreadLocalRandom.current().nextInt(executors.size)]
            
            selectedPool.execute {
                try {
                    r.run()
                    totalTasksCompleted.increment()
                    totalExecutionTimeMs.addAndGet((System.nanoTime() - startTime) / 1_000_000)
                } catch (e: Throwable) {
                    val t = Thread.currentThread()
                    t.uncaughtExceptionHandler?.uncaughtException(t, e)
                }
            }
            
        } catch (e: Exception) {
            totalTasksRejected.increment()
            LOGGER.warning("Tarefa rejeitada: ${e.message}")
        }
    }
    
    @JvmStatic
    fun executeIO(task: Runnable) {
        val executor = virtualExecutor
        if (executor == null) {
            execute(task)
            return
        }
        try {
            totalTasksSubmitted.increment()
            executor.execute(task)
        } catch (e: Exception) {
            execute(task)
        }
    }
    
    // --- Scheduled ---
    
    @JvmStatic
    fun schedule(r: Runnable, delay: Long): ScheduledFuture<*> {
        val executors = scheduledExecutors ?: throw IllegalStateException("Pools nulos")
        totalTasksSubmitted.increment()
        val selectedPool = executors[ThreadLocalRandom.current().nextInt(executors.size)]
        return selectedPool.schedule(RunnableWrapper(r), validate(delay), TimeUnit.MILLISECONDS)
    }
    
    @JvmStatic
    fun scheduleAtFixedRate(r: Runnable, delay: Long, period: Long): ScheduledFuture<*> {
        val executors = scheduledExecutors ?: throw IllegalStateException("Pools nulos")
        totalTasksSubmitted.increment()
        val selectedPool = executors[ThreadLocalRandom.current().nextInt(executors.size)]
        return selectedPool.scheduleAtFixedRate(RunnableWrapper(r), validate(delay), validate(period), TimeUnit.MILLISECONDS)
    }
    
    // --- Pathfinding ---

    @JvmStatic
    fun executePathfinding(task: Runnable) {
        pathfindingExecutor?.execute(RunnableWrapper(task)) ?: task.run()
    }
    
    @JvmStatic
    fun executeParallelBlocking(task: Runnable) {
        forkJoinPool?.submit(task)?.join() ?: execute(task)
    }

    // --- Métricas ---
    
    @JvmStatic fun getPathfindingQueueSize(): Int = pathfindingExecutor?.queue?.size ?: 0
    @JvmStatic fun getPathfindingActiveCount(): Int = pathfindingExecutor?.activeCount ?: 0
    
    private fun validate(delay: Long): Long {
        return max(0L, min(MAX_DELAY_MS, delay))
    }

    @JvmStatic
    fun shutdown() {
        BackgroundScope.cancel()
        scheduledExecutors?.forEach { it.shutdown() }
        instantExecutors?.forEach { it.shutdown() }
        pathfindingExecutor?.shutdown()
        virtualExecutor?.shutdown()
        forkJoinPool?.shutdown()
    }

    // =================================================================================
    // CLASSES AUXILIARES (Embutidas para não depender do Commons do Server)
    // =================================================================================
    
    /** Wrapper seguro para evitar que exceções matem a thread */
    private class RunnableWrapper(private val task: Runnable) : Runnable {
        override fun run() {
            try {
                task.run()
            } catch (t: Throwable) {
                val thread = Thread.currentThread()
                thread.uncaughtExceptionHandler?.uncaughtException(thread, t) 
                    ?: t.printStackTrace()
            }
        }
    }

    /** Handler simples para rejeição de tarefas */
    private class RejectedExecutionHandlerImpl : RejectedExecutionHandler {
        override fun rejectedExecution(r: Runnable, executor: ThreadPoolExecutor) {
            if (!executor.isShutdown) {
                // Se o pool encher, roda na thread atual (CallerRunsPolicy customizada)
                r.run()
            }
        }
    }
}