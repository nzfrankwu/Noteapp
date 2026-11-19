package com.nzfrankwu.noteapp.domain.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A point (desk or page units). */
data class Node(val x: Float, val y: Float)

/**
 * A clean line or shape (PHASE-7.md 7.14), all in one coordinate space (desk or page units).
 *
 * Shapes aren't stored as anything special: a snapped shape is saved as an ordinary stroke along
 * its [outline], and [Shapes.fromStroke] finds the shape again when it's lassoed. So the page file
 * format doesn't change.
 */
sealed interface Shape {
    /** The points you can drag: a line's ends, a polygon's corners, an ellipse's top, right, bottom and left. */
    val nodes: List<Node>

    /** The outline, points at most [spacing] apart. A closed shape ends where it starts. */
    fun outline(spacing: Float = Shapes.POINT_SPACING): Pair<FloatArray, FloatArray>

    fun translated(dx: Float, dy: Float): Shape

    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Shape {
        override val nodes get() = listOf(Node(x1, y1), Node(x2, y2))
        override fun outline(spacing: Float) = Shapes.along(listOf(Node(x1, y1), Node(x2, y2)), spacing)
        override fun translated(dx: Float, dy: Float) = Line(x1 + dx, y1 + dy, x2 + dx, y2 + dy)
    }

    /** A closed polygon (corners in drawing order, the first not repeated at the end). */
    data class Polygon(val corners: List<Node>) : Shape {
        override val nodes get() = corners
        // The pen smooths what it draws, which rounds sharp corners: the stroke waits a moment
        // at each corner (the same point a few times) so the corners stay sharp.
        override fun outline(spacing: Float) = Shapes.along(corners + corners.first(), spacing, cornerHold = Shapes.CORNER_HOLD)
        override fun translated(dx: Float, dy: Float) = Polygon(corners.map { Node(it.x + dx, it.y + dy) })

        /** Four corners with level and upright sides: resizing keeps it a rectangle. */
        val isRect: Boolean
            get() = corners.size == 4 && (0 until 4).all { i ->
                val a = corners[i]; val b = corners[(i + 1) % 4]
                abs(a.x - b.x) <= Shapes.EXACT || abs(a.y - b.y) <= Shapes.EXACT
            }
    }

    /**
     * An ellipse around ([cx], [cy]) with radii [rx] and [ry] along its own axes, turned by
     * [angle] (radians, clockwise on screen; 0 = level). A level one is what snapping makes;
     * the lasso's turn handle turns it (19 Nov).
     */
    data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val angle: Float = 0f) : Shape {
        /** Its own top, right, bottom and left (turned with it). */
        override val nodes get() = listOf(at(0f, -ry), at(rx, 0f), at(0f, ry), at(-rx, 0f))

        /** The point [u] along its own x axis and [v] along its y axis from the centre. */
        fun at(u: Float, v: Float): Node {
            val c = cos(angle); val s = sin(angle)
            return Node(cx + u * c - v * s, cy + u * s + v * c)
        }

        override fun outline(spacing: Float): Pair<FloatArray, FloatArray> {
            // Ramanujan's perimeter, roughly: enough points for a smooth curve.
            val perimeter = PI * (3 * (rx + ry) - sqrt((3 * rx + ry) * (rx + 3 * ry).toDouble()))
            // A multiple of 4, so its top, right, bottom and left are points of the stroke (then
            // the stroke's box is exactly the ellipse's, and it's found again when lassoed).
            val n = ((perimeter / spacing).toInt().coerceIn(Shapes.MIN_ELLIPSE_POINTS, Shapes.MAX_POINTS - 4) + 3) / 4 * 4
            val xs = FloatArray(n + 1)
            val ys = FloatArray(n + 1)
            for (i in 0..n) {
                // From the top, clockwise; the last point is the first again.
                val a = -PI / 2 + 2 * PI * (i % n) / n
                val p = at((rx * cos(a)).toFloat(), (ry * sin(a)).toFloat())
                xs[i] = p.x
                ys[i] = p.y
            }
            return xs to ys
        }
        override fun translated(dx: Float, dy: Float) = copy(cx = cx + dx, cy = cy + dy)
    }
}

/**
 * Shape recognition and editing (PHASE-7.md 7.14). Plain Kotlin, unit-tested.
 *
 * - [recognize]: what a hand-drawn closed stroke looks like: a rectangle (a square when nearly
 *   so), an ellipse (a circle when nearly so) or a triangle; null if none.
 * - [grip] and [drag]: resizing a just-snapped shape from the point the pen stopped at.
 * - [fromStroke] and [moveNode]: a lassoed line or snapped shape, and dragging its points.
 */
