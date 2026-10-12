package app.sparkbible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlinx.coroutines.isActive
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sparkbible.SparkViewModel
import app.sparkbible.data.PlantEntity
import app.sparkbible.data.ProgressStyle

@Composable
fun GardenScreen(vm: SparkViewModel) {
    val plants by vm.plants.collectAsStateWithLifecycle()
    val moments by vm.moments.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        val sky = settings.progressStyle == ProgressStyle.CONSTELLATION
        Text(if (sky) "Your sky" else "Your garden", style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
        Text(
            if (sky) "Stars join into a shape. Nothing is erased."
            else "They sprout and open. Nothing here dies or resets.",
            color = palette.muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        if (settings.progressStyle == ProgressStyle.GARDEN) {
            GardenBed(plants)
        } else {
            Constellation(plants)
        }
        if (sky) {
            Text(skyCaption(plants.size), color = palette.ink, modifier = Modifier.padding(top = 14.dp))
        } else {
            val shown = plants.size.coerceAtMost(18)
            Text(
                when {
                    plants.isEmpty() -> "Nothing planted yet. One verse is enough to start."
                    plants.size <= 18 -> "${plants.size} sprout${if (plants.size == 1) "" else "s"} from showing up."
                    else -> "$shown in the bed, ${plants.size} remembered. Nothing is thrown away."
                },
                color = palette.ink,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        Text(
            if (settings.progressStyle == ProgressStyle.GARDEN) "See it as a sky" else "See it as a garden",
            color = palette.sage,
            modifier = Modifier
                .clickable {
                    vm.updateSettings { current ->
                        current.copy(
                            progressStyle = if (current.progressStyle == ProgressStyle.GARDEN) {
                                ProgressStyle.CONSTELLATION
                            } else {
                                ProgressStyle.GARDEN
                            },
                        )
                    }
                }
                .padding(vertical = 12.dp),
        )
        WeekBlock(moments, settings.weeklyAim)
        Text(
            "Any amount counts. Restarts are normal.",
            color = palette.muted,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun GardenBed(plants: List<PlantEntity>) {
    val palette = LocalPalette.current
    var started by remember(plants.size) { mutableLongStateOf(System.nanoTime()) }
    var frame by remember(plants.size) { mutableLongStateOf(started) }
    LaunchedEffect(plants.size) {
        val origin = System.nanoTime()
        started = origin
        while (isActive) {
            withFrameNanos { frame = it }
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(palette.sageSoft)
            .semantics {
                contentDescription = if (plants.isEmpty()) {
                    "Garden bed. Nothing planted yet."
                } else {
                    "Garden bed. ${plants.size} plant${if (plants.size == 1) "" else "s"} sprouting and blooming."
                }
            },
    ) {
        val soil = size.height * 0.74f
        drawRect(
            palette.sage.copy(alpha = 0.18f),
            topLeft = Offset(0f, soil),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - soil),
        )
        val elapsed = (frame - started) / 1_000_000f
        val swayTime = frame / 1_000_000_000f
        plants.takeLast(18).forEachIndexed { index, plant ->
            val h = plant.id.fold(0) { acc, c -> acc * 31 + c.code }
            val x = size.width * (0.1f + (h and 1023) / 1023f * 0.8f)
            val scale = 0.8f + ((h shr 10) and 255) / 255f * 0.45f
            val growth = easeOut(((elapsed - index * 80f) / 1200f).coerceIn(0f, 1f))
            val sway = sin(swayTime * 1.4f + x * 0.02f) * 3.5f * growth
            drawPlant(plant.kind, Offset(x, soil), scale, growth, sway, palette)
        }
    }
}

private fun easeOut(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x) * (1f - x)
}

private fun DrawScope.drawPlant(
    kind: Int,
    base: Offset,
    scale: Float,
    growth: Float,
    sway: Float,
    palette: SparkPalette,
) {
    if (growth <= 0.01f) {
        drawCircle(palette.sage, 3f * scale, Offset(base.x, base.y - 2f))
        return
    }
    val stemT = easeOut((growth / 0.55f).coerceIn(0f, 1f))
    val stem = 46f * scale * stemT
    val top = Offset(base.x + sway, base.y - stem)
    drawLine(palette.sage, base, top, strokeWidth = 3f * scale)
    val leaf = palette.sage
    val bloom = if (kind % 2 == 0) palette.ember else palette.sage
    val bloomT = easeOut(((growth - 0.58f) / 0.42f).coerceIn(0f, 1f))
    val leafT = easeOut(((growth - 0.34f) / 0.4f).coerceIn(0f, 1f))
    if (leafT > 0f && kind % 6 != 0) {
        drawCircle(leaf.copy(alpha = 0.85f), 9f * scale * leafT, Offset(base.x - 10f * scale, base.y - stem * 0.5f))
    }
    if (bloomT <= 0f) return
    when (kind % 6) {
        0 -> drawCircle(bloom, 8f * scale * bloomT, top)
        1 -> drawCircle(bloom, 6f * scale * bloomT, top)
        2 -> {
            val path = Path().apply {
                moveTo(top.x, top.y)
                quadraticTo(top.x + 18f * scale, top.y + stem * 0.25f, top.x + 8f * scale, top.y + stem * 0.55f)
            }
            drawPath(path, leaf, style = Stroke(width = 2.5f * scale))
            drawCircle(bloom, 7f * scale * bloomT, top)
        }
        3 -> drawCircle(leaf.copy(alpha = 0.9f), 14f * scale * bloomT, Offset(top.x, top.y + stem * 0.15f))
        4 -> {
            drawCircle(bloom, 5f * scale * bloomT, Offset(top.x - 6f * scale, top.y))
            drawCircle(palette.emberSoft, 5f * scale * bloomT, Offset(top.x + 6f * scale, top.y + 4f))
        }
        else -> drawCircle(palette.star, 9f * scale * bloomT, top)
    }
}

@Composable
private fun Constellation(plants: List<PlantEntity>) {
    val palette = LocalPalette.current
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { frame = it }
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(palette.sky)
            .semantics { contentDescription = "Night sky. ${skyCaption(plants.size)}" },
    ) {
        val picture = skyPicture(plants.size, size.width, size.height)
        val pulse = 1f + 0.22f * sin(frame / 1_000_000_000f * 2.4f)
        picture.lines.forEach { (a, b) ->
            drawLine(palette.star.copy(alpha = 0.45f), a, b, strokeWidth = 1.6f)
        }
        picture.stars.forEach { star ->
            val radius = if (star.newest) 6.5f * pulse else 3.6f
            drawCircle(if (star.newest) palette.ember else palette.star, radius, star.at)
        }
    }
}
