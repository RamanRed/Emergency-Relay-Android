package com.raman.emergencyrelay.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * An emergency type. code matches EmergencyCode (1..7); severity
 * is a sensible default so the user never has to pick a number.
 */
data class SosType(
    val code: Int,
    val name: String,
    val severity: Int
)

/*
 * Six specific types sit on the ring (clockwise from the top).
 * General SOS is the centre — the one-tap / panic action.
 */
val SOS_TYPES = listOf(
    SosType(1, "Medical", 5),
    SosType(2, "Fire", 5),
    SosType(3, "Police", 4),
    SosType(4, "Accident", 4),
    SosType(5, "Trapped", 5),
    SosType(6, "Missing", 3)
)

val SOS_CENTER = SosType(7, "General SOS", 5)

/**
 * SOS wheel.
 *
 * Interaction model (minimal + safe):
 *   - tap a wedge or the centre  -> preview that type (onPreview)
 *   - HOLD a wedge or the centre -> send it (onSend)
 *
 * Holding prevents accidental sends; the big centre makes the
 * panic action a single, easy-to-hit gesture.
 *
 * @param selectedIndex  null = centre (General SOS), else 0..5
 */
@Composable
fun SosWheel(
    selectedIndex: Int?,
    onPreview: (Int?) -> Unit,
    onSend: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val teal = MaterialTheme.colorScheme.primary          // selected wedge
    val onTeal = MaterialTheme.colorScheme.onPrimary
    val wedge = WheelWedge                                 // unselected wedge (brighter)
    val wedgeText = MaterialTheme.colorScheme.onSurface    // brighter label
    val coral = MaterialTheme.colorScheme.error           // centre SOS
    val onCoral = MaterialTheme.colorScheme.onError
    val bg = MaterialTheme.colorScheme.background

    val measurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .size(300.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pos ->
                        resolve(pos, size.width.toFloat(), size.height.toFloat())
                            ?.let { onPreview(it.value) }
                    },
                    onLongPress = { pos ->
                        resolve(pos, size.width.toFloat(), size.height.toFloat())
                            ?.let { onSend(it.value) }
                    }
                )
            }
    ) {
        val w = size.width
        val cx = w / 2f
        val cy = size.height / 2f
        val outerR = w * 0.5f
        val wedgeInner = w * 0.30f
        val centerR = w * 0.24f
        val labelR = (wedgeInner + outerR) / 2f

        // --- wedges (pie sectors, hole carved out afterwards) ---
        for (i in SOS_TYPES.indices) {
            val slotStart = -120f + i * 60f          // wedge 0 centred at top
            val fill = if (selectedIndex == i) teal else wedge
            drawArc(
                color = fill,
                startAngle = slotStart + 2f,
                sweepAngle = 56f,
                useCenter = true,
                topLeft = Offset(cx - outerR, cy - outerR),
                size = Size(outerR * 2f, outerR * 2f)
            )
        }

        // carve the donut hole with the background colour
        drawCircle(color = bg, radius = wedgeInner, center = Offset(cx, cy))

        // --- centre SOS button ---
        drawCircle(color = coral, radius = centerR, center = Offset(cx, cy))

        // --- wedge labels ---
        for (i in SOS_TYPES.indices) {
            val mid = Math.toRadians((-90f + i * 60f).toDouble())
            val lx = cx + labelR * cos(mid).toFloat()
            val ly = cy + labelR * sin(mid).toFloat()
            val color = if (selectedIndex == i) onTeal else wedgeText
            val layout = measurer.measure(
                SOS_TYPES[i].name,
                TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            )
            drawText(
                layout,
                topLeft = Offset(
                    lx - layout.size.width / 2f,
                    ly - layout.size.height / 2f
                )
            )
        }

        // --- centre text: SOS + HOLD TO SEND ---
        val sos = measurer.measure(
            "SOS",
            TextStyle(color = onCoral, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        )
        drawText(
            sos,
            topLeft = Offset(cx - sos.size.width / 2f, cy - sos.size.height / 2f - 8f)
        )
        val hold = measurer.measure(
            "HOLD TO SEND",
            TextStyle(color = onCoral, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        )
        drawText(
            hold,
            topLeft = Offset(cx - hold.size.width / 2f, cy + 12f)
        )
    }
}

/*
 * Map a tap/hold position to a target:
 *   value = null  -> centre (General SOS)
 *   value = 0..5  -> a ring wedge
 * Returns null for the dead gap between centre and ring.
 */
private class Hit(val value: Int?)

private fun resolve(pos: Offset, w: Float, h: Float): Hit? {
    val cx = w / 2f
    val cy = h / 2f
    val dx = pos.x - cx
    val dy = pos.y - cy
    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

    val outerR = w * 0.5f
    val wedgeInner = w * 0.30f
    val centerR = w * 0.24f

    if (dist <= centerR) return Hit(null)                 // centre
    if (dist in wedgeInner..outerR) {
        val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
        var a = ((deg - (-120.0)) % 360.0 + 360.0) % 360.0
        val idx = (a / 60.0).toInt().coerceIn(0, SOS_TYPES.size - 1)
        return Hit(idx)
    }
    return null                                           // gap ring
}
