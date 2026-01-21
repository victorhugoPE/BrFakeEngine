package ext.mods.fakeplayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responsável APENAS por armazenar a referência dos bots ativos.
 * Zero lógica de jogo. Apenas dados.
 */
public class FakePlayerRepository
{
	// ConcurrentHashMap é Thread-Safe por natureza, não precisa de synchronized para put/get
	private final Map<Integer, FakePlayer> _fakePlayers = new ConcurrentHashMap<>();

	public void add(FakePlayer player)
	{
		_fakePlayers.put(player.getObjectId(), player);
	}

	public FakePlayer remove(int objectId)
	{
		return _fakePlayers.remove(objectId);
	}

	public FakePlayer get(int objectId)
	{
		return _fakePlayers.get(objectId);
	}

	public boolean contains(int objectId)
	{
		return _fakePlayers.containsKey(objectId);
	}

	public int getCount()
	{
		return _fakePlayers.size();
	}

	public List<FakePlayer> getAll()
	{
		// Retorna uma cópia segura para evitar ConcurrentModificationException ao iterar
		return new ArrayList<>(_fakePlayers.values());
	}
	
	public void clear()
	{
		_fakePlayers.clear();
	}

	// Singleton Pattern
	private static class SingletonHolder
	{
		private static final FakePlayerRepository _instance = new FakePlayerRepository();
	}

	public static FakePlayerRepository getInstance()
	{
		return SingletonHolder._instance;
	}
}