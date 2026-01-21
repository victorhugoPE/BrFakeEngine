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
 * CÉREBRO BASE - OTIMIZADO EM KOTLIN
 * * Performance:
 * - Substituído Streams por Loops diretos (Zero-GC).
 * - Algoritmo de busca de alvo O(n) sem alocação de lista.
 * - Null Safety nativo.
 */
abstract class FakePlayerAI(
    // @JvmField expõe como campo público pro Java (sem get/set) para compatibilidade com subclasses
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

    // ==========================================
    // LÓGICA DE MORTE (Safe & Clean)
    // ==========================================
    
    protected fun handleDeath() {
        if (_controls.isDead) {
            if (!isDeadProcessing) {
                isDeadProcessing = true
                startDeathScenario()
            }
            return
        }

        // Atualiza save point se estiver parado e seguro
        if (!_fakePlayer.isInCombat && !_controls.isMoving) {
            _lastFarmLocation = _fakePlayer.position.clone()
        }

        isDeadProcessing = false
    }

    private fun startDeathScenario() {
        val mgr = ArchetypeManager.getInstance()
        val delay = Rnd.get(mgr.deathDelayMin, mgr.deathDelayMax).toLong()

        ThreadPool.schedule({
            if (_fakePlayer.isOnline) toVillageStep()
        }, delay * 1000L)
    }

    private fun toVillageStep() {
        _fakePlayer.doRevive()
        
        val mgr = ArchetypeManager.getInstance()
        val restTime = Rnd.get(mgr.townRestMin, mgr.townRestMax).toLong()

        // Usando method reference do Kotlin
        ThreadPool.schedule(this::returnToSpotStep, restTime * 1000L)
    }

    private fun returnToSpotStep() {
        val spot = _lastFarmLocation ?: return

        // Busca GK
        val gkDrop = TeleportData.getInstance().getNearestTeleportLocation(spot.x, spot.y, spot.z)

        if (gkDrop != null) {
            val mgr = ArchetypeManager.getInstance()
            val gkTime = Rnd.get(mgr.gkTalkMin, mgr.gkTalkMax).toLong()

            ThreadPool.schedule({
                _fakePlayer.teleportTo(gkDrop, 0)
                
                val arrivalTime = Rnd.get(mgr.spotArrivalMin, mgr.spotArrivalMax).toLong()
                
                ThreadPool.schedule({
                    // Caminhada final
                    _controls.moveToLocation(spot, 0)
                }, arrivalTime * 1000L)

            }, gkTime * 1000L)
        } else {
            // Fallback
            _fakePlayer.teleportTo(spot, 0)
        }
    }

    // ==========================================
    // MÉTODOS DE COMBATE OTIMIZADOS (ZERO GC)
    // ==========================================

    /**
     * OTIMIZAÇÃO CRÍTICA:
     * Substitui o Stream().sorted() do Java.
     * Faz apenas UMA passagem pela lista e não cria objetos temporários.
     */
    protected fun tryTargetNearestCreatureByTypeInRadius(creatureClass: Class<out Creature>, radius: Int) {
        // 1. Validação do alvo atual
        val currentTarget = _controls.botTarget
        if (currentTarget != null) {
            if (currentTarget.isDead || 
                !GeoEngine.getInstance().canSeeTarget(_fakePlayer, currentTarget) ||
                !GeoEngine.getInstance().canMoveToTarget(_fakePlayer.x, _fakePlayer.y, _fakePlayer.z, currentTarget.x, currentTarget.y, currentTarget.z)
            ) {
                _controls.botTarget = null
            } else {
                // Alvo atual ainda é válido, mantém
                return
            }
        }

        // 2. Busca Otimizada (Winner Takes All)
        val knownList = _fakePlayer.getKnownTypeInRadius(creatureClass, radius)
        if (knownList.isEmpty()) return

        var bestTarget: Creature? = null
        var minDistance = Double.MAX_VALUE

        // Loop manual é mais rápido que iterator/stream aqui
        for (obj in knownList) {
            val creature = obj as? Creature ?: continue

            // Filtros rápidos primeiro
            if (creature.isDead) continue
            if (creature is Player && !isAttackablePlayer(creature)) continue
            
            // Filtros pesados (GeoEngine) só se passar nos leves
            if (!GeoEngine.getInstance().canSeeTarget(_fakePlayer, creature)) continue
            
            // Distância (MathUtil otimizado)
            val dist = MathUtil.calculateDistance(_fakePlayer, creature, false)
            if (dist > radius) continue // Radius check preciso

            // GeoEngine de caminho (o mais pesado)
            if (!GeoEngine.getInstance().canMoveToTarget(_fakePlayer.x, _fakePlayer.y, _fakePlayer.z, creature.x, creature.y, creature.z)) continue

            // Lógica de Party/Clan (Evitar KS ou Friendly Fire)
            if (shouldIgnoreTarget(creature)) continue

            // Se chegou aqui, é válido. É o mais perto?
            if (dist < minDistance) {
                minDistance = dist
                bestTarget = creature
            }
        }

        if (bestTarget != null) {
            _controls.botTarget = bestTarget
        }
    }

    /**
     * Lógica auxiliar para verificar Party/Clan sem alocar Streams
     */
    private fun shouldIgnoreTarget(target: Creature): Boolean {
        // Verifica se alguém da minha party/clan já está batendo nele
        // Essa lógica é cara, então só roda se necessário
        val players = World.getInstance().players
        for (other in players) {
            if (other == null || other === _fakePlayer) continue
            
            if (other.target === target) {
                if (other is FakePlayer) {
                    if (isSamePartyOrClan(_fakePlayer, other)) {
                        return false // Ally batendo -> Assistir (OK)
                    }
                }
                return true // Inimigo/Random batendo -> Evitar KS (Ignore)
            }
        }
        return false // Ninguém batendo -> OK
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

            // O Kotlin faz o cast seguro automaticamente
            if (target is Creature) {
                _controls.castSpell(skill, target)
            }
        }
    }

    protected fun applyDefaultBuffs() {
        val buffs = getBuffs() ?: return // Null safety

        for (buffInfo in buffs) {
            try {
                val skillId = buffInfo[0]
                val level = buffInfo[1]
                
                // Verifica se já tem o buff de forma otimizada
                val hasBuff = _fakePlayer.allEffects.any { it.skill.id == skillId && it.effectType == EffectType.BUFF }
                
                if (!hasBuff) {
                    // Correção aplicada: Chamada explícita de getEffects
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

        // Filtragem simples
        val valid = ArrayList<Location>()
        for (loc in waypoints) {
             if (GeoEngine.getInstance().canMove(fakePlayer.x, fakePlayer.y, fakePlayer.z, loc.x, loc.y, loc.z, null)) {
                 valid.add(loc)
             }
        }

        return if (valid.isNotEmpty()) {
            valid[Rnd.get(valid.size)]
        } else {
            // Find min distance
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
    // BRIDGE METHODS (CORREÇÃO DE COMPATIBILIDADE JAVA)
    // ==========================================
    
    // Estes métodos são necessários porque o AITask.java chama isBusyThinking()
    // e o Kotlin por padrão não gerou esse método para a variável _isBusyThinking
    fun isBusyThinking(): Boolean {
        return _isBusyThinking
    }

    fun setBusyThinking(thinking: Boolean) {
        _isBusyThinking = thinking
    }
    
    // Companion Object para métodos estáticos (Utils)
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
    
    // Mantido compatibilidade
    protected fun toVillageOnDeath() = handleDeath()

    // Abstract methods
    abstract fun thinkAndAct()
    protected abstract fun getBuffs(): Array<IntArray>
}