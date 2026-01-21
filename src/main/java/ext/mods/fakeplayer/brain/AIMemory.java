package ext.mods.fakeplayer.brain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ext.mods.commons.random.Rnd;

public class AIMemory
{
    // ID do Player -> Nível de Rancor (Quantas vezes matou ou dano acumulado)
    private final Map<Integer, Integer> _grudgeList = new ConcurrentHashMap<>();
    
    // ID do último assassino (Short-term memory)
    private int _lastKillerId = 0;
    private long _lastDeathTime = 0;

    /**
     * Registra uma agressão ou morte.
     * @param objectId ID do agressor
     * @param weight Peso da agressão (1 = hit, 10 = morte)
     */
    public void registerAggression(int objectId, int weight)
    {
        _grudgeList.merge(objectId, weight, Integer::sum);
        
        // Se foi morte (peso alto), atualiza último assassino
        if (weight >= 10)
        {
            _lastKillerId = objectId;
            _lastDeathTime = System.currentTimeMillis();
        }
    }

    public int getGrudgeLevel(int objectId)
    {
        return _grudgeList.getOrDefault(objectId, 0);
    }

    public void forget(int objectId)
    {
        _grudgeList.remove(objectId);
    }

    public void clearAll()
    {
        _grudgeList.clear();
        _lastKillerId = 0;
    }
    
    public int getLastKillerId()
    {
        // Esquece assassino após 30 minutos (Realismo)
        if (System.currentTimeMillis() - _lastDeathTime > 1800000)
            return 0;
            
        return _lastKillerId;
    }
}