package ext.mods.fakeplayer.brain;

import java.util.HashMap;
import java.util.Map;
import ext.mods.fakeplayer.brain.FakeBotBrain.BrainGoal;

public class AIPersonality implements Cloneable
{
    // Identidade
    private String _archetype = "Custom";
    
    // ==========================================
    // PARÂMETROS SOCIAIS (Restaurados)
    // ==========================================
    public boolean acceptsParty = true;
    public boolean acceptsTrade = true;
    public boolean repliesToChat = false;
    
    // Função no Grupo (Mantendo para o futuro sistema de Party)
    public AIRole role = AIRole.DUMMY; 
    
    // ==========================================
    // PARÂMETROS DE COMBATE
    // ==========================================
    public int courage = 50;
    public int baseRevengeChance = 30;
    public int angerMultiplier = 25;
    public int farmFocus = 50;
    
    // Pesos dos Objetivos (0-100)
    public Map<BrainGoal, Integer> goalPriorities = new HashMap<>();

    public AIPersonality(String name)
    {
        _archetype = name;
        // Valores padrão de prioridade
        goalPriorities.put(BrainGoal.FARM, 50);
        goalPriorities.put(BrainGoal.PVP, 50);
        goalPriorities.put(BrainGoal.IDLE, 10);
        goalPriorities.put(BrainGoal.RESUPPLY, 80);
    }
    
    // Cria uma cópia independente para um bot específico
    @Override
    public AIPersonality clone() {
        try {
            AIPersonality copy = (AIPersonality) super.clone();
            copy.goalPriorities = new HashMap<>(this.goalPriorities);
            return copy;
        } catch (CloneNotSupportedException e) {
            return new AIPersonality("Error");
        }
    }

    public String getArchetype() { return _archetype; }
    public void setArchetype(String a) { _archetype = a; }
    
    public int getGoalWeight(BrainGoal goal) {
        return goalPriorities.getOrDefault(goal, 0);
    }
    
    public void setGoalWeight(BrainGoal goal, int weight) {
        goalPriorities.put(goal, Math.max(0, Math.min(100, weight)));
    }
}