package ext.mods.fakeplayer.brain;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ext.mods.fakeplayer.brain.FakeBotBrain.BrainGoal;

public class ArchetypeManager
{
    private static ArchetypeManager _instance;
    private final Map<String, AIPersonality> _archetypes = new ConcurrentHashMap<>();

    // Variáveis Globais (Overrides)
    private Boolean _globalParty = null;
    private Boolean _globalTrade = null;
    private Boolean _globalChat = null;
    private BrainGoal _globalGoal = null;
    
    // Configuração de Cenário (Timings)
    public int deathDelayMin = 5;
    public int deathDelayMax = 10;
    public int townRestMin = 20;
    public int townRestMax = 60;
    public int gkTalkMin = 2;
    public int gkTalkMax = 5;
    public int spotArrivalMin = 3;
    public int spotArrivalMax = 8;

    public static ArchetypeManager getInstance() {
        if (_instance == null) _instance = new ArchetypeManager();
        return _instance;
    }

    private ArchetypeManager() {
        loadDefaults();
    }

    private void loadDefaults() {
        // Agressivo
        AIPersonality aggro = new AIPersonality("Agressivo");
        aggro.courage = 90; aggro.baseRevengeChance = 80; aggro.farmFocus = 10;
        aggro.setGoalWeight(BrainGoal.PVP, 100); aggro.setGoalWeight(BrainGoal.FARM, 20);
        _archetypes.put("Agressivo", aggro);
        
        // Farmer
        AIPersonality farmer = new AIPersonality("Farmer");
        farmer.courage = 20; farmer.baseRevengeChance = 5; farmer.farmFocus = 95;
        farmer.setGoalWeight(BrainGoal.FARM, 100); farmer.setGoalWeight(BrainGoal.PVP, 10);
        _archetypes.put("Farmer", farmer);
        
        // Neutro
        AIPersonality neutral = new AIPersonality("Neutro");
        _archetypes.put("Neutro", neutral);
    }

    // O MÉTODO QUE ESTAVA FALTANDO
    public AIPersonality getArchetype(String name) {
        if (_archetypes.containsKey(name))
            return _archetypes.get(name).clone();
        return _archetypes.get("Neutro").clone();
    }
    
    public AIPersonality getGlobalTemplate(String name) {
        return _archetypes.get(name);
    }

    public Collection<AIPersonality> getAllArchetypes() {
        return _archetypes.values();
    }
    
    // Getters/Setters Globais
    public Boolean getGlobalParty() { return _globalParty; }
    public void setGlobalParty(Boolean val) { _globalParty = val; }
    
    public Boolean getGlobalTrade() { return _globalTrade; }
    public void setGlobalTrade(Boolean val) { _globalTrade = val; }
    
    public Boolean getGlobalChat() { return _globalChat; }
    public void setGlobalChat(Boolean val) { _globalChat = val; }
    
    public BrainGoal getGlobalGoal() { return _globalGoal; }
    public void setGlobalGoal(BrainGoal val) { _globalGoal = val; }
}