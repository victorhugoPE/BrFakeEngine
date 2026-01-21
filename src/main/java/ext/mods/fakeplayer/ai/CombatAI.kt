package ext.mods.fakeplayer.ai

import ext.mods.commons.math.MathUtil
import ext.mods.commons.pool.ThreadPool
import ext.mods.commons.random.Rnd
import ext.mods.fakeplayer.FakePlayer
import ext.mods.fakeplayer.helper.FakePlayerHelpers
import ext.mods.fakeplayer.holder.WalkerHolder
import ext.mods.fakeplayer.model.BotSkill
import ext.mods.fakeplayer.model.HealingSpell
import ext.mods.fakeplayer.model.OffensiveSpell
import ext.mods.fakeplayer.model.SpellUsageCondition
import ext.mods.fakeplayer.model.SupportSpell
import ext.mods.fakeplayer.pool.LocationPool
import ext.mods.gameserver.data.SkillTable
import ext.mods.gameserver.enums.ZoneId
import ext.mods.gameserver.enums.items.ShotType
import ext.mods.gameserver.enums.items.WeaponType
import ext.mods.gameserver.enums.skills.EffectType
import ext.mods.gameserver.enums.skills.SkillTargetType
import ext.mods.gameserver.geoengine.GeoEngine
import ext.mods.gameserver.model.actor.Creature
import ext.mods.gameserver.model.actor.Npc
import ext.mods.gameserver.model.actor.Player
import ext.mods.gameserver.model.actor.instance.Monster
import ext.mods.gameserver.model.location.Location
import ext.mods.gameserver.skills.L2Skill
import java.util.Comparator
import java.util.stream.Collectors
import kotlin.math.abs

/**
 * CÉREBRO DE COMBATE "ULTIMATE" (V9.0)
 * Fusão completa: Comportamento Humano (Java Antigo) + Performance Extrema (Kotlin Novo).
 */
abstract class CombatAI(character: FakePlayer) : FakePlayerAI(character) {

    // ==========================================
    // 1. VARIÁVEIS DE CONTROLE E ESTADO
    // ==========================================
    
    // Controle de Inatividade e Retorno
    private var lastActionTime = System.currentTimeMillis()
    private val INACTIVITY_TIMEOUT_MS = 60000L // 60 segundos sem fazer nada = teleporte
    private val RETURN_LOC = Location(83400, 147943, -3400) // Giran Harbor (Fallback)

    // Controle de Comportamento em Cidade (Do seu código antigo)
    private var lastPeaceActionTime = 0L
    private val PEACE_ACTION_INTERVAL = 10000L // A cada 10s faz algo na cidade
    private var isSitting = false
    private var cityWaypoint: Location? = null

    // Controles de Combate e Performance
    private var _lastShotId = 0
    private var _lastTargetId = 0
    private var _lastTargetHp = 0.0
    private var _stuckTimestamp = 0L
    private val STUCK_TIMEOUT = 5000L 
    
    // Controle de Exploração (Roaming)
    private var _lastSearchMoveTime = 0L
    private val SEARCH_MOVE_DELAY = 3000L 
    
    // Controle de Superlotação
    private var _lastCrowdCheckTime = 0L
    private val CROWD_CHECK_DELAY = 10000L
    private val CROWD_LIMIT = 3 
    private val CROWD_RADIUS = 800

    init {
        // Tenta pegar waypoint da cidade mais próxima ou Giran
        cityWaypoint = getCityWaypoint("Giran", _fakePlayer)
    }

    // ==========================================
    // 2. O GRANDE LOOP (CÉREBRO)
    // ==========================================

