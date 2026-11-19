package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** PHASE-7.md 7.14: recognising hand-drawn shapes, and editing lines and shapes. */
class ShapesTest {
    /** A hand-drawn path through [corners] (closed back to the first), with a little wobble. */
    private fun hand(vararg corners: Pair<Float, Float>, startMidSide: Boolean = false): Pair<FloatArray, FloatArray> {
        val path = corners.map { Node(it.first, it.second) }.toMutableList()
        if (startMidSide) {
            val a = path[0]; val b = path[1]
            path.add(0, Node((a.x + b.x) / 2, (a.y + b.y) / 2)); path.removeAt(1); path.add(a)
        }
        path += path[0]
        val (xs, ys) = Shapes.along(path, 2f)
        for (i in xs.indices) { xs[i] += 1.2f * sin(i * 0.7f); ys[i] += 1.2f * cos(i * 0.9f) }
        return xs to ys
    }

    private fun handEllipse(cx: Float, cy: Float, rx: Float, ry: Float): Pair<FloatArray, FloatArray> {
        val n = 90
        val xs = FloatArray(n + 1) { (cx + (rx + 1.5f * sin(it * 0.8f)) * cos(2 * PI * it / n)).toFloat() }
        val ys = FloatArray(n + 1) { (cy + (ry + 1.5f * cos(it * 0.6f)) * sin(2 * PI * it / n)).toFloat() }
        return xs to ys
    }

    private fun near(a: Float, b: Float, tol: Float = 0.5f) = assertTrue("$a vs $b", abs(a - b) <= tol)

    // ---- Recognising ----

    @Test
    fun aRoughSquare_becomesAPerfectSquare_evenStartedMidSide() {
        val (xs, ys) = hand(100f to 100f, 205f to 102f, 203f to 198f, 99f to 196f, startMidSide = true)
        val shape = Shapes.recognize(xs, ys) as Shape.Polygon
        assertTrue(shape.isRect)
        val box = Shapes.box(shape)
        near(box[2] - box[0], box[3] - box[1], 0.01f)
    }

    @Test
    fun aWideRectangle_staysARectangle() {
        val (xs, ys) = hand(100f to 100f, 260f to 100f, 260f to 160f, 100f to 160f)
        val shape = Shapes.recognize(xs, ys) as Shape.Polygon
        assertTrue(shape.isRect)
        val box = Shapes.box(shape)
        assertTrue(box[2] - box[0] > 2 * (box[3] - box[1]))
    }

    @Test
    fun aRoughCircle_becomesACircle_andAnOval_anEllipse() {
        val circle = Shapes.recognize(handEllipse(200f, 200f, 60f, 57f).first, handEllipse(200f, 200f, 60f, 57f).second) as Shape.Ellipse
        assertEquals(circle.rx, circle.ry)
        val (xs, ys) = handEllipse(200f, 200f, 90f, 45f)
        val oval = Shapes.recognize(xs, ys) as Shape.Ellipse
        assertTrue(oval.rx > oval.ry * 1.5f)
    }

    @Test
    fun aTriangle_keepsItsCorners_withStraightSides() {
        val (xs, ys) = hand(150f to 100f, 220f to 210f, 80f to 205f)
        val shape = Shapes.recognize(xs, ys) as Shape.Polygon
        assertEquals(3, shape.corners.size)
        assertTrue(!shape.isRect)
    }

    @Test
    fun openStrokes_lines_andSmallLetters_areNotShapes() {
        // An open zigzag.
        val (zx, zy) = Shapes.along(listOf(Node(0f, 0f), Node(50f, 40f), Node(100f, 0f), Node(150f, 40f)), 2f)
        assertNull(Shapes.recognize(zx, zy))
        // A small "o" (6 units across): writing, not a circle.
        val (ox, oy) = handEllipse(50f, 50f, 6f, 7f)
        assertNull(Shapes.recognize(ox, oy))
        // Too few points.
        assertNull(Shapes.recognize(floatArrayOf(0f, 50f, 0f), floatArrayOf(0f, 50f, 0f)))
    }

    // ---- Lassoed strokes ----

    private fun strokeOf(shape: Shape) = Shapes.stroke(shape, "s", InkTool.PEN_PRESSURE_FULL_V2, 0, 2f, 0.5f, InkStroke.TOOL_STYLUS)

    @Test
    fun snappedShapes_areFoundAgain_fromTheirStrokes() {
        for (shape in listOf(
            Shape.Line(10f, 20f, 200f, 90f),
            Shapes.rect(50f, 50f, 150f, 120f),
            Shape.Polygon(listOf(Node(150f, 100f), Node(220f, 210f), Node(80f, 205f))),
            Shape.Ellipse(200f, 200f, 60f, 60f),
            Shape.Ellipse(200f, 200f, 90f, 37f),
        )) {
            val s = strokeOf(shape)
            val found = Shapes.fromStroke(s.xs, s.ys)
            assertEquals(shape::class, found!!::class)
            assertEquals(shape.nodes.size, found.nodes.size)
            for ((a, b) in shape.nodes.zip(found.nodes)) { near(a.x, b.x); near(a.y, b.y) }
        }
    }

