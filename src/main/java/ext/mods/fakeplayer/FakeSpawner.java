package ext.mods.fakeplayer;

import java.util.logging.Logger;

import ext.mods.commons.random.Rnd;
import ext.mods.fakeplayer.network.FakeInternalServer;
import ext.mods.gameserver.model.location.Location;

/**
 * Responsável por receber a ordem de spawn e delegar ao Manager.
 */
public class FakeSpawner
{
	private static final Logger _log = Logger.getLogger(FakeSpawner.class.getName());

	public static FakePlayer performSpawn(int x, int y, int z)
	{
		try
		{
			// 1. Cria e spawna via Manager
			FakePlayer fakePlayer = FakePlayerManager.getInstance().spawnPlayer(x, y, z);

			if (fakePlayer == null)
			{
				_log.warning("[FakeSpawner] Falha: FakePlayerManager retornou null.");
				return null;
			}

			_log.info("[FakeSpawner] Sucesso: " + fakePlayer.getName() + " [" + fakePlayer.getClassId().name() + "]");

			// 2. Avisa o Console Externo
			String registerCmd = String.format("CMD:REGISTER:%d:%d:%s", 
					fakePlayer.getObjectId(), 
					fakePlayer.getClassId().getId(), 
					fakePlayer.getName());
			
			FakeInternalServer.getInstance().sendPacket(registerCmd);

			// 3. KICKSTART DA IA (O "Empurrãozinho")
			int destX = x + Rnd.get(-150, 150);
			int destY = y + Rnd.get(-150, 150);
			
			if (fakePlayer.getAi() != null)
			{
				fakePlayer.getAI().tryToMoveTo(new Location(destX, destY, z), null);
			}

			return fakePlayer;
		}
		catch (Exception e)
		{
			_log.severe("[FakeSpawner] Erro fatal ao processar spawn: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
}