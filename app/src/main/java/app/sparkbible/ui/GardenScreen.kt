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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
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
        Text("Your garden", style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
        Text(
            "It only grows. Nothing here dies or resets.",
            color = palette.muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        if (settings.progressStyle == ProgressStyle.GARDEN) {
            GardenBed(plants)
        } else {
            Constellation(plants)
        }
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
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(palette.sageSoft),
    ) {
        val soil = size.height * 0.74f
        drawRect(
            palette.sage.copy(alpha = 0.18f),
            topLeft = Offset(0f, soil),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - soil),
        )
        plants.takeLast(18).forEach { plant ->
            val h = plant.id.fold(0) { acc, c -> acc * 31 + c.code }
            val x = size.width * (0.1f + (h and 1023) / 1023f * 0.8f)
            val scale = 0.8f + ((h shr 10) and 255) / 255f * 0.45f
            drawPlant(plant.kind, Offset(x, soil), scale, palette)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlant(
    kind: Int,
    base: Offset,
    scale: Float,
    palette: SparkPalette,
) {
    val stem = 46f * scale
    drawLine(palette.sage, base, Offset(base.x, base.y - stem), strokeWidth = 3f * scale)
    val leaf = palette.sage
    val bloom = if (kind % 2 == 0) palette.ember else palette.sage
    when (kind % 6) {
        0 -> drawCircle(bloom, 8f * scale, Offset(base.x, base.y - stem))
        1 -> {
            drawCircle(leaf, 10f * scale, Offset(base.x - 8f * scale, base.y - stem * 0.55f))
            drawCircle(bloom, 6f * scale, Offset(base.x, base.y - stem))
        }
        2 -> {
            val path = Path().apply {
                moveTo(base.x, base.y - stem)
                quadraticTo(base.x + 18f * scale, base.y - stem * 0.7f, base.x + 8f * scale, base.y - stem * 0.35f)
            }
            drawPath(path, leaf)
            drawCircle(bloom, 7f * scale, Offset(base.x, base.y - stem))
        }
        3 -> drawCircle(leaf.copy(alpha = 0.9f), 14f * scale, Offset(base.x, base.y - stem * 0.72f))
        4 -> {
            drawCircle(bloom, 5f * scale, Offset(base.x - 6f * scale, base.y - stem))
            drawCircle(palette.emberSoft, 5f * scale, Offset(base.x + 6f * scale, base.y - stem + 4f))
        }
        else -> drawCircle(palette.star, 9f * scale, Offset(base.x, base.y - stem))
    }
}

@Composable
private fun Constellation(plants: List<PlantEntity>) {
    val palette = LocalPalette.current
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(palette.sky),
    ) {
        val pts = plants.takeLast(24).map { plant ->
            val h = plant.id.fold(0) { acc, c -> acc * 33 + c.code }
            Offset(
                size.width * (0.08f + (h and 1023) / 1023f * 0.84f),
                size.height * (0.12f + ((h shr 8) and 1023) / 1023f * 0.76f),
            )
        }
        for (i in 1 until pts.size) {
            drawLine(palette.star.copy(alpha = 0.35f), pts[i - 1], pts[i], strokeWidth = 2f)
        }
        pts.forEachIndexed { index, point ->
            drawCircle(if (index == pts.lastIndex) palette.ember else palette.star, if (index == pts.lastIndex) 6f else 4f, point)
        }
    }
}
