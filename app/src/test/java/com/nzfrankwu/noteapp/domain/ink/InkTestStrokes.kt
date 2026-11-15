package com.nzfrankwu.noteapp.domain.ink

/** A stroke through the given (x, y) points, for tests. */
fun testStroke(
    id: String,
    vararg points: Pair<Float, Float>,
    size: Float = 2f,
    tool: InkTool = InkTool.PEN,
    withPressure: Boolean = true,
): InkStroke {
    val n = points.size
    return InkStroke(
        id = id,
        tool = tool,
        colorArgb = 0xFF1A237E.toInt(),
        size = size,
        xs = FloatArray(n) { points[it].first },
        ys = FloatArray(n) { points[it].second },
        times = IntArray(n) { it * 8 },
        pressure = FloatArray(n) { if (withPressure) 0.3f + it * 0.01f else InkStroke.NONE },
        tilt = FloatArray(n) { InkStroke.NONE },
        orientation = FloatArray(n) { InkStroke.NONE },
    )
}
