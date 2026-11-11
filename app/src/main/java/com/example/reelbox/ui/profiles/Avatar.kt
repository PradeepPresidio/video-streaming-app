package com.example.reelbox.ui.profiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Original avatar palette (ReelBox brand-adjacent colours). */
val AvatarColors = listOf(
    Color(0xFFFF8A3D), Color(0xFF2EC4B6), Color(0xFF8E7DFF),
    Color(0xFF3DA5FF), Color(0xFF9BD44A), Color(0xFFFF5C8A),
)

/** Generated avatar: a coloured tile with a simple face; the eye/mouth style varies with [index]. */
@Composable
fun Avatar(index: Int, size: Dp, modifier: Modifier = Modifier, kids: Boolean = false) {
    val color = AvatarColors[Math.floorMod(index, AvatarColors.size)]
    Box(modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            drawRoundRect(color, cornerRadius = CornerRadius(w * 0.16f))
            val ink = Color(0xFF0B0D12)
            val eyeY = w * 0.42f
            val eyeDx = w * 0.17f
            when (Math.floorMod(index, 3)) {
                0 -> listOf(-1, 1).forEach { drawCircle(ink, w * 0.07f, Offset(w / 2 + it * eyeDx, eyeY)) }
                1 -> listOf(-1, 1).forEach {
                    drawRoundRect(ink, Offset(w / 2 + it * eyeDx - w * 0.09f, eyeY - w * 0.03f), Size(w * 0.18f, w * 0.06f), CornerRadius(w * 0.03f))
                }
                else -> listOf(-1, 1).forEach {
                    drawCircle(Color.White, w * 0.1f, Offset(w / 2 + it * eyeDx, eyeY))
                    drawCircle(ink, w * 0.05f, Offset(w / 2 + it * eyeDx, eyeY))
                }
            }
            drawArc(
                ink, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(w * 0.3f, w * 0.42f), size = Size(w * 0.4f, w * 0.3f),
                style = Stroke(width = w * 0.06f, cap = StrokeCap.Round),
            )
        }
        if (kids) {
            Text(
                "KIDS", color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value / 7).sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.05f)
                    .clip(RoundedCornerShape(4.dp)).background(Color(0x99000000)).padding(horizontal = 4.dp),
            )
        }
    }
}
