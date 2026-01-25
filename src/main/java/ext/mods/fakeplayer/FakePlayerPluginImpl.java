package ext.mods.fakeplayer;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import ext.mods.bridge.fakeplayer.IFakePlayerPlugin;
import ext.mods.fakeplayer.FakePlayerManager;
import ext.mods.fakeplayer.FakeSpawner;
import ext.mods.fakeplayer.network.FakeInternalServer;
import ext.mods.fakeplayer.network.FakeWebServer; // Import corrigido

public class FakePlayerPluginImpl implements IFakePlayerPlugin
{
    private static final Logger _log = Logger.getLogger(FakePlayerPluginImpl.class.getName());
    
    // Referência ao servidor web para mantê-lo vivo
    private FakeWebServer _webServer;

    @Override
    public void onEnable()
    {
        _log.info("FakePlayer Plugin: CARREGANDO (v4.5 - Web Dashboard Edition)");
        try {
            // 1. Inicializa o Gerenciador de Fakes (Pools, Tasks, etc)
            FakePlayerManager.getInstance().initialise();
            
            // 2. Inicializa o Servidor Interno (Porta 9000 - Para comandos via Console/TCP)
            FakeInternalServer.getInstance().start();
            
            // 3. Inicializa o Servidor WEB (Porta 8080 - Para o Navegador/Dashboard)
            _webServer = new FakeWebServer();
            _webServer.start();
            
            _log.info(">> DASHBOARD WEB: http://localhost:8080");
            
        } catch (Exception e) {
            _log.severe("Erro critico ao iniciar FakePlugin: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable()
    {
        // Desliga o servidor de comandos internos
        FakeInternalServer.getInstance().shutdown();
        
        // Remove todos os fakes do mundo para não bugar o shutdown do servidor principal
        FakePlayerManager.getInstance().despawnAll();
        
        _log.info("FakePlayer Plugin: DESLIGADO.");
    }

    @Override
    public String getVersion()
    {
        return "4.5-WebReady";
    }

    // =========================================================
    // IMPLEMENTAÇÃO DA INTERFACE (Ações do Servidor)
    // =========================================================

    @Override
    public void spawnFake(int x, int y, int z)
    {
        FakeSpawner.performSpawn(x, y, z);
    }

    @Override
    public void spawnFakeParty(int x, int y, int z, int count)
    {
        for (int i = 0; i < count; i++) {
            FakeSpawner.performSpawn(x, y, z);
            try { 
                // Pequeno delay para não sobrecarregar a thread de spawn
                Thread.sleep(50); 
            } catch (Exception e) {}
        }
    }

    @Override
    public void deleteFake(int objectId)
    {
        FakePlayerManager.getInstance().despawnFakePlayer(objectId);
    }

    @Override
    public void deleteFakes()
    {
        FakePlayerManager.getInstance().despawnAll();
    }

    @Override
    public int getFakeCount()
    {
        return FakePlayerManager.getInstance().getFakePlayersCount();
    }

    @Override
    public Map<Integer, String> getFakePlayersList()
    {
        Map<Integer, String> list = new HashMap<>();
        for (FakePlayer fp : FakePlayerManager.getInstance().getFakePlayers())
        {
            list.put(fp.getObjectId(), fp.getName());
        }
        return list;
    }
}