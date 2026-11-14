package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.game.BulletEntity
import com.example.game.GameEngine
import com.example.game.PlayerId
import com.example.game.PowerUpEntity
import com.example.game.PowerUpType
import com.example.game.TankEntity
import com.example.ui.theme.ArenaAbyss
import com.example.ui.theme.ArenaGridLine
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGold
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.WallFill
import com.example.ui.theme.WallStroke
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ArenaCanvas(
    engine: GameEngine,
    frameTickProvider: () -> Long,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val shakeRng = remember { Random(1337) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val tick = frameTickProvider()

        // Full-bleed dark cyber background
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF0E1626), ArenaAbyss),
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = maxOf(size.width, size.height) * 0.78f
            )
        )

        val gridSpacing = 56f
        var gx = 0f
        while (gx < size.width) {
            drawLine(
                color = ArenaGridLine,
                start = Offset(gx, 0f),
                end = Offset(gx, size.height),
                strokeWidth = 1.2f
            )
            gx += gridSpacing
        }
        var gy = 0f
        while (gy < size.height) {
            drawLine(
                color = ArenaGridLine,
                start = Offset(0f, gy),
                end = Offset(size.width, gy),
                strokeWidth = 1.2f
            )
            gy += gridSpacing
        }

        // Uniformly fit 900x1280 portrait arena inside the middle area
        val scale = min(
            size.width / GameEngine.ARENA_WIDTH,
            size.height / GameEngine.ARENA_HEIGHT
        )
        val arenaPixelW = GameEngine.ARENA_WIDTH * scale
        val arenaPixelH = GameEngine.ARENA_HEIGHT * scale
        val offsetX = (size.width - arenaPixelW) * 0.5f
        val offsetY = (size.height - arenaPixelH) * 0.5f

        val shakeAmp = engine.screenShake * scale
        val shakeX = if (shakeAmp > 0.2f) (shakeRng.nextFloat() * 2f - 1f) * shakeAmp else 0f
        val shakeY = if (shakeAmp > 0.2f) (shakeRng.nextFloat() * 2f - 1f) * shakeAmp else 0f

        translate(left = offsetX + shakeX, top = offsetY + shakeY) {
            drawArenaFrame(scale)
            drawWalls(engine, scale)
            engine.activePowerUp?.let { drawPowerUp(it, scale, tick) }
            drawBullets(engine.bullets, scale)
            if (engine.p2.alive) drawTank(engine.p2, scale, tick)
            if (engine.p1.alive) drawTank(engine.p1, scale, tick)
            drawShockwaves(engine, scale)
            drawParticles(engine, scale)
            drawFloatingBanners(engine, scale, textMeasurer)
        }
    }
}

private fun DrawScope.drawArenaFrame(scale: Float) {
    val margin = GameEngine.ARENA_MARGIN * scale
    val w = (GameEngine.ARENA_WIDTH - GameEngine.ARENA_MARGIN * 2f) * scale
    val h = (GameEngine.ARENA_HEIGHT - GameEngine.ARENA_MARGIN * 2f) * scale

    // Horizontal center divider dashed line + center faceoff ring
    val centerY = GameEngine.ARENA_HEIGHT * 0.5f * scale
    val centerX = GameEngine.ARENA_WIDTH * 0.5f * scale
    var x = margin + 14f * scale
    while (x < margin + w - 14f * scale) {
        drawLine(
            color = Color(0xFF1C2C47),
            start = Offset(x, centerY),
            end = Offset((x + 18f * scale).coerceAtMost(margin + w - 14f * scale), centerY),
            strokeWidth = 2.2f * scale
        )
        x += 34f * scale
    }
    drawCircle(
        color = Color(0xFF1C2C47),
        radius = 86f * scale,
        center = Offset(centerX, centerY),
        style = Stroke(width = 2.2f * scale)
    )

    // Rounded neon perimeter frame
    drawRoundRect(
        color = Color(0xFF223A5E),
        topLeft = Offset(margin, margin),
        size = Size(w, h),
        cornerRadius = CornerRadius(32f * scale, 32f * scale),
        style = Stroke(width = 4f * scale)
    )

    // Top home zone accents (P2 - Magenta)
    val accentLen = 58f * scale
    drawLine(NeonMagenta.copy(alpha = 0.75f), Offset(margin + 24f * scale, margin), Offset(margin + 24f * scale + accentLen, margin), strokeWidth = 5f * scale)
    drawLine(NeonMagenta.copy(alpha = 0.75f), Offset(margin + w - 24f * scale - accentLen, margin), Offset(margin + w - 24f * scale, margin), strokeWidth = 5f * scale)

    // Bottom home zone accents (P1 - Cyan)
    drawLine(NeonCyan.copy(alpha = 0.75f), Offset(margin + 24f * scale, margin + h), Offset(margin + 24f * scale + accentLen, margin + h), strokeWidth = 5f * scale)
    drawLine(NeonCyan.copy(alpha = 0.75f), Offset(margin + w - 24f * scale - accentLen, margin + h), Offset(margin + w - 24f * scale, margin + h), strokeWidth = 5f * scale)
}