    override fun thinkAndAct() {
        // A. Verifica Morte
        if (_fakePlayer.isDead || _fakePlayer.isAlikeDead) {
            handleDeath()
            return
        }

        _fakePlayer.isRunning = true

        // B. Verifica Colisão (Evita bots empilhados)
        if (checkCollision()) return

        // C. Comportamento de Zona de Paz (Cidade/Social)
        if (isPeaceZone()) {
            performPeaceZoneBehavior()
            return
        }

        // D. Verifica se está travado (Cast/Paralisia)
        if (_fakePlayer.cast.isCastingNow() || _fakePlayer.isMovementDisabled) {
            lastActionTime = System.currentTimeMillis()
            return
        }

        // E. Manutenção (Shots, Buffs, Potions)
        handleShots()
        selfSupportBuffs() 

        // F. Varredura (Spoil/Sweep) - Prioridade alta para anões
        if (trySweep()) {
            lastActionTime = System.currentTimeMillis()
            return
        }

        // G. Seleção de Alvo
        var target = _controls.botTarget
        if (target == null || (target as? Creature)?.isDead == true) {
            _controls.botTarget = null
            tryTargetNearestCreatureByTypeInRadius(FakePlayerHelpers.getTargetClass(), FakePlayerHelpers.getTargetRange())
            target = _controls.botTarget
        }

        // H. Árvore de Decisão
        if (target is Creature) {
            // MODO COMBATE
            lastActionTime = System.currentTimeMillis()
            
            // Verifica se está bugado na parede (Stuck)
            if (checkTargetStuck(target)) {
                _controls.botTarget = null
                moveUsingRandomOffset(200)
                return
            }
            handleCombat(target)
            
        } else {
            // MODO OCIOSO (IDLE)
            
            // 1. Prioridade: Seguir Party (Se existir)
            if (handlePartyLogic()) {
                lastActionTime = System.currentTimeMillis()
                return
            }

            // 2. Verificar Lotação e Migrar de Spot
            if (checkCrowdingAndRelocate()) {
                lastActionTime = System.currentTimeMillis()
                return
            }
            
            // 3. Explorar a área (Roaming)
            handleIdleExploration()
            
            // 4. Teleporte de Emergência (Se ficou 60s sem fazer NADA)
            if (System.currentTimeMillis() - lastActionTime > INACTIVITY_TIMEOUT_MS) {
                teleportToSafeZone()
                lastActionTime = System.currentTimeMillis()
            }
        }
    }

    // ==========================================
    // 3. LÓGICA DE CIDADE E COLISÃO (Do seu Java)
    // ==========================================

    private fun isPeaceZone(): Boolean {
        return _fakePlayer.isInsideZone(ZoneId.PEACE)
    }

    private fun checkCollision(): Boolean {
        // Só evita colisão em zona de paz
        if (!_fakePlayer.isInsideZone(ZoneId.PEACE)) return false

        val minSafeDistance = 50.0 
        
        // Otimização: Busca apenas players próximos
        val players = _fakePlayer.getKnownTypeInRadius(Player::class.java, 200)
        
        for (player in players) {
            if (player.objectId == _fakePlayer.objectId || player.isDead) continue
            
            val dist = MathUtil.calculateDistance(_fakePlayer, player, false)
            if (dist < minSafeDistance) {
                // Encontra um lugar livre e move
                val newLoc = findFreeLocationNearby(_fakePlayer, minSafeDistance * 1.5)
                if (newLoc != null) {
                    _controls.moveToLocation(newLoc, 0)
                    LocationPool.release(newLoc) // Limpa memória
                    return true
                }
            }
        }
        return false
    }

    private fun findFreeLocationNearby(player: FakePlayer, radius: Double): Location? {
        val tryLimit = 10
        for (i in 0 until tryLimit) {
            val offsetX = Rnd.get(-radius.toInt(), radius.toInt())
            val offsetY = Rnd.get(-radius.toInt(), radius.toInt())
            
            val newX = player.x + offsetX
            val newY = player.y + offsetY
            
            // Verifica GeoEngine para não entrar na parede
            if (GeoEngine.getInstance().canMove(player.x, player.y, player.z, newX, newY, player.z, null)) {
                return LocationPool.get(newX, newY, player.z)
            }
        }
        return null
    }