object Shapes {
    /** Page units between the points of a shape's stroke (like [Straighten.line]'s). */
    const val POINT_SPACING = 4f

    /** Shapes smaller than this (page units, about 8 mm) aren't snapped: letters like "o" stay. */
    const val MIN_SIZE = 24f

    /** Sides within this share of each other make a square (or radii a circle). */
    const val EVEN = 0.12f

    /** While resizing, sides within this share of each other snap to a square (or circle). */
    const val EVEN_DRAG = 0.06f

    /** How close (in page units) a stroke's points must be to count as an exact line or shape. */
    const val EXACT = 0.3f

    const val MIN_ELLIPSE_POINTS = 24
    const val MAX_POINTS = 600

    // ---- Recognising a hand-drawn shape ----

    /** The clean shape a hand-drawn stroke ([xs], [ys]) is closest to, or null if it isn't a closed shape. */
    fun recognize(xs: FloatArray, ys: FloatArray): Shape? {
        val n = min(xs.size, ys.size)
        if (n < 8) return null
        val left = xs.min(); val right = xs.max(); val top = ys.min(); val bottom = ys.max()
        val w = right - left
        val h = bottom - top
        val size = max(w, h)
        if (size < MIN_SIZE || min(w, h) < size * 0.15f) return null // too small, or a flat squiggle
        // Closed: it ends near where it started.
        if (hypot(xs[n - 1] - xs[0], ys[n - 1] - ys[0]) > size * 0.3f) return null

        val corners = corners(xs, ys, n, size)
        val roundness = ellipseError(xs, ys, n, (left + right) / 2, (top + bottom) / 2, w / 2, h / 2)
        return when {
            corners.size == 3 && roundness > ROUND -> Shape.Polygon(corners)
            corners.size == 4 && roundness > ROUND -> rectangle(corners)
            roundness <= ELLIPSE_FIT -> ellipse((left + right) / 2, (top + bottom) / 2, w / 2, h / 2)
            else -> null
        }
    }

    /** Mean distance from the unit ellipse (0 for a perfect one; a square's sides average about 0.15). */
    private fun ellipseError(xs: FloatArray, ys: FloatArray, n: Int, cx: Float, cy: Float, rx: Float, ry: Float): Float {
        var sum = 0.0
        for (i in 0 until n) {
            val nx = (xs[i] - cx) / rx
            val ny = (ys[i] - cy) / ry
            sum += abs(sqrt((nx * nx + ny * ny).toDouble()) - 1.0)
        }
        return (sum / n).toFloat()
    }

    /** Below this ellipse error a 3- or 4-corner stroke is really round (a wobbly circle). */
    private const val ROUND = 0.07f

    /** Up to this ellipse error a stroke without 3 or 4 clear corners is an ellipse. */
    private const val ELLIPSE_FIT = 0.12f

    /** The clear corners of a closed hand-drawn stroke, in drawing order. */
    private fun corners(xs: FloatArray, ys: FloatArray, n: Int, size: Float): List<Node> {
        val points = List(n) { Node(xs[it], ys[it]) }
        val kept = simplify(points, size * 0.08f).toMutableList()
        if (kept.size > 1 && hypot(kept.last().x - kept.first().x, kept.last().y - kept.first().y) < size * 0.3f) kept.removeAt(kept.size - 1)
        // Drop points where the outline hardly turns (e.g. where the pen started mid-side).
        var changed = true
        while (changed && kept.size > 2) {
            changed = false
            for (i in kept.indices) {
                val a = kept[(i - 1 + kept.size) % kept.size]; val b = kept[i]; val c = kept[(i + 1) % kept.size]
                if (turn(a, b, c) < MIN_TURN_DEGREES) { kept.removeAt(i); changed = true; break }
            }
        }
        return kept
    }

    private const val MIN_TURN_DEGREES = 35.0

