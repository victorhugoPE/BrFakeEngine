package ext.mods.fakeplayer.ai

import ext.mods.api.fake.IFakeBotControl
import ext.mods.commons.math.MathUtil
import ext.mods.commons.pool.ThreadPool
import ext.mods.commons.random.Rnd
import ext.mods.fakeplayer.FakePlayer
import ext.mods.fakeplayer.brain.ArchetypeManager
import ext.mods.fakeplayer.holder.WalkerHolder
import ext.mods.gameserver.data.SkillTable
import ext.mods.gameserver.data.xml.TeleportData
import ext.mods.gameserver.enums.ZoneId
import ext.mods.gameserver.enums.skills.EffectType
import ext.mods.gameserver.enums.skills.SkillTargetType
import ext.mods.gameserver.geoengine.GeoEngine
import ext.mods.gameserver.model.World
import ext.mods.gameserver.model.WorldObject
import ext.mods.gameserver.model.actor.Attackable
import ext.mods.gameserver.model.actor.Creature
import ext.mods.gameserver.model.actor.Player
import ext.mods.gameserver.model.location.Location
import ext.mods.gameserver.skills.L2Skill
import java.util.ArrayList

/**
 * CÉREBRO BASE - OTIMIZADO EM KOTLIN (v4.2 - Final Stable)
 * Inclui correção de Ciclo de Morte, Logs de Cenário e Otimizações de Memória.
 */