    private fun performPeaceZoneBehavior() {
        if (System.currentTimeMillis() - lastPeaceActionTime < PEACE_ACTION_INTERVAL) return
        lastPeaceActionTime = System.currentTimeMillis()
        
        // Chance de ficar parado (Comportamento humano)
        if (Rnd.get(100) > 30) return

        when (Rnd.get(7)) {
            0, 6 -> { // Andar aleatoriamente no Waypoint
                cityWaypoint?.let { wp ->
                    val dest = LocationPool.get(wp.x, wp.y, wp.z)
                    dest.x += Rnd.get(-150, 150)
                    dest.y += Rnd.get(-150, 150)
                    
                    if (GeoEngine.getInstance().canMove(_fakePlayer.x, _fakePlayer.y, _fakePlayer.z, dest.x, dest.y, dest.z, null)) {
                        _controls.moveToLocation(dest, 50)
                    }
                    LocationPool.release(dest)
                }
            }
            1 -> { // Ir até NPC próximo
                val npc = getNearestNpc()
                if (npc != null && GeoEngine.getInstance().canMoveToTarget(_fakePlayer, npc)) {
                    val loc = LocationPool.get(npc.x, npc.y, npc.z)
                    _controls.moveToLocation(loc, 75)
                    LocationPool.release(loc)
                }
            }
            2 -> { // Virar (Random Heading - Simulado com movimento curto)
                val offset = Rnd.get(10, 20)
                moveUsingRandomOffset(offset)
            }
            3 -> { // Sentar e levantar
                if (!isSitting) {
                    _fakePlayer.sitDown()
                    isSitting = true
                    ThreadPool.schedule({
                        _fakePlayer.standUp()
                        isSitting = false
                    }, 60000) // Senta por 1 min
                }
            }
            4 -> { // Ir até GK (Gatekeeper)
                val gk = getNearestTeleportNpc()
                if (gk != null && GeoEngine.getInstance().canMoveToTarget(_fakePlayer, gk)) {
                    val loc = LocationPool.get(gk.x, gk.y, gk.z)
                    _controls.moveToLocation(loc, 75)
                    LocationPool.release(loc)
                }
            }
            5 -> { // Voltar para spot salvo (se existir)
                _lastFarmLocation?.let { saved ->
                    if (GeoEngine.getInstance().canMoveToTarget(_fakePlayer.x, _fakePlayer.y, _fakePlayer.z, saved.x, saved.y, saved.z)) {
                        _controls.moveToLocation(saved, 50)
                    }
                }
            }
        }
    }

    private fun getNearestNpc(): Npc? {
        return _fakePlayer.getKnownType(Npc::class.java)
            .minByOrNull { MathUtil.calculateDistance(_fakePlayer, it, false) }
    }

    private fun getNearestTeleportNpc(): Npc? {
        return _fakePlayer.getKnownType(Npc::class.java)
            .filter { it.template.type.equals("Gatekeeper", ignoreCase = true) }
            .minByOrNull { MathUtil.calculateDistance(_fakePlayer, it, false) }
    }

    // ==========================================
    // 4. LÓGICA DE EXPLORAÇÃO E PARTY
    // ==========================================

    private fun handlePartyLogic(): Boolean {
        val party = _fakePlayer.party ?: return false
        val leader = party.leader ?: return false

        // Não sigo se eu sou o líder ou se ele está morto
        if (leader.objectId == _fakePlayer.objectId || leader.isDead) return false

        val distToLeader = MathUtil.calculateDistance(_fakePlayer, leader, true)
        
        // Segue se estiver longe (> 300)
        if (distToLeader > 300) {
            val loc = LocationPool.get(leader.x, leader.y, leader.z)
            // Offset aleatório para não fundir com o líder
            loc.x += Rnd.get(-50, 50)
            loc.y += Rnd.get(-50, 50)
            
            _controls.moveToLocation(loc, 0)
            LocationPool.release(loc)
            return true
        }
        return false
    }

    private fun checkCrowdingAndRelocate(): Boolean {
        if (System.currentTimeMillis() - _lastCrowdCheckTime < CROWD_CHECK_DELAY) return false
        _lastCrowdCheckTime = System.currentTimeMillis()

        val nearbyPlayers = _fakePlayer.getKnownTypeInRadius(Player::class.java, CROWD_RADIUS)
        var crowdCount = 0
        
        for (p in nearbyPlayers) {
            if (p.objectId == _fakePlayer.objectId) continue
            if (isSamePartyOrClan(_fakePlayer, p as? FakePlayer)) continue 
            if (p.isDead) continue
            crowdCount++
        }

        if (crowdCount >= CROWD_LIMIT) {
            // Migra para longe (1000-2000 range)
            val angle = Math.toRadians(Rnd.get(0, 360).toDouble())
            val distance = Rnd.get(1000, 2000)
            
            val newX = _fakePlayer.x + (distance * Math.cos(angle)).toInt()
            val newY = _fakePlayer.y + (distance * Math.sin(angle)).toInt()
            
            val destLocation = LocationPool.get(newX, newY, _fakePlayer.z)
            _controls.moveToLocation(destLocation, 0)
            
            // Atualiza o LAR para o novo local
            _lastFarmLocation = Location(destLocation.x, destLocation.y, destLocation.z)
            LocationPool.release(destLocation)
            
            _lastSearchMoveTime = System.currentTimeMillis() + 5000 
            return true
        }
        return false
    }