    /** How sharply the outline turns at [b] (0 = straight on), in degrees. */
    private fun turn(a: Node, b: Node, c: Node): Double {
        val a1 = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
        val a2 = atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble())
        var d = Math.toDegrees(a2 - a1)
        while (d > 180) d -= 360
        while (d < -180) d += 360
        return abs(d)
    }

    /** Ramer–Douglas–Peucker: the points needed to keep the line within [epsilon] of [points]. */
    fun simplify(points: List<Node>, epsilon: Float): List<Node> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true; keep[points.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.size - 1)
        while (stack.isNotEmpty()) {
            val (from, to) = stack.removeLast()
            var far = -1
            var farDist = epsilon
            for (i in from + 1 until to) {
                val d = distanceToSegment(points[i], points[from], points[to])
                if (d > farDist) { farDist = d; far = i }
            }
            if (far >= 0) {
                keep[far] = true
                stack.addLast(from to far)
                stack.addLast(far to to)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun distanceToSegment(p: Node, a: Node, b: Node): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-9f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /**
     * Four hand-drawn corners: a level rectangle (a square when nearly so) if its sides are near
     * level and upright, else the four corners joined by straight sides.
     */
    private fun rectangle(corners: List<Node>): Shape.Polygon {
        val level = (0 until 4).all { i ->
            val a = corners[i]; val b = corners[(i + 1) % 4]
            val angle = Math.toDegrees(atan2(abs(b.y - a.y).toDouble(), abs(b.x - a.x).toDouble()))
            angle <= LEVEL_DEGREES || angle >= 90 - LEVEL_DEGREES
        }
        if (!level) return Shape.Polygon(corners)
        var left = corners.minOf { it.x }; var right = corners.maxOf { it.x }
        var top = corners.minOf { it.y }; var bottom = corners.maxOf { it.y }
        val w = right - left; val h = bottom - top
        if (abs(w - h) <= EVEN * max(w, h)) {
            // A square, the same size around the same middle.
            val side = (w + h) / 2
            val cx = (left + right) / 2; val cy = (top + bottom) / 2
            left = cx - side / 2; right = cx + side / 2; top = cy - side / 2; bottom = cy + side / 2
        }
        return rect(left, top, right, bottom)
    }

    private const val LEVEL_DEGREES = 15.0

    /** A level rectangle, corners clockwise from the top left. */
    fun rect(left: Float, top: Float, right: Float, bottom: Float) = Shape.Polygon(
        listOf(Node(min(left, right), min(top, bottom)), Node(max(left, right), min(top, bottom)), Node(max(left, right), max(top, bottom)), Node(min(left, right), max(top, bottom))),
    )

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): Shape.Ellipse =
        if (abs(rx - ry) <= EVEN * max(rx, ry)) ((rx + ry) / 2).let { Shape.Ellipse(cx, cy, it, it) } else Shape.Ellipse(cx, cy, rx, ry)

    // ---- Resizing a just-snapped shape with the pen still down ----

    /**
     * What the pen holds after a shape snaps at ([x], [y]): a level rectangle's or an ellipse's
     * nearest box corner (the opposite one stays), or another polygon's nearest corner (only it
     * moves). [dx], [dy]: from the pen to that point, so it doesn't jump.
     */
    data class Grip(val anchor: Node?, val node: Int, val dx: Float, val dy: Float, val keepEven: Boolean)

    fun grip(shape: Shape, x: Float, y: Float): Grip = when {
        shape is Shape.Ellipse || (shape is Shape.Polygon && shape.isRect) -> {
            val box = box(shape)
            val boxCorners = listOf(Node(box[0], box[1]), Node(box[2], box[1]), Node(box[2], box[3]), Node(box[0], box[3]))
            val i = boxCorners.indices.minBy { hypot(boxCorners[it].x - x, boxCorners[it].y - y) }
            Grip(boxCorners[(i + 2) % 4], i, boxCorners[i].x - x, boxCorners[i].y - y, keepEven = true)
        }
        else -> {
            val nodes = shape.nodes
            val i = nodes.indices.minBy { hypot(nodes[it].x - x, nodes[it].y - y) }
            Grip(null, i, nodes[i].x - x, nodes[i].y - y, keepEven = false)
        }
    }

    /**
     * The shape with its gripped point moved to follow the pen at ([x], [y]). A square or circle
     * stays even while the new size is nearly so (like a line snapping to 45°).
     */
    fun drag(shape: Shape, grip: Grip, x: Float, y: Float): Shape {
        val px = x + grip.dx
        val py = y + grip.dy
        val anchor = grip.anchor ?: return moveNode(shape, grip.node, px, py)
        var w = abs(px - anchor.x); var h = abs(py - anchor.y)
        if (grip.keepEven && abs(w - h) <= EVEN_DRAG * max(w, h)) { w = max(w, h); h = w }
        val right = anchor.x + if (px >= anchor.x) w else -w
        val bottom = anchor.y + if (py >= anchor.y) h else -h
        return when (shape) {
            is Shape.Ellipse -> Shape.Ellipse((anchor.x + right) / 2, (anchor.y + bottom) / 2, max(w / 2, 1f), max(h / 2, 1f))
            else -> rect(anchor.x, anchor.y, right, bottom)
        }
    }

    /** [left, top, right, bottom] of a shape. */
    fun box(shape: Shape): FloatArray = when (shape) {
        is Shape.Ellipse -> {
            // A turned ellipse's box reaches out by these half-widths.
            val c = cos(shape.angle); val s = sin(shape.angle)
            val hw = hypot(shape.rx * c, shape.ry * s); val hh = hypot(shape.rx * s, shape.ry * c)
            floatArrayOf(shape.cx - hw, shape.cy - hh, shape.cx + hw, shape.cy + hh)
        }
        else -> shape.nodes.let { p -> floatArrayOf(p.minOf { it.x }, p.minOf { it.y }, p.maxOf { it.x }, p.maxOf { it.y }) }
    }

    // ---- A lassoed line or shape ----

    /**
     * The line or shape a stroke was made as (a snapped line or shape: its points lie exactly on
     * it), or null for ordinary writing.
     */
    fun fromStroke(xs: FloatArray, ys: FloatArray): Shape? {
        val n = min(xs.size, ys.size)
        if (n < 3) return null
        val points = List(n) { Node(xs[it], ys[it]) }
        val first = points.first(); val last = points.last()
        val chord = hypot(last.x - first.x, last.y - first.y)
        // A straight line: every point on the line from its first to its last.
        if (chord >= 2f && points.all { distanceToSegment(it, first, last) <= EXACT }) return Shape.Line(first.x, first.y, last.x, last.y)
        if (chord > EXACT * 2) return null // open, and not straight
        // An ellipse: every point on the ellipse filling its box.
        val left = xs.min(); val right = xs.max(); val top = ys.min(); val bottom = ys.max()
        val rx = (right - left) / 2; val ry = (bottom - top) / 2
        if (n >= MIN_ELLIPSE_POINTS && rx > 1f && ry > 1f) {
            val cx = (left + right) / 2; val cy = (top + bottom) / 2
            val onIt = points.all { p ->
                val nx = (p.x - cx) / rx; val ny = (p.y - cy) / ry
                abs(sqrt(nx * nx + ny * ny) - 1f) * min(rx, ry) <= EXACT * 2
            }
            if (onIt) return Shape.Ellipse(cx, cy, rx, ry)
            turnedEllipse(points)?.let { return it }
        }
        // A polygon: a few straight sides.
        val corners = simplify(points, EXACT).dropLast(1).toMutableList()
        // The start may lie mid-side (after editing): drop corners that don't turn.
        var i = 0
        while (corners.size > 3 && i < corners.size) {
            val a = corners[(i - 1 + corners.size) % corners.size]; val c = corners[(i + 1) % corners.size]
            if (distanceToSegment(corners[i], a, c) <= EXACT) corners.removeAt(i) else i++
        }
        return if (corners.size in 3..MAX_CORNERS) Shape.Polygon(corners) else null
    }

    private const val MAX_CORNERS = 8

    /**
     * A snapped ellipse turned with the lasso (19 Nov): its points are still the evenly spread
     * points of an ellipse, just turned. Their average is the centre and their spread gives the
     * axes; then every point must lie exactly on that ellipse. Null if they don't.
     */
    private fun turnedEllipse(points: List<Node>): Shape.Ellipse? {
        val ring = points.dropLast(1) // the last point repeats the first
        val n = ring.size
        val cx = ring.sumOf { it.x.toDouble() } / n
        val cy = ring.sumOf { it.y.toDouble() } / n
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (p in ring) {
            val dx = p.x - cx; val dy = p.y - cy
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        val angle = 0.5 * atan2(2 * sxy, sxx - syy)
        val c = cos(angle); val s = sin(angle)
        var rx = 0.0; var ry = 0.0
        for (p in ring) {
            val dx = p.x - cx; val dy = p.y - cy
            rx = max(rx, abs(dx * c + dy * s)); ry = max(ry, abs(-dx * s + dy * c))
        }
        if (rx <= 1.0 || ry <= 1.0) return null
        val onIt = points.all { p ->
            val dx = p.x - cx; val dy = p.y - cy
            val u = (dx * c + dy * s) / rx; val v = (-dx * s + dy * c) / ry
            abs(sqrt(u * u + v * v) - 1.0) * min(rx, ry) <= EXACT * 2
        }
        return if (onIt) Shape.Ellipse(cx.toFloat(), cy.toFloat(), rx.toFloat(), ry.toFloat(), angle.toFloat()) else null
    }

    /**
     * The shape with node [index] dragged to ([x], [y]): a line's end (snapping to 45° like F11);
     * a level rectangle's corner (the opposite corner stays, a square stays square while nearly
     * so); any other polygon's corner alone; an ellipse's side (the opposite side stays).
     */
    fun moveNode(shape: Shape, index: Int, x: Float, y: Float): Shape = when (shape) {
        is Shape.Line -> if (index == 0) {
            val (sx, sy) = Straighten.snappedEnd(shape.x2, shape.y2, x, y)
            Shape.Line(sx, sy, shape.x2, shape.y2)
        } else {
            val (ex, ey) = Straighten.snappedEnd(shape.x1, shape.y1, x, y)
            Shape.Line(shape.x1, shape.y1, ex, ey)
        }
        is Shape.Polygon -> if (shape.isRect) {
            drag(shape, Grip(shape.corners[(index + 2) % 4], index, 0f, 0f, keepEven = true), x, y)
        } else {
            Shape.Polygon(shape.corners.mapIndexed { i, p -> if (i == index) Node(x, y) else p })
        }
        is Shape.Ellipse -> {
            // In the ellipse's own (maybe turned) frame: u along its x axis, v along its y axis.
            val c = cos(shape.angle); val s = sin(shape.angle)
            val u = (x - shape.cx) * c + (y - shape.cy) * s
            val v = -(x - shape.cx) * s + (y - shape.cy) * c
            var top = -shape.ry; var bottom = shape.ry
            var left = -shape.rx; var right = shape.rx
            when (index) {
                0 -> top = min(v, bottom - 2f)
                1 -> right = max(u, left + 2f)
                2 -> bottom = max(v, top + 2f)
                else -> left = min(u, right - 2f)
            }
            val mid = shape.at((left + right) / 2, (top + bottom) / 2)
            Shape.Ellipse(mid.x, mid.y, (right - left) / 2, (bottom - top) / 2, shape.angle)
        }
    }

    /** The index of [shape]'s node a dragged node ends up as (a rectangle's corners swap when crossed). */
    fun nearestNode(shape: Shape, x: Float, y: Float): Int =
        shape.nodes.indices.minBy { hypot(shape.nodes[it].x - x, shape.nodes[it].y - y) }

    // ---- Making the stroke ----

    /**
     * Points at most [spacing] apart along the corners [path] (a closed path repeats its first
     * corner). Each corner where the path turns is repeated [cornerHold] more times.
     */
    fun along(path: List<Node>, spacing: Float, cornerHold: Int = 0): Pair<FloatArray, FloatArray> {
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        xs += path[0].x; ys += path[0].y
        for (k in 1 until path.size) {
            val a = path[k - 1]; val b = path[k]
            val steps = ceil(hypot(b.x - a.x, b.y - a.y) / spacing).toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                xs += a.x + (b.x - a.x) * s / steps
                ys += a.y + (b.y - a.y) * s / steps
            }
            if (k < path.size - 1) repeat(cornerHold) { xs += b.x; ys += b.y }
        }
        return xs.toFloatArray() to ys.toFloatArray()
    }

    /** How many times a polygon's stroke repeats each corner (see [Shape.Polygon.outline]). */
    const val CORNER_HOLD = 6

    /**
     * A stroke drawn along [shape] (page units), with an even [pressure] (or [InkStroke.NONE]) so
     * it draws like the pen, as [Straighten.line] does.
     */
    fun stroke(shape: Shape, id: String, tool: InkTool, colorArgb: Int, size: Float, pressure: Float, inputTool: Int): InkStroke {
        val (xs, ys) = shape.outline()
        val n = xs.size
        return InkStroke(
            id, tool, colorArgb, size, xs, ys,
            IntArray(n) { it * 4 }, FloatArray(n) { pressure },
            FloatArray(n) { InkStroke.NONE }, FloatArray(n) { InkStroke.NONE }, inputTool,
        )
    }
}
