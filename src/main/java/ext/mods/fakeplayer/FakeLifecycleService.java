package ext.mods.fakeplayer;

import java.util.logging.Logger;

import ext.mods.Config;
import ext.mods.gameserver.enums.RestartType;
import ext.mods.gameserver.enums.ZoneId;
import ext.mods.gameserver.model.World;
import ext.mods.fakeplayer.helper.FakePlayerHelpers;
import ext.mods.fakeplayer.network.FakeGameClient;

/**
 * Fábrica de Bots.
 * Cuida de toda a burocracia de criar, spawnar e remover do mundo.
 */
public class FakeLifecycleService
{
	private static final Logger _log = Logger.getLogger(FakeLifecycleService.class.getName());

	public FakePlayer createAndSpawn(int x, int y, int z)
	{
		// 1. Criação do Objeto (Dados Básicos)
		FakePlayer activeChar = FakePlayerHelpers.createRandomFakePlayer();
		
		if (activeChar == null)
		{
			_log.warning("FakeLifecycleService: Falha ao criar template ou nome esgotado.");
			return null;
		}
		
		// 2. Vínculo de Rede (Bypass)
		if (activeChar.getClient() == null)
		{
			activeChar.setClient(new FakeGameClient(activeChar));
		}
		
		// 3. Registro no Mundo (L2J Core)
		World.getInstance().addPlayer(activeChar);
		
		// 4. Proteção de Spawn (Config)
		if (Config.PLAYER_SPAWN_PROTECTION > 0)
			activeChar.setSpawnProtection(true);
		
		// 5. Materialização (Spawn)
		activeChar.spawnMe(x, y, z);
		activeChar.onPlayerEnter();
		
		// 6. Correção de Zona (Se nascer em Siege sem ser registrado)
		if (!activeChar.isGM() && (activeChar.getSiegeState() < 2) && activeChar.isInsideZone(ZoneId.SIEGE))
			activeChar.teleportTo(RestartType.TOWN);
		
		// 7. Ativação da IA
		if (activeChar.getAi() == null)
		{
			activeChar.assignDefaultAI();
		}
		activeChar.setRunning(true);
		
		// 8. Registro no Repositório Local
		FakePlayerRepository.getInstance().add(activeChar);
		
		// 9. Registro no Agendador de Tarefas
		FakePlayerTaskManager.getInstance().adjustTaskSize();
		
		return activeChar;
	}

	public void despawn(int objectId)
	{
		// Remove do Repositório primeiro para evitar que a IA processe um bot morrendo
		FakePlayer fakePlayer = FakePlayerRepository.getInstance().remove(objectId);
		
		if (fakePlayer != null)
		{
			try 
			{
				// Remove do Mundo (Lógica do FakePlayer.java)
				fakePlayer.despawnPlayer();
			} 
			catch (Exception e) 
			{
				_log.warning("Erro ao despawnar fake " + objectId + ": " + e.getMessage());
			}
		}
		
		// Reajusta as threads de IA
		FakePlayerTaskManager.getInstance().adjustTaskSize();
	}
	
	public void despawnAll()
	{
		// Copia a lista para evitar erro de concorrência enquanto deleta
		for (FakePlayer fp : FakePlayerRepository.getInstance().getAll())
		{
			despawn(fp.getObjectId());
		}
	}

	// Singleton Pattern
	private static class SingletonHolder
	{
		private static final FakeLifecycleService _instance = new FakeLifecycleService();
	}

	public static FakeLifecycleService getInstance()
	{
		return SingletonHolder._instance;
	}
}