    private fun handleIdleExploration() {
        if (_fakePlayer.isMoving) return
        if (System.currentTimeMillis() - _lastSearchMoveTime < SEARCH_MOVE_DELAY) return

        val myLoc = LocationPool.get(_fakePlayer.x, _fakePlayer.y, _fakePlayer.z)
        val anchor = _lastFarmLocation ?: myLoc
        
        val distFromAnchor = MathUtil.calculateDistance(_fakePlayer.x, _fakePlayer.y, _fakePlayer.z, anchor.x, anchor.y, anchor.z, true)
        LocationPool.release(myLoc)

        // Se longe de casa, volta
        if (distFromAnchor > 2000) {
            _controls.moveToLocation(anchor, 100)
            _lastSearchMoveTime = System.currentTimeMillis()
            return
        }

        // Anda aleatoriamente
        moveUsingRandomOffset(500)
        _lastSearchMoveTime = System.currentTimeMillis()
    }

    // ==========================================
    // 5. LÓGICA DE COMBATE
    // ==========================================

    private fun checkTargetStuck(target: Creature): Boolean {
        val now = System.currentTimeMillis()
        val currentHp = target.status.hp

        if (target.objectId != _lastTargetId) {
            _lastTargetId = target.objectId
            _lastTargetHp = currentHp
            _stuckTimestamp = now
            return false
        }

        if (currentHp < _lastTargetHp) {
            _lastTargetHp = currentHp
            _stuckTimestamp = now 
            return false
        }

        if (now - _stuckTimestamp > STUCK_TIMEOUT) {
            _lastTargetId = 0 
            return true
        }
        return false
    }

    private fun handleCombat(target: Creature) {
        val dist = MathUtil.calculateDistance(_fakePlayer, target, true)
        val attackRange = getRealAttackRange()

        _lastSearchMoveTime = System.currentTimeMillis()

        if (isArcher()) {
            if (dist < 100) { 
                runAway(target)
                return
            }
        }

        val shouldUseSkill = Rnd.get(100) < (changeOfUsingSkill() * 100)
        
        if (shouldUseSkill) {
            if (isMageClass()) {
                if (tryUsingMageSkill(target)) return
            } else {
                if (tryUsingOffensiveSkill(target)) return
            }
        }

        if (dist <= attackRange + _fakePlayer.template.collisionRadius + target.template.collisionRadius + 20) {
            if (GeoEngine.getInstance().canSeeTarget(_fakePlayer, target)) {
                _controls.forceAutoAttack(target)
            } else {
                moveToPawn(target, 20)
            }
        } else {
            moveToPawn(target, attackRange)
        }
    }

    protected fun tryUsingMageSkill(target: Creature): Boolean {
        val spells = getOffensiveSpells()
        if (spells.isNullOrEmpty()) return false

        var bestSkill: L2Skill? = null
        var maxPower = -1.0

        for (i in spells.indices) {
            val spellInfo = spells[i]
            val skill = _fakePlayer.getSkill(spellInfo.skillId) ?: continue

            if (_fakePlayer.isSkillDisabled(skill)) continue
            if (_fakePlayer.status.mp < skill.mpConsume) continue
            if (skill.reuseDelay > 0 && _fakePlayer.isSkillDisabled(skill)) continue
            
            val dist = MathUtil.calculateDistance(_fakePlayer, target, true)
            if (dist > skill.castRange + 150) continue

            if (skill.power > maxPower) {
                maxPower = skill.power
                bestSkill = skill
            }
        }

        if (bestSkill != null) {
            castSpell(bestSkill)
            return true
        }
        return false
    }

    protected fun tryUsingOffensiveSkill(target: Creature): Boolean {
        val spells = getOffensiveSpells()
        if (spells.isNullOrEmpty()) return false

        val startIndex = Rnd.get(spells!!.size)
        
        for (i in spells.indices) {
            val index = (startIndex + i) % spells.size
            val spellInfo = spells[index]
            val skillId = spellInfo.skillId
            val skill = _fakePlayer.getSkill(skillId) ?: continue

            if (_fakePlayer.isSkillDisabled(skill)) continue
            if (_fakePlayer.status.mp < skill.mpConsume) continue
            if (skill.reuseDelay > 0 && _fakePlayer.isSkillDisabled(skill)) continue
            if (!checkSpellCondition(spellInfo.condition, spellInfo.conditionValue, target)) continue

            val dist = MathUtil.calculateDistance(_fakePlayer, target, true)
            if (dist > skill.castRange + 100) continue 

            castSpell(skill)
            return true
        }
        return false
    }
    
