package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.game.ArenaMap
import com.example.game.GameUiState
import com.example.game.KeyboardKey
import com.example.game.MatchPhase
import com.example.game.PlayerId
import com.example.game.TankDuelViewModel
import com.example.game.TouchControlsSnapshot
import com.example.game.TouchZone
import com.example.ui.theme.ArenaAbyss
import com.example.ui.theme.ArenaSurface
import com.example.ui.theme.ArenaSurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGold
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun TankDuelScreen(
    viewModel: TankDuelViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val touchSnapshot by viewModel.touchSnapshot.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    BackHandler(enabled = uiState.phase == MatchPhase.PLAYING || uiState.phase == MatchPhase.ROUND_OVER) {
        viewModel.pauseGame()
    }

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == MatchPhase.PAUSED) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNanos = now
            viewModel.stepSimulation(dt)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val density = LocalDensity.current
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val zoneCoords = remember { mutableStateMapOf<TouchZone, LayoutCoordinates>() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ArenaAbyss)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val mapped = when (keyEvent.key) {
                    Key.W -> KeyboardKey.P1_UP
                    Key.S -> KeyboardKey.P1_DOWN
                    Key.A -> KeyboardKey.P1_LEFT
                    Key.D -> KeyboardKey.P1_RIGHT
                    Key.ShiftLeft -> KeyboardKey.P1_DASH
                    Key.Spacebar -> KeyboardKey.P1_FIRE
                    Key.DirectionUp -> KeyboardKey.P2_UP
                    Key.DirectionDown -> KeyboardKey.P2_DOWN
                    Key.DirectionLeft -> KeyboardKey.P2_LEFT
                    Key.DirectionRight -> KeyboardKey.P2_RIGHT
                    Key.ShiftRight -> KeyboardKey.P2_DASH
                    Key.Enter, Key.NumPadEnter -> KeyboardKey.P2_FIRE
                    else -> null
                } ?: return@onPreviewKeyEvent false

                when (keyEvent.type) {
                    KeyEventType.KeyDown -> {
                        viewModel.setKeyboardKey(mapped, true)
                        true
                    }
                    KeyEventType.KeyUp -> {
                        viewModel.setKeyboardKey(mapped, false)
                        true
                    }
                    else -> false
                }
            }
    ) {
        val isTablet = maxWidth >= 600.dp
        val joySize = if (isTablet) 132.dp else 108.dp
        val knobSize = if (isTablet) 54.dp else 44.dp
        val dashSize = if (isTablet) 78.dp else 66.dp
        val shootSize = if (isTablet) 98.dp else 84.dp
        val maxKnobTravelPx = with(density) { ((joySize - knobSize) * 0.5f).toPx() }

        val controlsEnabled = uiState.phase == MatchPhase.PLAYING || uiState.phase == MatchPhase.ROUND_OVER

        // Unified multi-touch controller container: Top Dock (P2) — Middle Arena — Bottom Dock (P1)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootCoords = it }
                .pointerInput(controlsEnabled, maxKnobTravelPx) {
                    if (!controlsEnabled) return@pointerInput
                    val pointerBindings = mutableMapOf<PointerId, TouchZone>()
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val root = rootCoords
                            if (root == null || !root.isAttached) continue

                            val rects = HashMap<TouchZone, Rect>(zoneCoords.size)
                            for ((zone, coords) in zoneCoords) {
                                if (coords.isAttached) {
                                    rects[zone] = root.localBoundingBoxOf(coords, clipBounds = false)
                                }
                            }

                            var p1Stick = Offset.Zero
                            var p2Stick = Offset.Zero
                            val activeButtons = HashSet<TouchZone>()

                            for (change in event.changes) {
                                if (!change.pressed) {
                                    pointerBindings.remove(change.id)
                                    continue
                                }

                                var boundZone = pointerBindings[change.id]
                                if (boundZone == null) {
                                    for ((zone, rect) in rects) {
                                        if (rect.inflate(10f).contains(change.position)) {
                                            boundZone = zone
                                            pointerBindings[change.id] = zone
                                            break
                                        }
                                    }
                                }

                                if (boundZone != null) {
                                    change.consume()
                                    val rect = rects[boundZone]
                                    when (boundZone) {
                                        TouchZone.P1_JOYSTICK -> {
                                            if (rect != null && maxKnobTravelPx > 1f) {
                                                p1Stick = computeNormalizedStick(
                                                    pointer = change.position,
                                                    center = rect.center,
                                                    maxRadiusPx = maxKnobTravelPx
                                                )
                                            }
                                        }
                                        TouchZone.P2_JOYSTICK -> {
                                            if (rect != null && maxKnobTravelPx > 1f) {
                                                p2Stick = computeNormalizedStick(
                                                    pointer = change.position,
                                                    center = rect.center,
                                                    maxRadiusPx = maxKnobTravelPx
                                                )
                                            }
                                        }
                                        TouchZone.P1_DASH,
                                        TouchZone.P1_FIRE,
                                        TouchZone.P2_DASH,
                                        TouchZone.P2_FIRE -> {
                                            if (rect != null && rect.inflate(28f).contains(change.position)) {
                                                activeButtons.add(boundZone)
                                            }
                                        }
                                    }
                                }
                            }

                            viewModel.updateTouchControls(
                                TouchControlsSnapshot(
                                    p1Stick = p1Stick,
                                    p2Stick = p2Stick,
                                    activeButtons = activeButtons
                                )
                            )
                        }
                    }
                }
        ) {
            // TOP DOCK: PLAYER 2 (Magenta)
            PlayerControlDock(
                playerLabel = stringResource(R.string.p2_label),
                score = uiState.scoreP2,
                targetScore = uiState.targetScore,
                playerColor = NeonMagenta,
                isTopDock = true,
                flip180 = uiState.flipP2Controls,
                stickVector = touchSnapshot.p2Stick,
                dashPressed = TouchZone.P2_DASH in touchSnapshot.activeButtons,
                firePressed = TouchZone.P2_FIRE in touchSnapshot.activeButtons,
                hasShield = uiState.p2Shield,
                hasRapid = uiState.p2Rapid,
                hasTurbo = uiState.p2Turbo,
                joySize = joySize,
                knobSize = knobSize,
                dashSize = dashSize,
                shootSize = shootSize,
                joystickZone = TouchZone.P2_JOYSTICK,
                dashZone = TouchZone.P2_DASH,
                fireZone = TouchZone.P2_FIRE,
                joystickDesc = stringResource(R.string.joystick_p2),
                dashDesc = stringResource(R.string.dash_p2),
                fireDesc = stringResource(R.string.fire_p2),
                scoreTag = "p2_score_text",
                onZonePositioned = { zone, coords -> zoneCoords[zone] = coords }
            )

            // MIDDLE ARENA: Strictly framed between the two docks
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                ArenaCanvas(
                    engine = viewModel.engine,
                    frameTickProvider = { viewModel.frameTick }
                )

                // Center Pause (||) button matching reference layout + Sound toggle on side
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (controlsEnabled) {
                        Surface(
                            color = ArenaSurface.copy(alpha = 0.85f),
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, OutlineSubtle),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.pauseGame() }
                                .testTag("pause_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = stringResource(R.string.pause_desc),
                                    tint = TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // Compact Mute toggle tucked cleanly at top-right of the arena frame
                IconButton(
                    onClick = viewModel::toggleSound,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("sound_toggle_button")
                ) {
                    Icon(
                        imageVector = if (uiState.soundEnabled) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.AutoMirrored.Filled.VolumeOff
                        },
                        contentDescription = stringResource(
                            if (uiState.soundEnabled) R.string.sound_on_desc else R.string.sound_off_desc
                        ),
                        tint = TextSecondary
                    )
                }

                // Round Score Banner
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.phase == MatchPhase.ROUND_OVER && uiState.roundWinner != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    val winnerColor = if (uiState.roundWinner == PlayerId.P1) NeonCyan else NeonMagenta
                    val winnerLabel = if (uiState.roundWinner == PlayerId.P1) "PLAYER 1 SCORES!" else "PLAYER 2 SCORES!"
                    Surface(
                        color = ArenaSurface.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, winnerColor)
                    ) {
                        Text(
                            text = winnerLabel,
                            style = MaterialTheme.typography.titleLarge,
                            color = winnerColor,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            // BOTTOM DOCK: PLAYER 1 (Cyan)
            PlayerControlDock(
                playerLabel = stringResource(R.string.p1_label),
                score = uiState.scoreP1,
                targetScore = uiState.targetScore,
                playerColor = NeonCyan,
                isTopDock = false,
                flip180 = false,
                stickVector = touchSnapshot.p1Stick,
                dashPressed = TouchZone.P1_DASH in touchSnapshot.activeButtons,
                firePressed = TouchZone.P1_FIRE in touchSnapshot.activeButtons,
                hasShield = uiState.p1Shield,
                hasRapid = uiState.p1Rapid,
                hasTurbo = uiState.p1Turbo,
                joySize = joySize,
                knobSize = knobSize,
                dashSize = dashSize,
                shootSize = shootSize,
                joystickZone = TouchZone.P1_JOYSTICK,
                dashZone = TouchZone.P1_DASH,
                fireZone = TouchZone.P1_FIRE,
                joystickDesc = stringResource(R.string.joystick_p1),
                dashDesc = stringResource(R.string.dash_p1),
                fireDesc = stringResource(R.string.fire_p1),
                scoreTag = "p1_score_text",
                onZonePositioned = { zone, coords -> zoneCoords[zone] = coords }
            )
        }

        // Start / Pause / Match Victory Overlay
        AnimatedVisibility(
            visible = uiState.phase == MatchPhase.MENU ||
                uiState.phase == MatchPhase.PAUSED ||
                uiState.phase == MatchPhase.MATCH_OVER,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            MatchOverlayDialog(
                uiState = uiState,
                onSelectMap = viewModel::selectMap,
                onSelectTarget = viewModel::selectTargetScore,
                onTogglePowerUps = viewModel::togglePowerUps,
                onToggleFlipP2 = viewModel::toggleFlipP2,
                onStartMatch = viewModel::startNewMatch,
                onResumeMatch = viewModel::resumeGame,
                onReturnToMenu = viewModel::returnToMenu
            )
        }
    }
}

