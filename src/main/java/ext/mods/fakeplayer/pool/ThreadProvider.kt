package ext.mods.fakeplayer.pool

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Implementação de ThreadFactory para o sistema de Fakes.
 * Responsável por nomear as threads criadas pelo CoroutinePool.
 */
class ThreadProvider @JvmOverloads constructor(
    private val prefix: String,
    private val priority: Int = Thread.NORM_PRIORITY,
    private val isDaemon: Boolean = false
) : ThreadFactory {
    
    private val id: AtomicInteger = AtomicInteger() 
    private val threadPrefix: String = "$prefix "

    /**
     * Cria uma nova Thread com o objeto Runnable especificado e com as propriedades definidas.
     */
    override fun newThread(runnable: Runnable): Thread {
        val thread = Thread(runnable, threadPrefix + id.incrementAndGet())
        thread.priority = priority
        thread.isDaemon = isDaemon
        return thread
    }
}