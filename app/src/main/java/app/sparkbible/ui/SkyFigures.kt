package app.sparkbible.ui

import androidx.compose.ui.geometry.Offset

data class SkyPicture(
    val stars: List<SkyStar>,
    val lines: List<Pair<Offset, Offset>>,
    val names: List<String>,
)

data class SkyStar(val at: Offset, val newest: Boolean)

private data class Figure(
    val name: String,
    val points: List<Offset>,
    val edges: List<Pair<Int, Int>>,
)

private data class Cell(val left: Float, val top: Float, val width: Float, val height: Float)

private val figures = listOf(
    Figure(
        "Bloom",
        listOf(
            Offset(0.50f, 0.48f),
            Offset(0.50f, 0.12f),
            Offset(0.84f, 0.32f),
            Offset(0.72f, 0.76f),
            Offset(0.28f, 0.76f),
            Offset(0.16f, 0.32f),
            Offset(0.50f, 0.92f),
        ),
        listOf(0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5, 0 to 6),
    ),
    Figure(
        "Lamp",
        listOf(
            Offset(0.32f, 0.88f),
            Offset(0.68f, 0.88f),
            Offset(0.50f, 0.62f),
            Offset(0.34f, 0.40f),
            Offset(0.66f, 0.40f),
            Offset(0.50f, 0.14f),
        ),
        listOf(0 to 1, 0 to 2, 1 to 2, 2 to 3, 2 to 4, 3 to 4, 3 to 5, 4 to 5),
    ),
    Figure(
        "Bird",
        listOf(
            Offset(0.10f, 0.62f),
            Offset(0.38f, 0.56f),
            Offset(0.58f, 0.42f),
            Offset(0.78f, 0.34f),
            Offset(0.46f, 0.14f),
            Offset(0.64f, 0.74f),
        ),
        listOf(0 to 1, 1 to 2, 2 to 3, 1 to 4, 1 to 5),
    ),
    Figure(
        "Cup",
        listOf(
            Offset(0.16f, 0.26f),
            Offset(0.56f, 0.26f),
            Offset(0.22f, 0.54f),
            Offset(0.36f, 0.80f),
            Offset(0.52f, 0.54f),
            Offset(0.78f, 0.34f),
            Offset(0.76f, 0.64f),
        ),
        listOf(0 to 1, 0 to 2, 2 to 3, 3 to 4, 4 to 1, 1 to 5, 5 to 6, 6 to 4),
    ),
    Figure(
        "Arch",
        listOf(
            Offset(0.20f, 0.88f),
            Offset(0.20f, 0.42f),
            Offset(0.34f, 0.18f),
            Offset(0.50f, 0.10f),
            Offset(0.66f, 0.18f),
            Offset(0.80f, 0.42f),
            Offset(0.80f, 0.88f),
        ),
        listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6),
    ),
    Figure(
        "Path",
        listOf(
            Offset(0.16f, 0.84f),
            Offset(0.32f, 0.64f),
            Offset(0.26f, 0.40f),
            Offset(0.48f, 0.28f),
            Offset(0.70f, 0.36f),
            Offset(0.84f, 0.14f),
        ),
        listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5),
    ),
)

private val cells = listOf(
    Cell(0.04f, 0.06f, 0.44f, 0.42f),
    Cell(0.52f, 0.06f, 0.44f, 0.42f),
    Cell(0.04f, 0.52f, 0.44f, 0.42f),
    Cell(0.52f, 0.52f, 0.44f, 0.42f),
)

fun skyPicture(count: Int, width: Float, height: Float): SkyPicture {
    if (count <= 0 || width <= 0f || height <= 0f) return SkyPicture(emptyList(), emptyList(), emptyList())
    val started = figuresStarted(count)
    val first = (started - cells.size).coerceAtLeast(0)
    val stars = ArrayList<SkyStar>()
    val lines = ArrayList<Pair<Offset, Offset>>()
    val names = ArrayList<String>()
    for (index in first until started) {
        val figure = figures[index % figures.size]
        val earned = (count - starsBefore(index)).coerceIn(0, figure.points.size)
        if (earned <= 0) continue
        names.add(figure.name)
        val cell = cells[(index - first) % cells.size]
        val placed = figure.points.take(earned).map { place(it, cell, width, height) }
        placed.forEachIndexed { starIndex, at ->
            stars.add(SkyStar(at, newest = index == started - 1 && starIndex == earned - 1))
        }
        figure.edges.forEach { (a, b) ->
            if (a < earned && b < earned) lines.add(placed[a] to placed[b])
        }
    }
    return SkyPicture(stars, lines, names)
}

fun skyCaption(count: Int): String {
    if (count <= 0) return "One visit lights the first star."
    val names = skyPicture(count, 1f, 1f).names
    val list = names.joinToString(", ")
    val currentIndex = figuresStarted(count) - 1
    val current = figures[currentIndex % figures.size]
    val into = count - starsBefore(currentIndex)
    val forming = into < current.points.size
    return if (forming) "$list. The ${current.name.lowercase()} is still forming."
    else "$list. That shape is whole. The next visit starts another."
}

private fun figuresStarted(count: Int): Int {
    var left = count
    var index = 0
    while (left > 0) {
        left -= figures[index % figures.size].points.size
        index += 1
    }
    return index
}

private fun starsBefore(index: Int): Int {
    var sum = 0
    for (i in 0 until index) sum += figures[i % figures.size].points.size
    return sum
}

private fun place(point: Offset, cell: Cell, width: Float, height: Float): Offset {
    val pad = 0.1f
    return Offset(
        (cell.left + (pad + point.x * (1f - pad * 2)) * cell.width) * width,
        (cell.top + (pad + point.y * (1f - pad * 2)) * cell.height) * height,
    )
}