    @Test
    fun handwriting_isNotAnEditableShape() {
        val (xs, ys) = hand(100f to 100f, 205f to 102f, 203f to 198f, 99f to 196f)
        assertNull(Shapes.fromStroke(xs, ys))
        val (wx, wy) = Shapes.along(listOf(Node(0f, 0f), Node(10f, 15f), Node(20f, 0f), Node(30f, 15f)), 2f)
        assertNull(Shapes.fromStroke(wx, wy))
    }

    @Test
    fun aMovedOrResizedShape_isStillFound() {
        // The lasso moves and stretches strokes point by point (LassoBox.map).
        val s = strokeOf(Shapes.rect(50f, 50f, 150f, 120f))
        val moved = s.mapped(1.5f, 0f, 0f, 30f, -10f)
        assertTrue(Shapes.fromStroke(moved.xs, moved.ys) is Shape.Polygon)
    }

    /** 19 Nov: an ellipse turned with the lasso's turn handle (point by point, LassoBox.rotatePoint) stays editable. */
    @Test
    fun aTurnedEllipse_isStillFound_withItsAngle() {
        val s = strokeOf(Shape.Ellipse(200f, 150f, 90f, 40f))
        val angle = 0.6f
        val xs = FloatArray(s.xs.size); val ys = FloatArray(s.ys.size)
        for (i in s.xs.indices) {
            val (x, y) = LassoBox.rotatePoint(s.xs[i], s.ys[i], 230f, 170f, angle)
            xs[i] = x; ys[i] = y
        }
        val found = Shapes.fromStroke(xs, ys) as Shape.Ellipse
        near(found.rx, 90f); near(found.ry, 40f)
        near(found.angle, angle, 0.01f)
        val (cx, cy) = LassoBox.rotatePoint(200f, 150f, 230f, 170f, angle)
        near(found.cx, cx); near(found.cy, cy)
        // Its points turned with it: the right end is along the turned long axis.
        val right = found.nodes[1]
        near(right.x, cx + 90f * kotlin.math.cos(angle)); near(right.y, cy + 90f * kotlin.math.sin(angle))
    }

    @Test
    fun anEllipseTurnedByTheLasso_isStillFound() {
        // As the lasso does it: LassoBox.map with the selection's box and an angle.
        for (angle in listOf(0.3f, 0.87f, 1.2f, -0.5f, 2.6f)) {
            val s = strokeOf(Shape.Ellipse(612.4f, 803.7f, 121.3f, 49.9f))
            val box = s.bounds()
            val turned = LassoBox.map(s, box, box, angle)
            val found = Shapes.fromStroke(turned.xs, turned.ys)
            assertTrue("angle $angle: $found", found is Shape.Ellipse)
        }
    }

    @Test
    fun draggingATurnedEllipsesSide_keepsTheOppositeSide() {
        val e = Shape.Ellipse(100f, 100f, 50f, 30f, angle = (Math.PI / 2).toFloat()) // long axis upright
        val left = e.nodes[3] // its own left end, now at the top
        near(left.x, 100f); near(left.y, 50f)
        // Drag its own right end (at the bottom, y = 150) down to y = 190.
        val longer = Shapes.moveNode(e, 1, 100f, 190f) as Shape.Ellipse
        near(longer.rx, 70f); near(longer.ry, 30f)
        near(longer.nodes[3].x, 100f); near(longer.nodes[3].y, 50f) // the far end stayed
        near(longer.nodes[1].y, 190f)
        // And its box is upright too.
        val box = Shapes.box(longer)
        near(box[0], 70f); near(box[2], 130f); near(box[1], 50f); near(box[3], 190f)
    }

    @Test
    fun handDrawnLooking_turnedOvals_areNotTakenForShapes() {
        // A hand-drawn tilted oval (not snapped) isn't exact enough to be an editable shape.
        val (hx, hy) = handEllipse(150f, 150f, 80f, 35f)
        val xs = FloatArray(hx.size); val ys = FloatArray(hy.size)
        for (i in hx.indices) {
            val (x, y) = LassoBox.rotatePoint(hx[i], hy[i], 150f, 150f, 0.5f)
            xs[i] = x; ys[i] = y
        }
        assertNull(Shapes.fromStroke(xs, ys))
    }

    // ---- Editing ----

