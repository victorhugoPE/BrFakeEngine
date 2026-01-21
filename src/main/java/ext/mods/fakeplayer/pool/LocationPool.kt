package ext.mods.fakeplayer.pool

import ext.mods.gameserver.model.location.Location
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * PISCINA DE LOCALIZAÇÕES (Zero-GC)
 * Reaproveita objetos Location para evitar alocação excessiva de memória RAM.
 */
object LocationPool {

    private val pool = ConcurrentLinkedQueue<Location>()
    private const val MAX_POOL_SIZE = 10000

    /**
     * Pega uma Location da piscina ou cria uma nova se estiver vazia.
     */
    fun get(x: Int, y: Int, z: Int): Location {
        val loc = pool.poll()
        if (loc != null) {
            loc.set(x, y, z)
            return loc
        }
        return Location(x, y, z)
    }

    /**
     * Devolve uma Location para a piscina para ser reutilizada.
     * Use isso APENAS quando tiver certeza que o objeto não será mais usado.
     */
    fun release(loc: Location?) {
        if (loc == null) return
        if (pool.size < MAX_POOL_SIZE) {
            pool.offer(loc)
        }
    }
}