    private fun trySweep(): Boolean {
        val sweepSkill = _fakePlayer.getSkill(42) ?: return false
        val target = _controls.botTarget
        
        if (target is Monster && target.isDead) {
            if (MathUtil.calculateDistance(_fakePlayer, target, true) < sweepSkill.castRange + 50) {
                 castSpell(sweepSkill)
                 _controls.botTarget = null 
                 return true
            }
        }
        return false
    }

    protected fun selfSupportBuffs() {
        val selfSpells = getSelfSupportSpells()
        if (selfSpells.isNullOrEmpty()) return

        for (selfBuff in selfSpells) {
            // Verifica buffs usando stream API ou loop
            val hasEffect = _fakePlayer.allEffects.any { it.skill.id == selfBuff.skillId }
            if (hasEffect) continue

            val skill = _fakePlayer.getSkill(selfBuff.skillId) ?: continue

            if (_fakePlayer.isSkillDisabled(skill)) continue
            if (_fakePlayer.status.mp < skill.mpConsume) continue

            if (checkSpellCondition(selfBuff.condition, selfBuff.conditionValue, _fakePlayer)) {
                castSelfSpell(skill)
                return
            }
        }
    }

    // ==========================================
    // 6. BRIDGE METHODS (LEGACY SUPPORT)
    // ==========================================

    open fun tryAttackingUsingMageOffensiveSkill() {
        val target = _controls.botTarget as? Creature ?: return
        tryUsingMageSkill(target)
    }

    open fun tryAttackingUsingFighterOffensiveSkill() {
        val target = _controls.botTarget as? Creature ?: return
        tryUsingOffensiveSkill(target)
    }
    
    open fun getArrowId(): Int {
        return 1345 
    }

    open fun getBestAvailableHealingSpell(): HealingSpell? {
        val spells = getHealingSpells()
        if (spells.isNullOrEmpty()) return null
        
        for (spell in spells) {
            val skill = _fakePlayer.getSkill(spell.skillId) ?: continue
            
            if (_fakePlayer.isSkillDisabled(skill)) continue
            if (_fakePlayer.status.mp < skill.mpConsume) continue
            
            val target = _fakePlayer.target as? Creature ?: _fakePlayer
            
            if (checkSpellCondition(spell.condition, spell.conditionValue, target)) {
                return spell
            }
        }
        return null
    }

    // ==========================================
    // 7. UTILITÁRIOS E HELPERS
    // ==========================================

    private fun checkSpellCondition(cond: SpellUsageCondition, value: Int, target: Creature): Boolean {
        return when (cond) {
            SpellUsageCondition.NONE -> true
            SpellUsageCondition.LESSHPPERCENT -> getHpPercent(target) < value
            SpellUsageCondition.MOREHPPERCENT -> getHpPercent(target) > value
            SpellUsageCondition.MISSINGCP -> {
                if (target is Player) target.status.cp < target.status.maxCp - value else false
            }
            else -> true
        }
    }
    
    private fun getHpPercent(creature: Creature): Double = (creature.status.hp / creature.status.maxHp) * 100.0
    private fun getCpPercent(creature: Creature): Double = if (creature is Player) (creature.status.cp / creature.status.maxCp) * 100.0 else 0.0
    private fun getMpPercent(creature: Creature): Double = (creature.status.mp / creature.status.maxMp) * 100.0
    
    private fun getRealAttackRange(): Int {
        val weaponType = _fakePlayer.attackType
        return when (weaponType) {
            WeaponType.BOW -> 900
            WeaponType.POLE -> 80
            WeaponType.DAGGER, WeaponType.DUAL, WeaponType.SWORD, WeaponType.BLUNT, WeaponType.FIST, WeaponType.ETC -> 40
            else -> 40
        }
    }

    protected fun handleShots() {
        val shotType = getShotType()
        if (shotType == ShotType.SOULSHOT || shotType == ShotType.BLESSED_SPIRITSHOT) {
            val shotId = getShotId()
            if (shotId > 0) {
                val item = _fakePlayer.inventory.getItemByItemId(shotId)
                if (item == null || item.count <= 20) {
                    _fakePlayer.inventory.addItem(shotId, 500)
                }
                
                if (!_fakePlayer.autoSoulShot.contains(shotId)) {
                    _fakePlayer.addAutoSoulShot(shotId)
                    _fakePlayer.rechargeShots(true, true)
                }
            }
        }
        
        if (isArcher()) {
            val arrowId = getArrowId()
            if (arrowId > 0) {
                val arrow = _fakePlayer.inventory.getItemByItemId(arrowId)
                if (arrow == null || arrow.count <= 20) {
                    _fakePlayer.inventory.addItem(arrowId, 500)
                }
                _fakePlayer.checkAndEquipArrows()
            }
        }
    }