private fun DrawScope.drawWalls(engine: GameEngine, scale: Float) {
    val corner = CornerRadius(12f * scale, 12f * scale)
    for (wall in engine.walls) {
        val left = wall.left * scale
        val top = wall.top * scale
        val w = wall.width * scale
        val h = wall.height * scale

        drawRoundRect(
            color = WallStroke.copy(alpha = 0.25f),
            topLeft = Offset(left - 3f * scale, top - 3f * scale),
            size = Size(w + 6f * scale, h + 6f * scale),
            cornerRadius = corner
        )
        drawRoundRect(
            color = WallFill,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = corner
        )
        drawRoundRect(
            color = WallStroke,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = corner,
            style = Stroke(width = 3f * scale)
        )
        val inset = 8f * scale
        if (w > inset * 2 && h > inset * 2) {
            drawRoundRect(
                color = Color(0xFF1E2B44),
                topLeft = Offset(left + inset, top + inset),
                size = Size(w - inset * 2, h - inset * 2),
                cornerRadius = CornerRadius(6f * scale, 6f * scale),
                style = Stroke(width = 1.5f * scale)
            )
        }
    }
}

private fun DrawScope.drawPowerUp(powerUp: PowerUpEntity, scale: Float, tick: Long) {
    val cx = powerUp.x * scale
    val cy = powerUp.y * scale
    val r = powerUp.radius * scale
    val pulse = 1f + 0.14f * sin(powerUp.pulsePhase)

    val orbColor = when (powerUp.type) {
        PowerUpType.SHIELD -> NeonGreen
        PowerUpType.RAPID_FIRE -> NeonGold
        PowerUpType.SPEED_BOOST -> NeonViolet
    }

    drawCircle(
        color = orbColor.copy(alpha = 0.28f),
        radius = r * 1.45f * pulse,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = orbColor,
        radius = r * pulse,
        center = Offset(cx, cy),
        style = Stroke(width = 3f * scale)
    )
    drawCircle(
        color = Color(0xFF0C1422),
        radius = r * 0.85f * pulse,
        center = Offset(cx, cy)
    )

    rotate(degrees = (tick * 2.2f) % 360f, pivot = Offset(cx, cy)) {
        val d = r * 0.48f
        val path = Path().apply {
            moveTo(cx, cy - d)
            lineTo(cx + d, cy)
            lineTo(cx, cy + d)
            lineTo(cx - d, cy)
            close()
        }
        drawPath(path = path, color = orbColor)
    }
}

