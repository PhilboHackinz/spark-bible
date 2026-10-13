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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
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
    val progress by vm.progress.collectAsStateWithLifecycle()
    val books by vm.books.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    var shelf by rememberSaveable { mutableStateOf(false) }
    val finished = progress.finishedBooks.split(',').mapNotNull { it.toIntOrNull() }
    val finishedNames = finished.mapNotNull { books.getOrNull(it)?.name }
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
            else "A botanical plate. Nothing here dies or resets.",
            color = palette.muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        if (settings.progressStyle == ProgressStyle.GARDEN) {
            if (shelf) {
                Shelf(finishedNames) { shelf = false }
            } else {
                GardenBed(plants, finishedNames.size) { shelf = true }
            }
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
                    plants.size <= 18 -> "${plants.size} bloom${if (plants.size == 1) "" else "s"} from showing up."
                    else -> "$shown on the plate, ${plants.size} remembered. Nothing is thrown away."
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
private fun GardenBed(plants: List<PlantEntity>, shelfCount: Int, onShelf: () -> Unit) {
    var started by remember(plants.size) { mutableLongStateOf(System.nanoTime()) }
    var frame by remember(plants.size) { mutableLongStateOf(started) }
    val growMs = ((plants.size.coerceAtMost(18) - 1).coerceAtLeast(0) * 80f) + 1200f
    LaunchedEffect(plants.size) {
        if (plants.isEmpty()) return@LaunchedEffect
        val origin = System.nanoTime()
        started = origin
        while (isActive) {
            val now = withFrameNanos { frameTime ->
                frame = frameTime
                frameTime
            }
            if ((now - origin) / 1_000_000f > growMs) break
        }
    }
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(paper)
                .semantics {
                    contentDescription = if (plants.isEmpty()) {
                        "Garden plate. Nothing planted yet. A shelf waits in the distance."
                    } else {
                        "Garden plate. ${plants.size} blooms. A shelf waits in the distance."
                    }
                },
        ) {
            val soil = size.height * 0.78f
            drawRect(Color(0xFFE4D3B4), topLeft = Offset(0f, soil), size = androidx.compose.ui.geometry.Size(size.width, size.height - soil))
            drawShelf(Offset(size.width * 0.5f, size.height * 0.18f), shelfCount)
            val elapsed = (frame - started) / 1_000_000f
            plants.takeLast(18).forEachIndexed { index, plant ->
                val h = plant.id.fold(0) { acc, c -> acc * 31 + c.code }
                val x = size.width * (0.1f + (h and 1023) / 1023f * 0.8f)
                val scale = 0.8f + ((h shr 10) and 255) / 255f * 0.45f
                val growth = easeOut(((elapsed - index * 80f) / 1200f).coerceIn(0f, 1f))
                drawPlant(plant.kind, Offset(x, soil), scale, growth, h)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .size(width = 140.dp, height = 64.dp)
                .semantics { contentDescription = "Shelf in the distance. Open it." }
                .clickable(onClick = onShelf),
        )
    }
}

@Composable
private fun Shelf(names: List<String>, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(paper)
            .padding(18.dp),
    ) {
        Text("Back", color = Color(0xFF2C6848), modifier = Modifier.clickable(onClick = onBack).padding(bottom = 8.dp))
        Text("The shelf", color = ink, fontSize = 22.sp)
        if (names.isEmpty()) {
            Text(
                "Nothing standing here yet. Finish a book and it will.",
                color = Color(0xFF5C5148),
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            names.forEach { name ->
                Text(
                    name,
                    color = Color(0xFFF6E7C1),
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF6B4428))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                )
            }
        }
    }
}

private val paper = Color(0xFFF3E6CF)
private val ink = Color(0xFF3C2F29)
private val leaf = Color(0xFF2F6B4F)
private val bloomColors = listOf(
    Color(0xFFC6453A),
    Color(0xFFE07A3D),
    Color(0xFFD45D79),
    Color(0xFF7E4B8A),
    Color(0xFFF2C14E),
    Color(0xFF3E6B8A),
    Color(0xFFF4F1E8),
    Color(0xFF8C3A4B),
)

private fun DrawScope.drawShelf(center: Offset, count: Int) {
    drawLine(Color(0xFF6B4428), Offset(center.x - 58f, center.y), Offset(center.x + 58f, center.y), strokeWidth = 4f)
    val shown = count.coerceAtMost(7)
    if (shown == 0) return
    val left = center.x - shown * 7f
    repeat(shown) { index ->
        drawRect(
            Color(0xFF8C5A32),
            topLeft = Offset(left + index * 14f, center.y - 22f),
            size = androidx.compose.ui.geometry.Size(8f, 22f),
        )
    }
}

private fun easeOut(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x) * (1f - x)
}

private fun DrawScope.drawPlant(kind: Int, base: Offset, scale: Float, growth: Float, hash: Int) {
    if (growth <= 0.01f) return
    val stemT = easeOut((growth / 0.55f).coerceIn(0f, 1f))
    val stem = 52f * scale * stemT
    val lean = ((hash and 15) - 8) * 0.6f * scale
    val top = Offset(base.x + lean, base.y - stem)
    drawLine(leaf, base, top, strokeWidth = 2.2f * scale)
    val leafT = easeOut(((growth - 0.3f) / 0.4f).coerceIn(0f, 1f))
    if (leafT > 0f) {
        val path = Path().apply {
            moveTo(base.x, base.y - stem * 0.45f)
            quadraticTo(base.x - 16f * scale, base.y - stem * 0.7f, base.x - 8f * scale, base.y - stem * 0.35f)
        }
        drawPath(path, leaf.copy(alpha = leafT), style = Stroke(width = 1.6f * scale))
    }
    val bloomT = easeOut(((growth - 0.58f) / 0.42f).coerceIn(0f, 1f))
    if (bloomT <= 0f) return
    val color = bloomColors[kind % bloomColors.size]
    val r = 7f * scale * bloomT
    when (kind % 8) {
        0 -> drawCircle(color, r, top)
        1 -> {
            drawCircle(color, r, Offset(top.x, top.y - r * 0.2f))
            drawCircle(Color(0xFFF6E7C1), r * 0.35f, top)
        }
        2 -> repeat(5) { petal ->
            val angle = petal * 1.256f
            drawCircle(color, r * 0.55f, Offset(top.x + kotlin.math.cos(angle) * r, top.y + kotlin.math.sin(angle) * r))
        }
        3 -> drawOval(color, Offset(top.x - r, top.y - r * 1.4f), androidx.compose.ui.geometry.Size(r * 2f, r * 2.4f))
        4 -> repeat(4) { bell ->
            drawCircle(color, r * 0.45f, Offset(top.x, top.y + bell * r * 0.7f))
        }
        5 -> {
            drawCircle(color, r * 0.9f, top)
            drawCircle(Color(0xFFF2C14E), r * 0.35f, top)
        }
        6 -> drawCircle(color.copy(alpha = 0.9f), r * 1.15f, top)
        else -> repeat(6) { dot ->
            drawCircle(color, r * 0.28f, Offset(top.x + (dot - 2.5f) * r * 0.35f, top.y - dot * r * 0.22f))
        }
    }
    drawCircle(ink.copy(alpha = 0.35f), r * 1.05f, top, style = Stroke(width = 1.1f * scale))
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