    protected open fun getShotId(): Int {
        val playerLevel = _fakePlayer.status.level
        val isSoul = getShotType() == ShotType.SOULSHOT
        
        return when {
            playerLevel < 20 -> if (isSoul) 1835 else 3947
            playerLevel < 40 -> if (isSoul) 1463 else 3948
            playerLevel < 52 -> if (isSoul) 1464 else 3949
            playerLevel < 61 -> if (isSoul) 1465 else 3950
            playerLevel < 76 -> if (isSoul) 1466 else 3951
            else -> if (isSoul) 1467 else 3952
        }
    }
    
    protected fun isArcher(): Boolean = _fakePlayer.attackType == WeaponType.BOW
    
    protected fun isMageClass(): Boolean {
        return getShotType() == ShotType.BLESSED_SPIRITSHOT
    }

    private fun runAway(target: Creature) {
        val angle = Math.toRadians(Rnd.get(90, 270).toDouble())
        val distance = 400 
        val newX = _fakePlayer.x + (distance * Math.cos(angle)).toInt()
        val newY = _fakePlayer.y + (distance * Math.sin(angle)).toInt()
        
        val dest = LocationPool.get(newX, newY, _fakePlayer.z)
        _controls.movePawnTo(dest.x, dest.y, dest.z)
        LocationPool.release(dest)
    }
    
    private fun moveToPawn(target: Creature, range: Int) {
        if (_fakePlayer.isMoving) return
        val loc = LocationPool.get(target.x, target.y, target.z)
        _controls.moveToLocation(loc, range)
        LocationPool.release(loc)
    }
    
    private fun moveUsingRandomOffset(offset: Int) {
        val x = _fakePlayer.x + Rnd.get(-offset, offset)
        val y = _fakePlayer.y + Rnd.get(-offset, offset)
        
        val dest = LocationPool.get(x, y, _fakePlayer.z)
        _controls.movePawnTo(dest.x, dest.y, dest.z)
        LocationPool.release(dest)
    }
    
    private fun teleportToSafeZone() {
        try {
            // CORREÇÃO: O Java retorna Location, então verificamos se não é nulo
            val saved = _fakePlayer.isFakeLocationRandom
            if (saved != null) {
                _fakePlayer.setFakeSaveLocation(saved)
            }
        } catch (e: Exception) {}

        _fakePlayer.teleportToLocation(RETURN_LOC.x, RETURN_LOC.y, RETURN_LOC.z, 75)
    }

    fun handleConsumable(player: FakePlayer, itemId: Int) {
        if (player.inventory.getItemByItemId(itemId) != null) _controls.useItem(itemId)
    }
    
    fun handlePotions(player: FakePlayer, cpId: Int, hpId: Int, mpId: Int) {
        if (getCpPercent(player) < 80) handleConsumable(player, cpId)
        if (getHpPercent(player) < 70) handleConsumable(player, hpId)
        if (getMpPercent(player) < 60) handleConsumable(player, mpId)
    }
    
    open fun getDebugInfo(): String {
        val sb = StringBuilder()
        val now = System.currentTimeMillis()

        sb.append("<font color=LEVEL>=== FakePlayer Brain V9 ===</font><br>")
        sb.append("Bot: ${_fakePlayer.name} | HP: ${_fakePlayer.status.hp.toInt()}<br>")
        
        val target = _controls.botTarget
        if (target != null) {
            sb.append("Target: <font color=FF0000>${target.name}</font><br>")
        } else {
            sb.append("Target: None<br>")
        }
        
        sb.append("Activity: ")
        if (isPeaceZone()) sb.append("PEACE ZONE")
        else if (_fakePlayer.isInCombat) sb.append("COMBAT")
        else sb.append("EXPLORING")
        sb.append("<br>")
        
        val idleTime = (now - lastActionTime) / 1000
        sb.append("Idle Time: ${idleTime}s<br>")
        
        return sb.toString()
    }

    protected abstract fun getShotType(): ShotType
    protected abstract fun getOffensiveSpells(): List<OffensiveSpell>?
    protected abstract fun getHealingSpells(): List<HealingSpell>?
    protected abstract fun getSelfSupportSpells(): List<SupportSpell>?
    
    protected open fun changeOfUsingSkill(): Double = 0.33
}