private fun computeNormalizedStick(
    pointer: Offset,
    center: Offset,
    maxRadiusPx: Float
): Offset {
    val dx = pointer.x - center.x
    val dy = pointer.y - center.y
    val dist = hypot(dx, dy)
    if (dist <= 0.001f) return Offset.Zero
    val clamped = dist.coerceAtMost(maxRadiusPx)
    val scale = clamped / (dist * maxRadiusPx)
    return Offset(dx * scale, dy * scale)
}

@Composable
private fun PlayerControlDock(
    playerLabel: String,
    score: Int,
    targetScore: Int,
    playerColor: Color,
    isTopDock: Boolean,
    flip180: Boolean,
    stickVector: Offset,
    dashPressed: Boolean,
    firePressed: Boolean,
    hasShield: Boolean,
    hasRapid: Boolean,
    hasTurbo: Boolean,
    joySize: Dp,
    knobSize: Dp,
    dashSize: Dp,
    shootSize: Dp,
    joystickZone: TouchZone,
    dashZone: TouchZone,
    fireZone: TouchZone,
    joystickDesc: String,
    dashDesc: String,
    fireDesc: String,
    scoreTag: String,
    onZonePositioned: (TouchZone, LayoutCoordinates) -> Unit
) {
    val density = LocalDensity.current
    val maxTravelPx = with(density) { ((joySize - knobSize) * 0.5f).toPx() }
    val knobOffsetX = if (flip180) -stickVector.x * maxTravelPx else stickVector.x * maxTravelPx
    val knobOffsetY = if (flip180) -stickVector.y * maxTravelPx else stickVector.y * maxTravelPx

    val dockInsets = if (isTopDock) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    } else {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    }

    Surface(
        color = ArenaSurface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(
            topStart = if (isTopDock) 0.dp else 22.dp,
            topEnd = if (isTopDock) 0.dp else 22.dp,
            bottomStart = if (isTopDock) 22.dp else 0.dp,
            bottomEnd = if (isTopDock) 22.dp else 0.dp
        ),
        border = BorderStroke(1.5.dp, playerColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(dockInsets)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationZ = if (flip180) 180f else 0f
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            // Header strip: Player tag + Score + Active Buffs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = playerLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = playerColor
                    )
                    Surface(
                        color = playerColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, playerColor.copy(alpha = 0.55f))
                    ) {
                        val scoreText = if (targetScore > 0) "$score / $targetScore" else "$score"
                        Text(
                            text = scoreText,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = playerColor,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                                .testTag(scoreTag)
                        )
                    }
                }

                BuffIndicators(
                    hasShield = hasShield,
                    hasRapid = hasRapid,
                    hasTurbo = hasTurbo
                )
            }

            // Main Dock Row: Analog Joystick (Left) — Dash (Center) — Shoot (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Virtual Analog Joystick
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(joySize)
                        .clip(CircleShape)
                        .background(ArenaSurfaceElevated.copy(alpha = 0.72f))
                        .border(2.dp, playerColor.copy(alpha = 0.45f), CircleShape)
                        .onGloballyPositioned { onZonePositioned(joystickZone, it) }
                        .testTag(joystickZone.name.lowercase())
                        .semantics { contentDescription = joystickDesc }
                ) {
                    // Inner guide ring
                    Box(
                        modifier = Modifier
                            .size(joySize * 0.52f)
                            .border(1.dp, playerColor.copy(alpha = 0.18f), CircleShape)
                    )
                    // Thumb puck
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = knobOffsetX.roundToInt(),
                                    y = knobOffsetY.roundToInt()
                                )
                            }
                            .size(knobSize)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color.White, playerColor)
                                )
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                    )
                }

                // Center Secondary Action: DASH
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(dashSize)
                        .clip(CircleShape)
                        .background(
                            if (dashPressed) playerColor.copy(alpha = 0.35f) else ArenaSurfaceElevated
                        )
                        .border(
                            width = if (dashPressed) 2.5.dp else 1.5.dp,
                            color = if (dashPressed) Color.White else OutlineSubtle,
                            shape = CircleShape
                        )
                        .onGloballyPositioned { onZonePositioned(dashZone, it) }
                        .testTag(dashZone.name.lowercase())
                        .semantics { contentDescription = dashDesc }
                ) {
                    Text(
                        text = stringResource(R.string.dash_label),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                        color = if (dashPressed) Color.White else TextPrimary
                    )
                }

                // Primary Action: Big Circular SHOOT Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(shootSize)
                        .clip(CircleShape)
                        .background(
                            if (firePressed) Color.White else playerColor
                        )
                        .border(
                            width = 3.dp,
                            color = if (firePressed) playerColor else Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .onGloballyPositioned { onZonePositioned(fireZone, it) }
                        .testTag(fireZone.name.lowercase())
                        .semantics { contentDescription = fireDesc }
                ) {
                    Text(
                        text = stringResource(R.string.shoot_label),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                        color = if (firePressed || playerColor == NeonCyan) ArenaAbyss else Color.White
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun BuffIndicators(
    hasShield: Boolean,
    hasRapid: Boolean,
    hasTurbo: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (hasShield) {
            BuffBadge(icon = Icons.Filled.Shield, tint = NeonGreen, label = "Shield active")
        }
        if (hasRapid) {
            BuffBadge(icon = Icons.Filled.Bolt, tint = NeonGold, label = "Rapid fire active")
        }
        if (hasTurbo) {
            BuffBadge(icon = Icons.Filled.Speed, tint = NeonViolet, label = "Speed boost active")
        }
    }
}

@Composable
private fun BuffBadge(icon: ImageVector, tint: Color, label: String) {
    Surface(
        color = tint.copy(alpha = 0.16f),
        shape = CircleShape,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.7f)),
        modifier = Modifier.size(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun MatchOverlayDialog(
    uiState: GameUiState,
    onSelectMap: (ArenaMap) -> Unit,
    onSelectTarget: (Int) -> Unit,
    onTogglePowerUps: (Boolean) -> Unit,
    onToggleFlipP2: (Boolean) -> Unit,
    onStartMatch: () -> Unit,
    onResumeMatch: () -> Unit,
    onReturnToMenu: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(ArenaAbyss.copy(alpha = 0.78f))
            .padding(20.dp)
    ) {
        Surface(
            color = ArenaSurface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(
                1.5.dp,
                Brush.horizontalGradient(listOf(NeonCyan.copy(alpha = 0.7f), NeonMagenta.copy(alpha = 0.7f)))
            ),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (uiState.phase) {
                    MatchPhase.MENU -> {
                        Text(
                            text = "TANK DUEL",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp),
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "2-Player Shared Screen  •  Top vs Bottom",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        SectionLabel(text = stringResource(R.string.arena_label))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArenaMap.entries.forEach { map ->
                                val selected = map == uiState.selectedMap
                                Surface(
                                    color = if (selected) NeonCyan.copy(alpha = 0.16f) else ArenaSurfaceElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (selected) NeonCyan else OutlineSubtle
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectMap(map) }
                                        .testTag("map_${map.name.lowercase()}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = map.title,
                                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
                                            color = if (selected) NeonCyan else TextPrimary,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = map.subtitle,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = TextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        SectionLabel(text = stringResource(R.string.target_score_label))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(3 to "3", 5 to "5", 7 to "7", 0 to "∞").forEach { (value, label) ->
                                val selected = uiState.targetScore == value
                                Surface(
                                    color = if (selected) NeonMagenta.copy(alpha = 0.2f) else ArenaSurfaceElevated,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (selected) NeonMagenta else OutlineSubtle
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectTarget(value) }
                                        .testTag("target_$value")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (selected) NeonMagenta else TextPrimary
                                        )
                                    }
                                }
                            }
                        }

                        // Power-ups & Tabletop Flip Toggles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.powerups_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            Switch(
                                checked = uiState.powerUpsEnabled,
                                onCheckedChange = onTogglePowerUps,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ArenaAbyss,
                                    checkedTrackColor = NeonGreen,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = ArenaSurfaceElevated
                                ),
                                modifier = Modifier.testTag("powerups_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.flip_p2_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            Switch(
                                checked = uiState.flipP2Controls,
                                onCheckedChange = onToggleFlipP2,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ArenaAbyss,
                                    checkedTrackColor = NeonMagenta,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = ArenaSurfaceElevated
                                ),
                                modifier = Modifier.testTag("flip_p2_switch")
                            )
                        }

                        Button(
                            onClick = onStartMatch,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = ArenaAbyss
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("start_duel_button")
                        ) {
                            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.play_duel),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                            )
                        }
                    }

                    MatchPhase.PAUSED -> {
                        Text(
                            text = "MATCH PAUSED",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "P1 ${uiState.scoreP1}  —  ${uiState.scoreP2} P2",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.flip_p2_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            Switch(
                                checked = uiState.flipP2Controls,
                                onCheckedChange = onToggleFlipP2,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ArenaAbyss,
                                    checkedTrackColor = NeonMagenta,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = ArenaSurfaceElevated
                                ),
                                modifier = Modifier.testTag("pause_flip_p2_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onReturnToMenu,
                                border = BorderStroke(1.5.dp, OutlineSubtle),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("menu_button")
                            ) {
                                Text("SETUP", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            }
                            OutlinedButton(
                                onClick = onStartMatch,
                                border = BorderStroke(1.5.dp, NeonMagenta),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("restart_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = NeonMagenta)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("RESTART", style = MaterialTheme.typography.labelLarge, color = NeonMagenta)
                            }
                        }
                        Button(
                            onClick = onResumeMatch,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = ArenaAbyss
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("resume_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.resume_duel),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                            )
                        }
                    }

                    MatchPhase.MATCH_OVER -> {
                        val isP1Win = uiState.matchWinner == PlayerId.P1
                        val winColor = if (isP1Win) NeonCyan else NeonMagenta
                        Text(
                            text = if (isP1Win) "PLAYER 1 WINS!" else "PLAYER 2 WINS!",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp),
                            color = winColor,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "FINAL SCORE: ${uiState.scoreP1} — ${uiState.scoreP2}",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onReturnToMenu,
                                border = BorderStroke(1.5.dp, OutlineSubtle),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("change_arena_button")
                            ) {
                                Text("ARENA", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            }
                            Button(
                                onClick = onStartMatch,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = winColor,
                                    contentColor = if (isP1Win) ArenaAbyss else Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("rematch_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.rematch),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        modifier = Modifier.fillMaxWidth()
    )
}
