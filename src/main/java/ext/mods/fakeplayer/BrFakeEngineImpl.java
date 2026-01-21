package ext.mods.fakeplayer;

import ext.mods.api.fake.IFakeEngine;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.fakeplayer.FakePlayerManager; // Seus managers antigos
import ext.mods.fakeplayer.data.EquipesData;

import java.util.logging.Logger;

public class BrFakeEngineImpl implements IFakeEngine {
    private static final Logger _log = Logger.getLogger(BrFakeEngineImpl.class.getName());

    @Override
    public void initialize() {
        _log.info("=== Inicializando BrFakeEngine (Nova API) ===");
        EquipesData.getInstance().load();
        FakePlayerManager.getInstance().initialise();
    }

    @Override
    public void shutdown() {
        FakePlayerManager.getInstance().getFakePlayers().forEach(fp -> fp.despawnPlayer());
    }

    @Override
    public String getVersion() {
        return "2.0-API";
    }

    @Override
    public boolean isFake(Player player) {
        return player instanceof FakePlayer;
    }

    @Override
    public void spawnFake(int x, int y, int z) {
        FakePlayerManager.getInstance().spawnPlayer(x, y, z);
    }
}