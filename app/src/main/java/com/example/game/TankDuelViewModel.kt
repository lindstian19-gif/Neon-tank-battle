package com.example.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import com.example.audio.SoundEffects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.hypot

enum class KeyboardKey {
    P1_UP, P1_DOWN, P1_LEFT, P1_RIGHT, P1_DASH, P1_FIRE,
    P2_UP, P2_DOWN, P2_LEFT, P2_RIGHT, P2_DASH, P2_FIRE
}

data class TouchControlsSnapshot(
    val p1Stick: Offset = Offset.Zero,
    val p2Stick: Offset = Offset.Zero,
    val activeButtons: Set<TouchZone> = emptySet()
)

class TankDuelViewModel : ViewModel() {

    private val soundEffects = SoundEffects()

    val engine = GameEngine(
        onShoot = { soundEffects.playShoot() },
        onBounce = { soundEffects.playBounce() },
        onPowerUp = { soundEffects.playPowerUp() },
        onShieldBlock = { soundEffects.playShieldBlock() },
        onExplosion = { soundEffects.playExplosion() },
        onWin = { soundEffects.playWin() },
        onDash = { soundEffects.playDash() }
    )

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _touchSnapshot = MutableStateFlow(TouchControlsSnapshot())
    val touchSnapshot: StateFlow<TouchControlsSnapshot> = _touchSnapshot.asStateFlow()

    private val pressedKeys = mutableSetOf<KeyboardKey>()

    // Read inside Canvas draw lambdas for zero-recomposition 60-120fps rendering.
    var frameTick by mutableLongStateOf(0L)
        private set

    fun updateTouchControls(snapshot: TouchControlsSnapshot) {
        if (_touchSnapshot.value != snapshot) {
            _touchSnapshot.value = snapshot
        }
    }

    fun clearTouchControls() {
        if (_touchSnapshot.value != TouchControlsSnapshot()) {
            _touchSnapshot.value = TouchControlsSnapshot()
        }
    }

    fun setKeyboardKey(key: KeyboardKey, isDown: Boolean) {
        if (isDown) pressedKeys.add(key) else pressedKeys.remove(key)
    }

    fun stepSimulation(dtSeconds: Float) {
        val touch = _touchSnapshot.value

        // Combine analog touch joystick with optional hardware keyboard fallback
        var p1x = touch.p1Stick.x
        var p1y = touch.p1Stick.y
        if (KeyboardKey.P1_LEFT in pressedKeys) p1x -= 1f
        if (KeyboardKey.P1_RIGHT in pressedKeys) p1x += 1f
        if (KeyboardKey.P1_UP in pressedKeys) p1y -= 1f
        if (KeyboardKey.P1_DOWN in pressedKeys) p1y += 1f
        val p1Mag = hypot(p1x, p1y)
        if (p1Mag > 1f) {
            p1x /= p1Mag
            p1y /= p1Mag
        }

        var p2x = touch.p2Stick.x
        var p2y = touch.p2Stick.y
        if (KeyboardKey.P2_LEFT in pressedKeys) p2x -= 1f
        if (KeyboardKey.P2_RIGHT in pressedKeys) p2x += 1f
        if (KeyboardKey.P2_UP in pressedKeys) p2y -= 1f
        if (KeyboardKey.P2_DOWN in pressedKeys) p2y += 1f
        val p2Mag = hypot(p2x, p2y)
        if (p2Mag > 1f) {
            p2x /= p2Mag
            p2y /= p2Mag
        }

        val p1Input = PlayerInputState(
            moveX = p1x,
            moveY = p1y,
            dash = (TouchZone.P1_DASH in touch.activeButtons) || (KeyboardKey.P1_DASH in pressedKeys),
            fire = (TouchZone.P1_FIRE in touch.activeButtons) || (KeyboardKey.P1_FIRE in pressedKeys)
        )
        val p2Input = PlayerInputState(
            moveX = p2x,
            moveY = p2y,
            dash = (TouchZone.P2_DASH in touch.activeButtons) || (KeyboardKey.P2_DASH in pressedKeys),
            fire = (TouchZone.P2_FIRE in touch.activeButtons) || (KeyboardKey.P2_FIRE in pressedKeys)
        )

        engine.step(dtSeconds, p1Input, p2Input)
        syncUiStateIfNeeded()
        frameTick++
    }

    fun startNewMatch() {
        val s = _uiState.value
        clearTouchControls()
        engine.startNewMatch(
            map = s.selectedMap,
            target = s.targetScore,
            enablePowerUps = s.powerUpsEnabled
        )
        syncUiStateIfNeeded()
        frameTick++
    }

    fun selectMap(map: ArenaMap) {
        _uiState.update { it.copy(selectedMap = map) }
        engine.selectMapPreview(map)
        frameTick++
    }

    fun selectTargetScore(target: Int) {
        _uiState.update { it.copy(targetScore = target) }
        engine.targetScore = target
    }

    fun togglePowerUps(enabled: Boolean) {
        _uiState.update { it.copy(powerUpsEnabled = enabled) }
        engine.powerUpsEnabled = enabled
    }

    fun toggleFlipP2(enabled: Boolean) {
        _uiState.update { it.copy(flipP2Controls = enabled) }
    }

    fun toggleSound() {
        val next = !_uiState.value.soundEnabled
        soundEffects.enabled = next
        _uiState.update { it.copy(soundEnabled = next) }
    }

    fun pauseGame() {
        clearTouchControls()
        engine.pauseMatch()
        syncUiStateIfNeeded()
    }

    fun resumeGame() {
        engine.resumeMatch()
        syncUiStateIfNeeded()
    }

    fun returnToMenu() {
        clearTouchControls()
        engine.openMenu()
        syncUiStateIfNeeded()
    }

    private fun syncUiStateIfNeeded() {
        val current = _uiState.value
        val nextPhase = engine.phase
        val nextScore1 = engine.scoreP1
        val nextScore2 = engine.scoreP2
        val nextRoundWinner = engine.roundWinner
        val nextMatchWinner = engine.matchWinner
        val nextP1Shield = engine.p1.hasShield
        val nextP1Rapid = engine.p1.rapidFireFrames > 0f
        val nextP1Turbo = engine.p1.speedBoostFrames > 0f
        val nextP2Shield = engine.p2.hasShield
        val nextP2Rapid = engine.p2.rapidFireFrames > 0f
        val nextP2Turbo = engine.p2.speedBoostFrames > 0f

        if (current.phase != nextPhase ||
            current.scoreP1 != nextScore1 ||
            current.scoreP2 != nextScore2 ||
            current.roundWinner != nextRoundWinner ||
            current.matchWinner != nextMatchWinner ||
            current.p1Shield != nextP1Shield ||
            current.p1Rapid != nextP1Rapid ||
            current.p1Turbo != nextP1Turbo ||
            current.p2Shield != nextP2Shield ||
            current.p2Rapid != nextP2Rapid ||
            current.p2Turbo != nextP2Turbo
        ) {
            _uiState.value = current.copy(
                phase = nextPhase,
                scoreP1 = nextScore1,
                scoreP2 = nextScore2,
                roundWinner = nextRoundWinner,
                matchWinner = nextMatchWinner,
                p1Shield = nextP1Shield,
                p1Rapid = nextP1Rapid,
                p1Turbo = nextP1Turbo,
                p2Shield = nextP2Shield,
                p2Rapid = nextP2Rapid,
                p2Turbo = nextP2Turbo
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundEffects.release()
    }
}
