package ext.mods.fakeplayer.brain;

public enum AIRole
{
    TANK,       // Foco: Aggro, Puxar, Iniciar
    MELEE_DPS,  // Foco: Assistir, Flanquear, Dano Burst
    RANGED_DPS, // Foco: Assistir de longe (Arqueiros)
    NUKER,      // Foco: Assistir de longe (Magos)
    HEALER,     // Foco: HP da Party, Buffs, Ressuscitar
    BUFFER,     // Foco: Buffs, Debuffs, MP
    DUMMY       // Sem função específica (Default)
}