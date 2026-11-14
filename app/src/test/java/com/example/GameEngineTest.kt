package com.example

import com.example.game.ArenaMap
import com.example.game.GameEngine
import com.example.game.MatchPhase
import com.example.game.PlayerId
import com.example.game.PlayerInputState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    @Test
    fun dualSimultaneousInput_movesAndFiresBothTanks() {
        var shoots = 0
        val engine = GameEngine(onShoot = { shoots++ })
        engine.startNewMatch(ArenaMap.NEON_GRID, target = 5, enablePowerUps = false)

        val p1StartY = engine.p1.y
        val p2StartY = engine.p2.y

        // P1 at bottom moves UP (-Y), P2 at top moves DOWN (+Y), both fire simultaneously
        val p1Input = PlayerInputState(moveY = -1f, fire = true)
        val p2Input = PlayerInputState(moveY = 1f, fire = true)

        repeat(5) {
            engine.step(1f / 60f, p1Input, p2Input)
        }

        assertTrue("P1 should advance upward", engine.p1.y < p1StartY)
        assertTrue("P2 should advance downward", engine.p2.y > p2StartY)
        assertEquals(2, shoots)
        assertEquals(2, engine.bullets.size)
    }

    @Test
    fun dashThrusterActivatesBurstAndCooldown() {
        var dashed = false
        val engine = GameEngine(onDash = { dashed = true })
        engine.startNewMatch(ArenaMap.NEON_GRID, target = 5, enablePowerUps = false)

        val p1StartY = engine.p1.y
        engine.step(1f / 60f, PlayerInputState(moveY = -1f, dash = true), PlayerInputState())

        assertTrue(dashed)
        assertTrue(engine.p1.dashActiveFrames > 0f)
        assertTrue(engine.p1.dashCooldownFrames > 0f)
        assertTrue(engine.p1.y < p1StartY)
    }

    @Test
    fun bulletRicochetsOffArenaWall() {
        var bounces = 0
        val engine = GameEngine(onBounce = { bounces++ })
        engine.startNewMatch(ArenaMap.NEON_GRID, target = 5, enablePowerUps = false)

        // Aim P1 straight left toward left arena boundary
        engine.p1.angleRad = Math.PI.toFloat()
        engine.step(1f / 60f, PlayerInputState(fire = true), PlayerInputState())

        assertEquals(1, engine.bullets.size)
        assertTrue(engine.bullets.first().vx < 0f)

        repeat(60) {
            engine.step(1f / 60f, PlayerInputState(), PlayerInputState())
        }

        assertTrue("Bullet should have bounced at least once", bounces >= 1)
        assertTrue("Bullet should reverse vx after hitting left wall", engine.bullets.first().vx > 0f)
    }

    @Test
    fun shieldAbsorbsHitWithoutScoring() {
        var shieldBlocked = false
        val engine = GameEngine(onShieldBlock = { shieldBlocked = true })
        engine.startNewMatch(ArenaMap.NEON_GRID, target = 3, enablePowerUps = false)

        // Clear vertical lane on left side
        engine.p1.x = 120f
        engine.p1.y = 700f
        engine.p1.angleRad = (-Math.PI / 2).toFloat()

        engine.p2.x = 120f
        engine.p2.y = 450f
        engine.p2.hasShield = true

        engine.step(1f / 60f, PlayerInputState(fire = true), PlayerInputState())

        repeat(35) {
            engine.step(1f / 60f, PlayerInputState(), PlayerInputState())
        }

        assertTrue(shieldBlocked)
        assertFalse(engine.p2.hasShield)
        assertTrue(engine.p2.alive)
        assertEquals(0, engine.scoreP1)
        assertEquals(MatchPhase.PLAYING, engine.phase)
    }

    @Test
    fun reachingTargetScoreEndsMatch() {
        var won = false
        val engine = GameEngine(onWin = { won = true })
        engine.startNewMatch(ArenaMap.NEON_GRID, target = 1, enablePowerUps = false)

        engine.p1.x = 120f
        engine.p1.y = 700f
        engine.p1.angleRad = (-Math.PI / 2).toFloat()

        engine.p2.x = 120f
        engine.p2.y = 480f

        engine.step(1f / 60f, PlayerInputState(fire = true), PlayerInputState())

        repeat(30) {
            engine.step(1f / 60f, PlayerInputState(), PlayerInputState())
        }

        assertTrue(won)
        assertEquals(1, engine.scoreP1)
        assertEquals(PlayerId.P1, engine.matchWinner)
        assertEquals(MatchPhase.MATCH_OVER, engine.phase)
    }

    @Test
    fun allMapsHaveValidWallDimensions() {
        val engine = GameEngine()
        ArenaMap.entries.forEach { map ->
            engine.selectMapPreview(map)
            assertTrue(engine.walls.isNotEmpty())
            engine.walls.forEach { w ->
                assertTrue(w.right > w.left)
                assertTrue(w.bottom > w.top)
            }
        }
    }
}