private fun DrawScope.drawTank(tank: TankEntity, scale: Float, tick: Long) {
    val mainColor = if (tank.id == PlayerId.P1) NeonCyan else NeonMagenta
    val dimColor = if (tank.id == PlayerId.P1) Color(0xFF0A2433) else Color(0xFF2D0A1B)
    val cx = tank.x * scale
    val cy = tank.y * scale
    val r = tank.radius * scale

    if (tank.hasShield) {
        val shieldPulse = 1f + 0.06f * sin(tick * 0.18f)
        drawCircle(
            color = NeonGreen.copy(alpha = 0.18f),
            radius = r * 1.48f * shieldPulse,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = NeonGreen,
            radius = r * 1.42f * shieldPulse,
            center = Offset(cx, cy),
            style = Stroke(width = 3f * scale)
        )
    }
    if (tank.speedBoostFrames > 0f || tank.dashActiveFrames > 0f) {
        drawCircle(
            color = if (tank.dashActiveFrames > 0f) mainColor.copy(alpha = 0.55f) else NeonViolet.copy(alpha = 0.45f),
            radius = r * 1.28f,
            center = Offset(cx, cy),
            style = Stroke(width = 2.5f * scale)
        )
    }

    val angleDeg = tank.angleRad * 180f / PI.toFloat()

    rotate(degrees = angleDeg, pivot = Offset(cx, cy)) {
        drawCircle(
            color = mainColor.copy(alpha = 0.22f),
            radius = r * 1.28f,
            center = Offset(cx, cy)
        )

        val treadW = r * 1.9f
        val treadH = r * 0.36f
        val treadCorner = CornerRadius(5f * scale, 5f * scale)

        drawRoundRect(
            color = Color(0xFF192233),
            topLeft = Offset(cx - treadW * 0.5f, cy - r * 0.98f),
            size = Size(treadW, treadH),
            cornerRadius = treadCorner
        )
        drawRoundRect(
            color = Color(0xFF192233),
            topLeft = Offset(cx - treadW * 0.5f, cy + r * 0.98f - treadH),
            size = Size(treadW, treadH),
            cornerRadius = treadCorner
        )

        val notchCount = 4
        val phaseShift = ((tank.treadPhase % 1f) + 1f) % 1f
        for (i in 0 until notchCount) {
            val frac = ((i + phaseShift) / notchCount)
            val nx = cx - treadW * 0.44f + frac * (treadW * 0.88f)
            drawLine(
                color = mainColor.copy(alpha = 0.55f),
                start = Offset(nx, cy - r * 0.94f),
                end = Offset(nx, cy - r * 0.66f),
                strokeWidth = 2.2f * scale
            )
            drawLine(
                color = mainColor.copy(alpha = 0.55f),
                start = Offset(nx, cy + r * 0.66f),
                end = Offset(nx, cy + r * 0.94f),
                strokeWidth = 2.2f * scale
            )
        }

        val hullW = r * 1.68f
        val hullH = r * 1.38f
        drawRoundRect(
            color = dimColor,
            topLeft = Offset(cx - hullW * 0.5f, cy - hullH * 0.5f),
            size = Size(hullW, hullH),
            cornerRadius = CornerRadius(10f * scale, 10f * scale)
        )
        drawRoundRect(
            color = mainColor,
            topLeft = Offset(cx - hullW * 0.5f, cy - hullH * 0.5f),
            size = Size(hullW, hullH),
            cornerRadius = CornerRadius(10f * scale, 10f * scale),
            style = Stroke(width = 3.2f * scale)
        )

        val recoil = tank.recoilOffset * scale
        val barrelLen = r * 1.35f - recoil
        drawLine(
            color = mainColor,
            start = Offset(cx, cy),
            end = Offset(cx + barrelLen, cy),
            strokeWidth = 10f * scale,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset(cx + r * 0.35f, cy),
            end = Offset(cx + barrelLen, cy),
            strokeWidth = 3.2f * scale,
            cap = StrokeCap.Round
        )

        if (tank.muzzleFlashFrames > 0f) {
            drawCircle(
                color = NeonGold.copy(alpha = 0.85f),
                radius = 12f * scale,
                center = Offset(cx + barrelLen + 5f * scale, cy)
            )
        }

        drawCircle(
            color = Color(0xFF0E1522),
            radius = r * 0.52f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = if (tank.rapidFireFrames > 0f) NeonGold else mainColor,
            radius = r * 0.52f,
            center = Offset(cx, cy),
            style = Stroke(width = 3f * scale)
        )
        drawCircle(
            color = mainColor,
            radius = r * 0.22f,
            center = Offset(cx, cy)
        )
    }

    if (tank.shootCooldownFrames > 0f) {
        val maxCd = if (tank.rapidFireFrames > 0f) GameEngine.RAPID_COOLDOWN else GameEngine.NORMAL_COOLDOWN
        val progress = (1f - tank.shootCooldownFrames / maxCd).coerceIn(0f, 1f)
        val barW = r * 1.6f
        val barY = cy + r * 1.35f
        drawLine(
            color = Color(0xFF1F2B3D),
            start = Offset(cx - barW * 0.5f, barY),
            end = Offset(cx + barW * 0.5f, barY),
            strokeWidth = 4f * scale,
            cap = StrokeCap.Round
        )
        drawLine(
            color = if (tank.rapidFireFrames > 0f) NeonGold else mainColor,
            start = Offset(cx - barW * 0.5f, barY),
            end = Offset(cx - barW * 0.5f + barW * progress, barY),
            strokeWidth = 4f * scale,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawBullets(bullets: List<BulletEntity>, scale: Float) {
    for (b in bullets) {
        val color = if (b.owner == PlayerId.P1) NeonCyan else NeonMagenta
        for (idx in b.trail.indices) {
            val (tx, ty) = b.trail[idx]
            val alpha = (1f - idx.toFloat() / b.trail.size) * 0.45f
            val tr = b.radius * scale * (1f - idx * 0.1f)
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = tr.coerceAtLeast(1.5f),
                center = Offset(tx * scale, ty * scale)
            )
        }
        val center = Offset(b.x * scale, b.y * scale)
        drawCircle(
            color = color.copy(alpha = 0.45f),
            radius = b.radius * 2.1f * scale,
            center = center
        )
        drawCircle(
            color = Color.White,
            radius = b.radius * scale,
            center = center
        )
    }
}

private fun DrawScope.drawShockwaves(engine: GameEngine, scale: Float) {
    for (s in engine.shockwaves) {
        val color = colorForType(s.colorType)
        drawCircle(
            color = color.copy(alpha = s.alpha.coerceIn(0f, 1f)),
            radius = s.radius * scale,
            center = Offset(s.x * scale, s.y * scale),
            style = Stroke(width = 3.5f * scale)
        )
    }
}

private fun DrawScope.drawParticles(engine: GameEngine, scale: Float) {
    for (p in engine.particles) {
        val color = colorForType(p.colorType)
        drawCircle(
            color = color.copy(alpha = p.life.coerceIn(0f, 1f)),
            radius = (p.radius * p.life.coerceAtLeast(0.2f)) * scale,
            center = Offset(p.x * scale, p.y * scale)
        )
    }
}

private fun DrawScope.drawFloatingBanners(
    engine: GameEngine,
    scale: Float,
    textMeasurer: TextMeasurer
) {
    for (b in engine.banners) {
        val color = colorForType(b.colorType)
        val alpha = b.life.coerceIn(0f, 1f)
        val layout = textMeasurer.measure(
            text = b.text,
            style = TextStyle(
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = (22f * scale).coerceIn(14f, 34f).sp,
                color = color.copy(alpha = alpha)
            )
        )
        val bx = (b.x * scale) - layout.size.width * 0.5f
        val by = (b.y * scale) - layout.size.height * 0.5f - (1f - alpha) * 32f * scale
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(bx, by)
        )
    }
}

private fun colorForType(type: Int): Color = when (type) {
    0 -> NeonCyan
    1 -> NeonMagenta
    2 -> NeonGold
    else -> NeonGreen
}
