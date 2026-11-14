package com.example.game

import androidx.compose.runtime.Immutable

enum class PlayerId {
    P1,
    P2
}

enum class TouchZone {
    P1_JOYSTICK,
    P1_DASH,
    P1_FIRE,
    P2_JOYSTICK,
    P2_DASH,
    P2_FIRE
}

data class PlayerInputState(
    val moveX: Float = 0f,
    val moveY: Float = 0f,
    val dash: Boolean = false,
    val fire: Boolean = false
)

enum class ArenaMap(val title: String, val subtitle: String) {
    NEON_GRID("NEON GRID", "Symmetric center walls"),
    TWIN_BUNKERS("BUNKERS", "Angled ricochet lanes"),
    REACTOR_CORE("REACTOR", "Core shield & pillars")
}

enum class PowerUpType(val label: String) {
    SHIELD("SHIELD"),
    RAPID_FIRE("RAPID"),
    SPEED_BOOST("TURBO")
}

enum class MatchPhase {
    MENU,
    PLAYING,
    ROUND_OVER,
    MATCH_OVER,
    PAUSED
}

@Immutable
data class WallRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class TankEntity(
    val id: PlayerId,
    var x: Float,
    var y: Float,
    var angleRad: Float,
    var speed: Float = 0f,
    var radius: Float = 26f,
    var shootCooldownFrames: Float = 0f,
    var dashCooldownFrames: Float = 0f,
    var dashActiveFrames: Float = 0f,
    var recoilOffset: Float = 0f,
    var muzzleFlashFrames: Float = 0f,
    var treadPhase: Float = 0f,
    var hasShield: Boolean = false,
    var rapidFireFrames: Float = 0f,
    var speedBoostFrames: Float = 0f,
    var alive: Boolean = true
)

data class BulletEntity(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val owner: PlayerId,
    val radius: Float = 7f,
    var bouncesRemaining: Int = 2,
    var lifeFrames: Float = 260f,
    val trail: MutableList<Pair<Float, Float>> = mutableListOf()
)

data class ParticleEntity(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var life: Float,
    val decay: Float,
    val colorType: Int // 0 = P1 Cyan, 1 = P2 Magenta, 2 = Gold/Spark, 3 = Green/PowerUp
)

data class ShockwaveEntity(
    val x: Float,
    val y: Float,
    var radius: Float,
    val maxRadius: Float,
    var alpha: Float,
    val colorType: Int
)

data class FloatingBanner(
    val x: Float,
    val y: Float,
    val text: String,
    var life: Float,
    val colorType: Int
)

data class PowerUpEntity(
    val x: Float,
    val y: Float,
    val type: PowerUpType,
    val radius: Float = 20f,
    var pulsePhase: Float = 0f
)

@Immutable
data class GameUiState(
    val phase: MatchPhase = MatchPhase.MENU,
    val scoreP1: Int = 0,
    val scoreP2: Int = 0,
    val targetScore: Int = 5, // 0 = Endless
    val selectedMap: ArenaMap = ArenaMap.NEON_GRID,
    val powerUpsEnabled: Boolean = true,
    val flipP2Controls: Boolean = false,
    val soundEnabled: Boolean = true,
    val roundWinner: PlayerId? = null,
    val matchWinner: PlayerId? = null,
    val p1Shield: Boolean = false,
    val p1Rapid: Boolean = false,
    val p1Turbo: Boolean = false,
    val p2Shield: Boolean = false,
    val p2Rapid: Boolean = false,
    val p2Turbo: Boolean = false
)
