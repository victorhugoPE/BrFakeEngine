package ext.mods.fakeplayer.brain;

import java.util.logging.Logger;
import ext.mods.commons.random.Rnd;
import ext.mods.fakeplayer.FakePlayer;

public class FakeBotBrain
{
    private static final Logger _log = Logger.getLogger(FakeBotBrain.class.getName());
    
    private final FakePlayer _owner;
    private AIPersonality _personality;
    private final AIMemory _memory;
    
    private boolean _isEnraged = false;
    public enum BrainGoal { IDLE, FARM, PVP, ASSIST, RESUPPLY }
    private BrainGoal _currentGoal = BrainGoal.FARM;
    
    private final String _originalArchetypeName;

    public FakeBotBrain(FakePlayer owner)
    {
        _owner = owner;
        _memory = new AIMemory();
        boolean isAggro = Rnd.get(100) < 30;
        _originalArchetypeName = isAggro ? "Agressivo" : "Farmer";
        resetPersonality();
    }

    // ... [MÉTODOS EXISTENTES MANTIDOS: setEnraged, setGoal, resetPersonality, toggle..., onDeath, decideRevenge] ...
    
    public void setEnraged(boolean val) {
        _isEnraged = val;
        if (val) {
            _personality.baseRevengeChance = 100;
            _personality.courage = 100;
            _personality.farmFocus = 0;
            _currentGoal = BrainGoal.PVP;
        } else {
            resetPersonality();
        }
    }
    
    public void setGoal(BrainGoal goal) {
        _currentGoal = goal;
        switch (goal) {
            case PVP: _personality.courage = Math.max(_personality.courage, 80); _personality.farmFocus = 0; break;
            case FARM: _personality.farmFocus = 100; break;
            case IDLE: _personality.farmFocus = 0; _personality.baseRevengeChance = 0; break;
        }
    }
    
    public void resetPersonality() {
        _personality = ArchetypeManager.getInstance().getArchetype(_originalArchetypeName);
        _isEnraged = false;
        _currentGoal = _originalArchetypeName.equals("Farmer") ? BrainGoal.FARM : BrainGoal.IDLE;
    }

    public void toggleParty() { _personality.acceptsParty = !_personality.acceptsParty; }
    public void toggleTrade() { _personality.acceptsTrade = !_personality.acceptsTrade; }
    public void toggleChat()  { _personality.repliesToChat = !_personality.repliesToChat; }

    public void onDeath(int killerId) {
        _memory.registerAggression(killerId, 10);
        if (decideRevenge(killerId)) { setEnraged(true); } else { _isEnraged = false; }
    }

    private boolean decideRevenge(int killerId) {
        int chance = _personality.baseRevengeChance;
        int deaths = _memory.getGrudgeLevel(killerId) / 10;
        if (deaths > 1) chance += (deaths - 1) * _personality.angerMultiplier;
        chance -= (_personality.farmFocus / 2); 
        chance = Math.max(5, Math.min(95, chance));
        return Rnd.get(100) < chance;
    }

    // ==========================================
    // GETTERS INTELIGENTES (HIERARQUIA GLOBAL)
    // ==========================================
    
    public BrainGoal getGoal() {
        // 1. Verifica Global
        BrainGoal global = ArchetypeManager.getInstance().getGlobalGoal();
        if (global != null) return global;
        
        // 2. Retorna Individual
        return _currentGoal;
    }
    
    public boolean acceptsParty() {
        Boolean global = ArchetypeManager.getInstance().getGlobalParty();
        return (global != null) ? global : _personality.acceptsParty;
    }
    
    public boolean acceptsTrade() {
        Boolean global = ArchetypeManager.getInstance().getGlobalTrade();
        return (global != null) ? global : _personality.acceptsTrade;
    }
    
    public boolean repliesToChat() {
        Boolean global = ArchetypeManager.getInstance().getGlobalChat();
        return (global != null) ? global : _personality.repliesToChat;
    }
    
    // Getters brutos para Admin (não alterados)
    public AIPersonality getPersonality() { return _personality; }
    public AIMemory getMemory() { return _memory; }
    public boolean isEnraged() { return _isEnraged; }
}