    @Test
    fun aLinesEnd_moves_andSnapsTo45Degrees() {
        val line = Shape.Line(0f, 0f, 100f, 0f)
        val moved = Shapes.moveNode(line, 1, 100f, 102f) as Shape.Line
        assertEquals(0f, moved.x1); assertEquals(0f, moved.y1)
        near(moved.x2, moved.y2, 0.01f) // pulled onto 45°
        val start = Shapes.moveNode(line, 0, 20f, 50f) as Shape.Line
        assertEquals(100f, start.x2)
        near(start.x1, 20f); near(start.y1, 50f)
    }

    @Test
    fun aRectanglesCorner_moves_withTheOppositeCornerStaying() {
        val rect = Shapes.rect(0f, 0f, 100f, 50f)
        val moved = Shapes.moveNode(rect, 2, 160f, 90f) as Shape.Polygon // bottom right
        assertTrue(moved.isRect)
        assertEquals(listOf(0f, 0f, 160f, 90f), Shapes.box(moved).toList())
        // Nearly square: snaps square.
        val square = Shapes.moveNode(rect, 2, 100f, 97f) as Shape.Polygon
        val box = Shapes.box(square)
        assertEquals(box[2] - box[0], box[3] - box[1])
        // Dragged past the opposite corner: it flips, and the corner held is found again.
        val flipped = Shapes.moveNode(rect, 2, -40f, -30f)
        assertEquals(listOf(-40f, -30f, 0f, 0f), Shapes.box(flipped).toList())
        assertEquals(0, Shapes.nearestNode(flipped, -40f, -30f))
    }

    @Test
    fun aTrianglesCorner_movesAlone() {
        val tri = Shape.Polygon(listOf(Node(0f, 0f), Node(100f, 0f), Node(50f, 80f)))
        val moved = Shapes.moveNode(tri, 2, 60f, 120f) as Shape.Polygon
        assertEquals(listOf(Node(0f, 0f), Node(100f, 0f), Node(60f, 120f)), moved.corners)
    }

    @Test
    fun anEllipsesSide_moves_withTheOppositeSideStaying() {
        val e = Shape.Ellipse(100f, 100f, 50f, 30f)
        val wider = Shapes.moveNode(e, 1, 190f, 100f) as Shape.Ellipse // right side
        near(wider.cx - wider.rx, 50f, 0.01f) // the left side stayed
        near(wider.cx + wider.rx, 190f, 0.01f)
        assertEquals(30f, wider.ry)
    }

    @Test
    fun aJustSnappedShape_resizesFromTheCornerThePenStoppedAt() {
        val square = Shapes.rect(100f, 100f, 200f, 200f)
        // The pen stopped near the bottom-right corner, 3 units off it.
        val grip = Shapes.grip(square, 197f, 198f)
        assertEquals(Node(100f, 100f), grip.anchor)
        val bigger = Shapes.drag(square, grip, 247f, 298f) // the corner follows, keeping the 3-unit offset
        assertEquals(listOf(100f, 100f, 250f, 300f), Shapes.box(bigger).toList())
        // Pulled nearly square: stays a square.
        val even = Shapes.drag(square, grip, 287f, 296f)
        val box = Shapes.box(even)
        assertEquals(box[2] - box[0], box[3] - box[1])
        // A circle grows from the opposite corner of its box.
        val circle = Shape.Ellipse(150f, 150f, 50f, 50f)
        val g = Shapes.grip(circle, 115f, 112f) // near the top-left
        val grown = Shapes.drag(circle, g, 15f, 12f) as Shape.Ellipse
        near(grown.cx + grown.rx, 200f, 0.01f); near(grown.cy + grown.ry, 200f, 0.01f)
        assertEquals(grown.rx, grown.ry)
        // A triangle: only the corner held moves.
        val tri = Shape.Polygon(listOf(Node(0f, 0f), Node(100f, 0f), Node(50f, 80f)))
        val tg = Shapes.grip(tri, 2f, 1f)
        // (The pen held it 2 left and 1 up of the corner: the corner stays that far from the pen.)
        assertEquals(listOf(Node(-22f, -11f), Node(100f, 0f), Node(50f, 80f)), (Shapes.drag(tri, tg, -20f, -10f) as Shape.Polygon).corners)
    }

    @Test
    fun shapeOutlines_close_andKeepTheirPointsClose() {
        for (shape in listOf(Shapes.rect(0f, 0f, 100f, 40f), Shape.Ellipse(0f, 0f, 70f, 30f))) {
            val (xs, ys) = shape.outline()
            assertEquals(xs.first(), xs.last(), 0.001f)
            assertEquals(ys.first(), ys.last(), 0.001f)
            // An ellipse's points are evenly spread by angle, so a little further apart at its ends.
            val most = if (shape is Shape.Ellipse) Shapes.POINT_SPACING * 2.5f else Shapes.POINT_SPACING + 0.01f
            for (i in 1 until xs.size) assertTrue(kotlin.math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]) <= most)
        }
    }
}