abstract class FakePlayerAI(
    @JvmField protected val _fakePlayer: FakePlayer
) {

    @JvmField protected val _controls: IFakeBotControl = _fakePlayer
    @JvmField protected var _isBusyThinking: Boolean = false
    @JvmField protected var _lastFarmLocation: Location? = null
    
    // Controle interno de morte
    private var isDeadProcessing = false
    
    // Legado para compatibilidade
    @JvmField protected var iterationsOnDeath: Int = 0

    init {
        setup()
    }

    fun setup() {
        _fakePlayer.isRunning = true
        _lastFarmLocation = _fakePlayer.position.clone()
    }

    // =========================================================
    // LÓGICA MESTRA (GATEKEEPER)
    // =========================================================
    
    /**
     * Este método agora é OPEN (não abstract).
     * Ele intercepta a morte antes que a IA de Combate (Java) tente rodar.
     */
    open fun thinkAndAct() {
        // 1. Prioridade Máxima: Morte
        if (_fakePlayer.isDead) {
            handleDeath()
            // Trava o pensamento para que subclasses não tentem targetar/atacar
            _isBusyThinking = true 
            return
        }
        
        // 2. Se estiver viajando ou ocupado com cenário, não faz nada
        if (_isBusyThinking) return
    }

    // ==========================================
    // CENÁRIO DE MORTE REALISTA (Debugável)
    // ==========================================
    
    protected fun handleDeath() {
        // Segurança: Se reviveu magicamente, libera a IA
        if (!_controls.isDead) {
            isDeadProcessing = false
            _isBusyThinking = false
            return
        }

        // Se já está no meio do processo, aguarda
        if (isDeadProcessing) return

        isDeadProcessing = true

        // Salva o spot (apenas se não morreu dentro de uma cidade/paz)
        if (!_fakePlayer.isInsideZone(ZoneId.PEACE)) {
            _lastFarmLocation = _fakePlayer.position.clone()
        }

        startDeathScenario()
    }

    private fun startDeathScenario() {
        val mgr = ArchetypeManager.getInstance()
        // Delay 1: Tempo morto no chão ("To Village")
        val delay = Rnd.get(mgr.deathDelayMin, mgr.deathDelayMax).toLong()

        println("[FakeAI] ${_fakePlayer.name} MORREU. Aguardando ${delay}s no chão.")

        ThreadPool.schedule({
            if (_fakePlayer.isOnline) toVillageStep()
        }, delay * 1000L)
    }

    private fun toVillageStep() {
        if (!_fakePlayer.isOnline) return

        println("[FakeAI] ${_fakePlayer.name} clicou 'To Village'. Teleportando...")

        // 1. AÇÃO CRÍTICA: Teleporta para a cidade AINDA MORTO
        // Isso evita loops de reviver e morrer para mobs agressivos
        _fakePlayer.teleportToClosestTown()

        // 2. Aguarda Loading Screen (Simulação)
        ThreadPool.schedule({
            if (_fakePlayer.isOnline) {
                // 3. Agora sim: Revive e Cura Total
                _fakePlayer.doRevive()
                _fakePlayer.heal() 
                
                // Limpa flags de combate residuais
                _controls.stopMove()
                _controls.botTarget = null
                
                // Delay 2: Descanso na Cidade (Buffs/Shop)
                val mgr = ArchetypeManager.getInstance()
                val restTime = Rnd.get(mgr.townRestMin, mgr.townRestMax).toLong()
                
                println("[FakeAI] ${_fakePlayer.name} reviveu na cidade. Descansando por ${restTime}s.")
                
                ThreadPool.schedule(this::returnToSpotStep, restTime * 1000L)
            }
        }, 3000L) // 3s de loading fictício
    }

    private fun returnToSpotStep() {
        // Validações
        if (!_fakePlayer.isOnline || _fakePlayer.isDead) {
             isDeadProcessing = false
             _isBusyThinking = false
             return
        }
        
        val spot = _lastFarmLocation
        if (spot == null) {
            println("[FakeAI] ${_fakePlayer.name} sem spot salvo. Ficando na cidade.")
            isDeadProcessing = false
            _isBusyThinking = false
            return
        }

        // Delay 3: Interação com GK
        val mgr = ArchetypeManager.getInstance()
        val gkTime = Rnd.get(mgr.gkTalkMin, mgr.gkTalkMax).toLong()
        
        println("[FakeAI] ${_fakePlayer.name} indo para GK. Viajando em ${gkTime}s.")

        ThreadPool.schedule({
            // Tenta teleporte inteligente via GK
            val gkDrop = TeleportData.getInstance().getNearestTeleportLocation(spot.x, spot.y, spot.z)

            if (gkDrop != null) {
                _fakePlayer.teleportTo(gkDrop, 0)
            } else {
                // Fallback: Teleporte direto se não houver rota GK
                _fakePlayer.teleportTo(spot, 0)
            }
            
            // Delay 4: Chegada no Spot (Caminhada final)
            val arrivalTime = Rnd.get(mgr.spotArrivalMin, mgr.spotArrivalMax).toLong()
            
            ThreadPool.schedule({
                // Ajuste fino de posição
                _controls.moveToLocation(spot, 0)
                
                println("[FakeAI] ${_fakePlayer.name} chegou no spot. FARM ATIVADO.")
                
                // LIBERA A IA
                isDeadProcessing = false
                _isBusyThinking = false 
                
            }, arrivalTime * 1000L)

        }, gkTime * 1000L)
    }

    // ==========================================
    // MÉTODOS DE COMBATE (ZERO GC - MANTIDOS)
    // ==========================================

    protected fun tryTargetNearestCreatureByTypeInRadius(creatureClass: Class<out Creature>, radius: Int) {
        // Validação do alvo atual
        val currentTarget = _controls.botTarget
        if (currentTarget != null) {
            if (currentTarget.isDead || 
                !GeoEngine.getInstance().canSeeTarget(_fakePlayer, currentTarget) ||
                !GeoEngine.getInstance().canMoveToTarget(_fakePlayer.x, _fakePlayer.y, _fakePlayer.z, currentTarget.x, currentTarget.y, currentTarget.z)
            ) {
                _controls.botTarget = null
            } else {
                return // Mantém alvo atual
            }
        }

        // Busca Otimizada (Winner Takes All - Sem Streams)
        val knownList = _fakePlayer.getKnownTypeInRadius(creatureClass, radius)
        if (knownList.isEmpty()) return

        var bestTarget: Creature? = null
        var minDistance = Double.MAX_VALUE

        for (obj in knownList) {
            val creature = obj as? Creature ?: continue

            if (creature.isDead) continue
            if (creature is Player && !isAttackablePlayer(creature)) continue
            if (!GeoEngine.getInstance().canSeeTarget(_fakePlayer, creature)) continue
            
            val dist = MathUtil.calculateDistance(_fakePlayer, creature, false)
            if (dist > radius) continue 

            if (!GeoEngine.getInstance().canMoveToTarget(_fakePlayer.x, _fakePlayer.y, _fakePlayer.z, creature.x, creature.y, creature.z)) continue

            if (shouldIgnoreTarget(creature)) continue

            if (dist < minDistance) {
                minDistance = dist
                bestTarget = creature
            }
        }

        if (bestTarget != null) {
            _controls.botTarget = bestTarget
        }
    }

    private fun shouldIgnoreTarget(target: Creature): Boolean {
        // Verifica Party/Clan sem alocar Streams
        val players = World.getInstance().players
        for (other in players) {
            if (other == null || other === _fakePlayer) continue
            
            if (other.target === target) {
                if (other is FakePlayer) {
                    if (isSamePartyOrClan(_fakePlayer, other)) {
                        return false // Ally batendo -> Assistir
                    }
                }
                return true // Outro batendo -> Ignorar (Anti-KS)
            }
        }
        return false 
    }

    protected fun checkTargetLost(target: WorldObject?): Boolean {
        if (target is Player) {
            if (target.isFakeDeath) {
                target.stopFakeDeath(true)
                return false
            }
        }
        return target == null
    }

    open fun castSpell(skill: L2Skill) {
        if (_fakePlayer.cast.isCastingNow) return

        if (skill.targetType == SkillTargetType.GROUND) {
            val target = _fakePlayer.target
            if (target != null) {
                val loc = Location(target.x, target.y, target.z)
                _controls.moveToLocation(loc, _fakePlayer.template.collisionRadius.toInt())
            }
        } else {
            val target = _controls.botTarget
            if (checkTargetLost(target)) {
                if (skill.isOffensive && target != null) {
                    _controls.botTarget = null
                }
                _fakePlayer.cast.stop()
                return
            }
            
            if (skill.hitTime > 50 && !skill.isSimultaneousCast) {
                _controls.stopMove()
            }

            if (target is Creature) {
                _controls.castSpell(skill, target)
            }
        }
    }

    protected fun applyDefaultBuffs() {
        val buffs = getBuffs() ?: return 

        for (buffInfo in buffs) {
            try {
                val skillId = buffInfo[0]
                val level = buffInfo[1]
                
                val hasBuff = _fakePlayer.allEffects.any { it.skill.id == skillId && it.effectType == EffectType.BUFF }
                
                if (!hasBuff) {
                    SkillTable.getInstance().getInfo(skillId, level)?.getEffects(_fakePlayer, _fakePlayer)
                } 
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    protected fun getCityWaypoint(townName: String, fakePlayer: Player): Location? {
        val waypoints = WalkerHolder.getCityWaypoints(townName)
        if (waypoints.isNullOrEmpty()) return null

        val valid = ArrayList<Location>()
        for (loc in waypoints) {
             if (GeoEngine.getInstance().canMove(fakePlayer.x, fakePlayer.y, fakePlayer.z, loc.x, loc.y, loc.z, null)) {
                 valid.add(loc)
             }
        }

        return if (valid.isNotEmpty()) {
            valid[Rnd.get(valid.size)]
        } else {
            var best: Location? = null
            var minDist = Double.MAX_VALUE
            for (loc in waypoints) {
                val d = MathUtil.calculateDistance(fakePlayer, loc, false)
                if (d < minDist) {
                    minDist = d
                    best = loc
                }
            }
            best
        }
    }
    
    protected fun castSelfSpell(skill: L2Skill) {
        if (!_fakePlayer.cast.isCastingNow && !_fakePlayer.isSkillDisabled(skill)) {
            _controls.castSelfSpell(skill)
        }
    }
    
    // ==========================================
    // BRIDGE METHODS
    // ==========================================
    
    fun isBusyThinking(): Boolean {
        return _isBusyThinking
    }

    fun setBusyThinking(thinking: Boolean) {
        _isBusyThinking = thinking
    }
    
    companion object {
        @JvmStatic
        fun isAttackablePlayer(player: Player): Boolean {
            return player.pvpFlag > 0 || player.karma > 0 || player.clanId > 0 || player.isInsideZone(ZoneId.PVP)
        }

        @JvmStatic
        fun isSamePartyOrClan(p1: FakePlayer?, p2: FakePlayer?): Boolean {
            if (p1 == null || p2 == null) return false
            if (p1.party != null && p1.party == p2.party) return true
            if (p1.clan != null && p1.clan == p2.clan) return true
            return false
        }
    }
    
    protected fun toVillageOnDeath() = handleDeath()

    protected abstract fun getBuffs(): Array<IntArray>
}