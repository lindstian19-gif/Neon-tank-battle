package com.example.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pure Kotlin 2D arena physics and match engine operating in a fixed 900x1280 portrait arena.
 */
class GameEngine(
    private val onShoot: () -> Unit = {},
    private val onBounce: () -> Unit = {},
    private val onPowerUp: () -> Unit = {},
    private val onShieldBlock: () -> Unit = {},
    private val onExplosion: () -> Unit = {},
    private val onWin: () -> Unit = {},
    private val onDash: () -> Unit = {}
) {
    companion object {
        const val ARENA_WIDTH = 900f
        const val ARENA_HEIGHT = 1280f
        const val ARENA_MARGIN = 22f

        private const val BASE_SPEED = 5.6f
        private const val TURN_SPEED = 0.135f
        private const val BULLET_SPEED = 13.5f
        const val NORMAL_COOLDOWN = 22f
        const val RAPID_COOLDOWN = 10f
        const val DASH_COOLDOWN = 110f
        private const val DASH_DURATION = 11f
        private const val BUFF_DURATION_FRAMES = 360f
        private const val POWERUP_SPAWN_INTERVAL = 450f
        private const val ROUND_RESET_DELAY_FRAMES = 78f
        private const val MAX_BULLETS_PER_PLAYER = 5
    }

    var walls: List<WallRect> = buildWalls(ArenaMap.NEON_GRID)
        private set

    // P1 is at the Bottom facing UP (-PI/2); P2 is at the Top facing DOWN (+PI/2)
    val p1 = TankEntity(
        id = PlayerId.P1,
        x = ARENA_WIDTH * 0.50f,
        y = ARENA_HEIGHT * 0.87f,
        angleRad = (-PI / 2).toFloat()
    )
    val p2 = TankEntity(
        id = PlayerId.P2,
        x = ARENA_WIDTH * 0.50f,
        y = ARENA_HEIGHT * 0.13f,
        angleRad = (PI / 2).toFloat()
    )

    val bullets = mutableListOf<BulletEntity>()
    val particles = mutableListOf<ParticleEntity>()
    val shockwaves = mutableListOf<ShockwaveEntity>()
    val banners = mutableListOf<FloatingBanner>()
    var activePowerUp: PowerUpEntity? = null
        private set

    var screenShake = 0f
        private set

    private var powerUpTimer = 210f
    private var roundOverTimer = 0f
    private val rng = Random(System.currentTimeMillis())

    var scoreP1 = 0
        private set
    var scoreP2 = 0
        private set
    var phase = MatchPhase.MENU
        private set
    var targetScore = 5
    var selectedMap = ArenaMap.NEON_GRID
    var powerUpsEnabled = true
    var roundWinner: PlayerId? = null
        private set
    var matchWinner: PlayerId? = null
        private set

    fun startNewMatch(
        map: ArenaMap = selectedMap,
        target: Int = targetScore,
        enablePowerUps: Boolean = powerUpsEnabled
    ) {
        selectedMap = map
        targetScore = target
        powerUpsEnabled = enablePowerUps
        walls = buildWalls(map)
        scoreP1 = 0
        scoreP2 = 0
        roundWinner = null
        matchWinner = null
        resetRoundEntities()
        phase = MatchPhase.PLAYING
    }

    fun pauseMatch() {
        if (phase == MatchPhase.PLAYING) {
            phase = MatchPhase.PAUSED
        }
    }

    fun resumeMatch() {
        if (phase == MatchPhase.PAUSED) {
            phase = MatchPhase.PLAYING
        }
    }

    fun openMenu() {
        phase = MatchPhase.MENU
    }

    fun selectMapPreview(map: ArenaMap) {
        selectedMap = map
        walls = buildWalls(map)
        resetRoundEntities()
    }

    private fun resetRoundEntities() {
        bullets.clear()
        particles.clear()
        shockwaves.clear()
        banners.clear()
        activePowerUp = null
        powerUpTimer = 180f
        roundWinner = null
        screenShake = 0f

        resetTank(p1, ARENA_WIDTH * 0.50f, ARENA_HEIGHT * 0.87f, (-PI / 2).toFloat())
        resetTank(p2, ARENA_WIDTH * 0.50f, ARENA_HEIGHT * 0.13f, (PI / 2).toFloat())
    }

    private fun resetTank(tank: TankEntity, x: Float, y: Float, angle: Float) {
        tank.x = x
        tank.y = y
        tank.angleRad = angle
        tank.speed = 0f
        tank.shootCooldownFrames = 0f
        tank.dashCooldownFrames = 0f
        tank.dashActiveFrames = 0f
        tank.recoilOffset = 0f
        tank.muzzleFlashFrames = 0f
        tank.hasShield = false
        tank.rapidFireFrames = 0f
        tank.speedBoostFrames = 0f
        tank.alive = true
    }

    /**
     * Advances simulation by [dtSeconds]. Normalized to 60fps frames (dtScale = dtSeconds * 60f).
     */
    fun step(dtSeconds: Float, p1Input: PlayerInputState, p2Input: PlayerInputState) {
        val dtScale = (dtSeconds * 60f).coerceIn(0.1f, 2.5f)

        if (phase == MatchPhase.PAUSED) return

        screenShake = max(0f, screenShake - 0.9f * dtScale)
        updateParticles(dtScale)
        updateShockwaves(dtScale)
        updateBanners(dtScale)

        if (phase == MatchPhase.MENU || phase == MatchPhase.MATCH_OVER) {
            return
        }

        if (phase == MatchPhase.ROUND_OVER) {
            roundOverTimer -= dtScale
            if (roundOverTimer <= 0f) {
                resetRoundEntities()
                phase = MatchPhase.PLAYING
            }
            return
        }

        updateTank(p1, p1Input, dtScale)
        updateTank(p2, p2Input, dtScale)
        resolveTankVsTankCollision()

        if (powerUpsEnabled) {
            updatePowerUp(dtScale)
        }

        updateBullets(dtScale)
    }

    private fun updateTank(tank: TankEntity, input: PlayerInputState, dtScale: Float) {
        if (!tank.alive) return

        if (tank.rapidFireFrames > 0f) tank.rapidFireFrames = max(0f, tank.rapidFireFrames - dtScale)
        if (tank.speedBoostFrames > 0f) tank.speedBoostFrames = max(0f, tank.speedBoostFrames - dtScale)
        if (tank.shootCooldownFrames > 0f) tank.shootCooldownFrames = max(0f, tank.shootCooldownFrames - dtScale)
        if (tank.dashCooldownFrames > 0f) tank.dashCooldownFrames = max(0f, tank.dashCooldownFrames - dtScale)
        if (tank.dashActiveFrames > 0f) tank.dashActiveFrames = max(0f, tank.dashActiveFrames - dtScale)
        if (tank.muzzleFlashFrames > 0f) tank.muzzleFlashFrames = max(0f, tank.muzzleFlashFrames - dtScale)
        if (tank.recoilOffset > 0f) tank.recoilOffset = max(0f, tank.recoilOffset - 0.8f * dtScale)

        val mag = hypot(input.moveX, input.moveY).coerceIn(0f, 1f)
        val boostMul = if (tank.speedBoostFrames > 0f) 1.35f else 1f
        val turnRate = TURN_SPEED * (if (tank.speedBoostFrames > 0f) 1.2f else 1f) * dtScale

        if (mag > 0.12f) {
            val targetAngle = atan2(input.moveY, input.moveX)
            val diff = atan2(sin(targetAngle - tank.angleRad), cos(targetAngle - tank.angleRad))
            tank.angleRad += diff.coerceIn(-turnRate, turnRate)
        }

        // Dash trigger
        if (input.dash && tank.dashCooldownFrames <= 0f) {
            tank.dashActiveFrames = DASH_DURATION
            tank.dashCooldownFrames = DASH_COOLDOWN
            if (mag > 0.12f) {
                tank.angleRad = atan2(input.moveY, input.moveX)
            }
            shockwaves.add(
                ShockwaveEntity(
                    x = tank.x,
                    y = tank.y,
                    radius = tank.radius * 0.6f,
                    maxRadius = tank.radius * 2.3f,
                    alpha = 0.75f,
                    colorType = if (tank.id == PlayerId.P1) 0 else 1
                )
            )
            onDash()
        }

        val targetSpeed = when {
            tank.dashActiveFrames > 0f -> BASE_SPEED * 2.4f * boostMul
            mag > 0.12f -> BASE_SPEED * mag * boostMul
            else -> 0f
        }

        tank.speed = if (targetSpeed > 0f) {
            tank.speed + (targetSpeed - tank.speed) * (0.4f * dtScale).coerceAtMost(1f)
        } else {
            tank.speed * (1f - (0.24f * dtScale).coerceAtMost(0.95f))
        }

        if (abs(tank.speed) > 0.1f) {
            tank.treadPhase += tank.speed * dtScale * 0.35f
        }

        if (tank.dashActiveFrames > 0f) {
            spawnDashParticles(tank)
        }

        tank.x += cos(tank.angleRad) * tank.speed * dtScale
        tank.y += sin(tank.angleRad) * tank.speed * dtScale

        // Clamp to arena margins
        val minX = ARENA_MARGIN + tank.radius
        val maxX = ARENA_WIDTH - ARENA_MARGIN - tank.radius
        val minY = ARENA_MARGIN + tank.radius
        val maxY = ARENA_HEIGHT - ARENA_MARGIN - tank.radius
        tank.x = tank.x.coerceIn(minX, maxX)
        tank.y = tank.y.coerceIn(minY, maxY)

        // Slide cleanly against walls
        for (wall in walls) {
            resolveCircleRect(tank, wall)
        }

        // Fire cannon
        val activeOwned = bullets.count { it.owner == tank.id }
        if (input.fire && tank.shootCooldownFrames <= 0f && activeOwned < MAX_BULLETS_PER_PLAYER) {
            val muzzleX = tank.x + cos(tank.angleRad) * (tank.radius + 14f)
            val muzzleY = tank.y + sin(tank.angleRad) * (tank.radius + 14f)
            bullets.add(
                BulletEntity(
                    x = muzzleX,
                    y = muzzleY,
                    vx = cos(tank.angleRad) * BULLET_SPEED,
                    vy = sin(tank.angleRad) * BULLET_SPEED,
                    owner = tank.id
                )
            )
            tank.shootCooldownFrames = if (tank.rapidFireFrames > 0f) RAPID_COOLDOWN else NORMAL_COOLDOWN
            tank.recoilOffset = 7f
            tank.muzzleFlashFrames = 5f
            spawnMuzzleSparks(muzzleX, muzzleY, tank.angleRad, if (tank.id == PlayerId.P1) 0 else 1)
            onShoot()
        }
    }

    private fun spawnDashParticles(tank: TankEntity) {
        val colorType = if (tank.id == PlayerId.P1) 0 else 1
        val backAngle = tank.angleRad + PI.toFloat()
        repeat(2) {
            val spread = backAngle + (rng.nextFloat() - 0.5f) * 0.6f
            val speed = 2.5f + rng.nextFloat() * 3.5f
            particles.add(
                ParticleEntity(
                    x = tank.x + cos(backAngle) * tank.radius * 0.7f,
                    y = tank.y + sin(backAngle) * tank.radius * 0.7f,
                    vx = cos(spread) * speed,
                    vy = sin(spread) * speed,
                    radius = 3.5f + rng.nextFloat() * 2.5f,
                    life = 0.85f,
                    decay = 0.08f,
                    colorType = colorType
                )
            )
        }
    }

    private fun resolveCircleRect(tank: TankEntity, rect: WallRect) {
        val closestX = tank.x.coerceIn(rect.left, rect.right)
        val closestY = tank.y.coerceIn(rect.top, rect.bottom)
        val dx = tank.x - closestX
        val dy = tank.y - closestY
        val distSq = dx * dx + dy * dy
        val r = tank.radius

        if (distSq < r * r) {
            val dist = hypot(dx, dy)
            if (dist > 0.0001f) {
                val overlap = r - dist
                tank.x += (dx / dist) * overlap
                tank.y += (dy / dist) * overlap
            } else {
                val leftPen = abs(tank.x - rect.left)
                val rightPen = abs(rect.right - tank.x)
                val topPen = abs(tank.y - rect.top)
                val bottomPen = abs(rect.bottom - tank.y)
                val minPen = min(min(leftPen, rightPen), min(topPen, bottomPen))
                when (minPen) {
                    leftPen -> tank.x = rect.left - r
                    rightPen -> tank.x = rect.right + r
                    topPen -> tank.y = rect.top - r
                    else -> tank.y = rect.bottom + r
                }
            }
        }
    }

    private fun resolveTankVsTankCollision() {
        if (!p1.alive || !p2.alive) return
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val minDist = p1.radius + p2.radius
        val dist = hypot(dx, dy)
        if (dist < minDist && dist > 0.001f) {
            val overlap = (minDist - dist) * 0.5f
            val nx = dx / dist
            val ny = dy / dist
            p1.x -= nx * overlap
            p1.y -= ny * overlap
            p2.x += nx * overlap
            p2.y += ny * overlap
        }
    }

    private fun updatePowerUp(dtScale: Float) {
        val current = activePowerUp
        if (current == null) {
            powerUpTimer -= dtScale
            if (powerUpTimer <= 0f) {
                spawnPowerUp()
                powerUpTimer = POWERUP_SPAWN_INTERVAL
            }
            return
        }

        current.pulsePhase += 0.09f * dtScale

        val collector = when {
            p1.alive && hypot(p1.x - current.x, p1.y - current.y) < p1.radius + current.radius -> p1
            p2.alive && hypot(p2.x - current.x, p2.y - current.y) < p2.radius + current.radius -> p2
            else -> null
        }
        if (collector != null) {
            when (current.type) {
                PowerUpType.SHIELD -> collector.hasShield = true
                PowerUpType.RAPID_FIRE -> collector.rapidFireFrames = BUFF_DURATION_FRAMES
                PowerUpType.SPEED_BOOST -> collector.speedBoostFrames = BUFF_DURATION_FRAMES
            }
            banners.add(FloatingBanner(current.x, current.y - 16f, current.type.label, 1.0f, 3))
            shockwaves.add(ShockwaveEntity(current.x, current.y, 12f, 85f, 0.9f, 3))
            activePowerUp = null
            onPowerUp()
        }
    }

    private fun spawnPowerUp() {
        val candidates = listOf(
            ARENA_WIDTH * 0.50f to ARENA_HEIGHT * 0.50f,
            ARENA_WIDTH * 0.22f to ARENA_HEIGHT * 0.50f,
            ARENA_WIDTH * 0.78f to ARENA_HEIGHT * 0.50f,
            ARENA_WIDTH * 0.24f to ARENA_HEIGHT * 0.36f,
            ARENA_WIDTH * 0.76f to ARENA_HEIGHT * 0.64f
        ).filter { (cx, cy) ->
            walls.none { w ->
                cx + 28f > w.left && cx - 28f < w.right && cy + 28f > w.top && cy - 28f < w.bottom
            }
        }
        if (candidates.isEmpty()) return
        val (sx, sy) = candidates[rng.nextInt(candidates.size)]
        val types = PowerUpType.entries
        activePowerUp = PowerUpEntity(sx, sy, types[rng.nextInt(types.size)])
        shockwaves.add(ShockwaveEntity(sx, sy, 6f, 65f, 0.8f, 3))
    }

    private fun updateBullets(dtScale: Float) {
        val toRemove = mutableSetOf<Int>()

        for (i in bullets.indices) {
            if (i in toRemove) continue
            val b = bullets[i]
            b.lifeFrames -= dtScale
            if (b.lifeFrames <= 0f) {
                toRemove.add(i)
                continue
            }

            b.trail.add(0, b.x to b.y)
            if (b.trail.size > 7) b.trail.removeAt(b.trail.lastIndex)

            val subSteps = 2
            val stepScale = dtScale / subSteps
            var destroyed = false

            for (s in 0 until subSteps) {
                b.x += b.vx * stepScale
                b.y += b.vy * stepScale

                var bounced = false
                if (b.x - b.radius < ARENA_MARGIN) {
                    b.x = ARENA_MARGIN + b.radius
                    b.vx = abs(b.vx)
                    b.bouncesRemaining--
                    bounced = true
                } else if (b.x + b.radius > ARENA_WIDTH - ARENA_MARGIN) {
                    b.x = ARENA_WIDTH - ARENA_MARGIN - b.radius
                    b.vx = -abs(b.vx)
                    b.bouncesRemaining--
                    bounced = true
                }

                if (b.y - b.radius < ARENA_MARGIN) {
                    b.y = ARENA_MARGIN + b.radius
                    b.vy = abs(b.vy)
                    b.bouncesRemaining--
                    bounced = true
                } else if (b.y + b.radius > ARENA_HEIGHT - ARENA_MARGIN) {
                    b.y = ARENA_HEIGHT - ARENA_MARGIN - b.radius
                    b.vy = -abs(b.vy)
                    b.bouncesRemaining--
                    bounced = true
                }

                for (wall in walls) {
                    val closestX = b.x.coerceIn(wall.left, wall.right)
                    val closestY = b.y.coerceIn(wall.top, wall.bottom)
                    val dx = b.x - closestX
                    val dy = b.y - closestY
                    if (dx * dx + dy * dy <= b.radius * b.radius) {
                        val overlapLeft = abs((b.x + b.radius) - wall.left)
                        val overlapRight = abs(wall.right - (b.x - b.radius))
                        val overlapTop = abs((b.y + b.radius) - wall.top)
                        val overlapBottom = abs(wall.bottom - (b.y - b.radius))
                        val minXPen = min(overlapLeft, overlapRight)
                        val minYPen = min(overlapTop, overlapBottom)

                        if (minXPen < minYPen) {
                            b.vx = -b.vx
                            b.x = if (overlapLeft < overlapRight) wall.left - b.radius - 0.5f else wall.right + b.radius + 0.5f
                        } else {
                            b.vy = -b.vy
                            b.y = if (overlapTop < overlapBottom) wall.top - b.radius - 0.5f else wall.bottom + b.radius + 0.5f
                        }
                        b.bouncesRemaining--
                        bounced = true
                        break
                    }
                }

                if (bounced) {
                    spawnRicochetSparks(b.x, b.y)
                    if (b.bouncesRemaining < 0) {
                        destroyed = true
                        break
                    } else {
                        onBounce()
                    }
                }

                val target = if (b.owner == PlayerId.P1) p2 else p1
                if (target.alive && hypot(b.x - target.x, b.y - target.y) < target.radius + b.radius) {
                    destroyed = true
                    if (target.hasShield) {
                        target.hasShield = false
                        shockwaves.add(ShockwaveEntity(target.x, target.y, target.radius, 90f, 1.0f, 3))
                        banners.add(FloatingBanner(target.x, target.y - 34f, "SHIELDED!", 1.0f, 3))
                        spawnExplosion(b.x, b.y, 2, count = 16)
                        screenShake = 8f
                        onShieldBlock()
                    } else {
                        handleTankHit(target, scorer = b.owner)
                    }
                    break
                }
            }

            if (destroyed) {
                toRemove.add(i)
                if (phase != MatchPhase.PLAYING) break
            }
        }

        // Bullet-vs-bullet mid-air clash
        if (phase == MatchPhase.PLAYING) {
            for (i in bullets.indices) {
                if (i in toRemove) continue
                for (j in i + 1 until bullets.size) {
                    if (j in toRemove) continue
                    val b1 = bullets[i]
                    val b2 = bullets[j]
                    if (b1.owner != b2.owner && hypot(b1.x - b2.x, b1.y - b2.y) < b1.radius + b2.radius + 3f) {
                        toRemove.add(i)
                        toRemove.add(j)
                        val mx = (b1.x + b2.x) * 0.5f
                        val my = (b1.y + b2.y) * 0.5f
                        spawnExplosion(mx, my, 2, count = 14)
                        onBounce()
                    }
                }
            }
        }

        if (toRemove.isNotEmpty()) {
            val descending = toRemove.sortedDescending()
            for (idx in descending) {
                if (idx in bullets.indices) {
                    bullets.removeAt(idx)
                }
            }
        }
    }

    private fun handleTankHit(victim: TankEntity, scorer: PlayerId) {
        victim.alive = false
        val colorType = if (victim.id == PlayerId.P1) 0 else 1
        spawnExplosion(victim.x, victim.y, colorType, count = 42)
        shockwaves.add(ShockwaveEntity(victim.x, victim.y, 16f, 150f, 1.0f, colorType))
        banners.add(FloatingBanner(victim.x, victim.y - 30f, "+1", 1.2f, if (scorer == PlayerId.P1) 0 else 1))
        screenShake = 18f

        if (scorer == PlayerId.P1) scoreP1++ else scoreP2++
        roundWinner = scorer
        bullets.clear()

        val reachedTarget = targetScore > 0 && (scoreP1 >= targetScore || scoreP2 >= targetScore)
        if (reachedTarget) {
            matchWinner = scorer
            phase = MatchPhase.MATCH_OVER
            onWin()
        } else {
            roundOverTimer = ROUND_RESET_DELAY_FRAMES
            phase = MatchPhase.ROUND_OVER
            onExplosion()
        }
    }

    private fun spawnMuzzleSparks(x: Float, y: Float, angle: Float, colorType: Int) {
        for (i in 0 until 7) {
            val spread = angle + (rng.nextFloat() - 0.5f) * 0.7f
            val speed = 3f + rng.nextFloat() * 4.5f
            particles.add(
                ParticleEntity(
                    x = x,
                    y = y,
                    vx = cos(spread) * speed,
                    vy = sin(spread) * speed,
                    radius = 2.5f + rng.nextFloat() * 2f,
                    life = 1f,
                    decay = 0.08f,
                    colorType = colorType
                )
            )
        }
    }

    private fun spawnRicochetSparks(x: Float, y: Float) {
        for (i in 0 until 8) {
            val angle = rng.nextFloat() * (2f * PI.toFloat())
            val speed = 2f + rng.nextFloat() * 4f
            particles.add(
                ParticleEntity(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    radius = 2.5f + rng.nextFloat() * 2f,
                    life = 1f,
                    decay = 0.07f,
                    colorType = 2
                )
            )
        }
    }

    private fun spawnExplosion(x: Float, y: Float, colorType: Int, count: Int) {
        for (i in 0 until count) {
            val angle = rng.nextFloat() * (2f * PI.toFloat())
            val speed = 2.5f + rng.nextFloat() * 9.5f
            particles.add(
                ParticleEntity(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    radius = 3f + rng.nextFloat() * 4.5f,
                    life = 1f,
                    decay = 0.022f + rng.nextFloat() * 0.025f,
                    colorType = if (i % 3 == 0) 2 else colorType
                )
            )
        }
    }

    private fun updateParticles(dtScale: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * dtScale
            p.y += p.vy * dtScale
            p.vx *= (1f - 0.04f * dtScale).coerceAtLeast(0.5f)
            p.vy *= (1f - 0.04f * dtScale).coerceAtLeast(0.5f)
            p.life -= p.decay * dtScale
            if (p.life <= 0f) it.remove()
        }
    }

    private fun updateShockwaves(dtScale: Float) {
        val it = shockwaves.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.radius += (s.maxRadius - s.radius) * (0.14f * dtScale).coerceAtMost(0.8f)
            s.alpha -= 0.035f * dtScale
            if (s.alpha <= 0f) it.remove()
        }
    }

    private fun updateBanners(dtScale: Float) {
        val it = banners.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.life -= 0.02f * dtScale
            if (b.life <= 0f) it.remove()
        }
    }

    private fun buildWalls(map: ArenaMap): List<WallRect> {
        return when (map) {
            ArenaMap.NEON_GRID -> listOf(
                // Horizontal center dividers leaving left/middle/right lanes
                WallRect(ARENA_WIDTH * 0.16f, ARENA_HEIGHT * 0.50f - 20f, ARENA_WIDTH * 0.39f, ARENA_HEIGHT * 0.50f + 20f),
                WallRect(ARENA_WIDTH * 0.61f, ARENA_HEIGHT * 0.50f - 20f, ARENA_WIDTH * 0.84f, ARENA_HEIGHT * 0.50f + 20f),
                // Top & bottom flanking cover blocks
                WallRect(ARENA_WIDTH * 0.50f - 54f, ARENA_HEIGHT * 0.31f - 28f, ARENA_WIDTH * 0.50f + 54f, ARENA_HEIGHT * 0.31f + 28f),
                WallRect(ARENA_WIDTH * 0.50f - 54f, ARENA_HEIGHT * 0.69f - 28f, ARENA_WIDTH * 0.50f + 54f, ARENA_HEIGHT * 0.69f + 28f)
            )
            ArenaMap.TWIN_BUNKERS -> listOf(
                // Top-left & bottom-right L-bunkers
                WallRect(ARENA_WIDTH * 0.22f, ARENA_HEIGHT * 0.31f, ARENA_WIDTH * 0.22f + 130f, ARENA_HEIGHT * 0.31f + 32f),
                WallRect(ARENA_WIDTH * 0.22f, ARENA_HEIGHT * 0.31f, ARENA_WIDTH * 0.22f + 32f, ARENA_HEIGHT * 0.31f + 125f),

                WallRect(ARENA_WIDTH * 0.78f - 130f, ARENA_HEIGHT * 0.69f - 32f, ARENA_WIDTH * 0.78f, ARENA_HEIGHT * 0.69f),
                WallRect(ARENA_WIDTH * 0.78f - 32f, ARENA_HEIGHT * 0.69f - 125f, ARENA_WIDTH * 0.78f, ARENA_HEIGHT * 0.69f),

                // Opposite corner covers
                WallRect(ARENA_WIDTH * 0.78f - 130f, ARENA_HEIGHT * 0.31f, ARENA_WIDTH * 0.78f, ARENA_HEIGHT * 0.31f + 32f),
                WallRect(ARENA_WIDTH * 0.22f, ARENA_HEIGHT * 0.69f - 32f, ARENA_WIDTH * 0.22f + 130f, ARENA_HEIGHT * 0.69f),

                // Center compact pillar
                WallRect(ARENA_WIDTH * 0.50f - 32f, ARENA_HEIGHT * 0.50f - 32f, ARENA_WIDTH * 0.50f + 32f, ARENA_HEIGHT * 0.50f + 32f)
            )
            ArenaMap.REACTOR_CORE -> listOf(
                // Central split core
                WallRect(ARENA_WIDTH * 0.50f - 26f, ARENA_HEIGHT * 0.50f - 95f, ARENA_WIDTH * 0.50f + 26f, ARENA_HEIGHT * 0.50f - 38f),
                WallRect(ARENA_WIDTH * 0.50f - 26f, ARENA_HEIGHT * 0.50f + 38f, ARENA_WIDTH * 0.50f + 26f, ARENA_HEIGHT * 0.50f + 95f),
                // 4 symmetrical quadrant pillars
                WallRect(ARENA_WIDTH * 0.26f - 28f, ARENA_HEIGHT * 0.28f - 28f, ARENA_WIDTH * 0.26f + 28f, ARENA_HEIGHT * 0.28f + 28f),
                WallRect(ARENA_WIDTH * 0.74f - 28f, ARENA_HEIGHT * 0.28f - 28f, ARENA_WIDTH * 0.74f + 28f, ARENA_HEIGHT * 0.28f + 28f),
                WallRect(ARENA_WIDTH * 0.26f - 28f, ARENA_HEIGHT * 0.72f - 28f, ARENA_WIDTH * 0.26f + 28f, ARENA_HEIGHT * 0.72f + 28f),
                WallRect(ARENA_WIDTH * 0.74f - 28f, ARENA_HEIGHT * 0.72f - 28f, ARENA_WIDTH * 0.74f + 28f, ARENA_HEIGHT * 0.72f + 28f)
            )
        }
    }
}
