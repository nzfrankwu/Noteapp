package com.nzfrankwu.noteapp.domain.ink

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

/**
 * A finger flick that keeps the pages moving after the finger lifts (PHASE-2.md F6), slowing
 * down smoothly: speed falls by a fixed share every second (exponential decay), so a fast flick
 * travels several pages and a gentle one barely coasts. Speeds are in pixels per second.
 */
object Fling {
    /** How quickly a flick slows down (per second). Lower = travels further. */
    const val FRICTION = 2.0f

    /** Releases slower than this just stop (a careful drag shouldn't drift). */
    const val MIN_START_SPEED = 350f

    /** The flick ends once it's this slow. */
    const val STOP_SPEED = 30f

    /** How far a flick at [velocity] moves during [seconds]. */
    fun distance(velocity: Float, seconds: Float): Float = velocity * (1f - exp(-FRICTION * seconds)) / FRICTION

    /** Its speed after [seconds]. */
    fun velocityAfter(velocity: Float, seconds: Float): Float = velocity * exp(-FRICTION * seconds)

    /** How far it will travel in all, until it stops. */
    fun totalDistance(velocity: Float): Float = velocity / FRICTION

    /**
     * The flick to start when a finger lifts moving at ([vx], [vy]), or null if it's too slow.
     * A flick that's mostly sideways, or a drag that was locked sideways, moves only sideways.
     */
    fun start(vx: Float, vy: Float, lockedSideways: Boolean): Pair<Float, Float>? {
        if (hypot(vx, vy) < MIN_START_SPEED) return null
        val sideways = lockedSideways || abs(vx) > SIDEWAYS_RATIO * abs(vy)
        return if (sideways) vx to 0f else vx to vy
    }

    /** "Mostly sideways": the sideways speed is at least this many times the up-down speed. */
    const val SIDEWAYS_RATIO = 2f
}

/**
 * Keeps a one-finger drag on the sideways axis when it starts out sideways (PHASE-2.md F6), so
 * paging through a notebook doesn't wobble up and down, even zoomed in. The lock is gentle: a
 * clear up-or-down movement while locked releases it.
 */
class AxisLock {
    enum class State { UNDECIDED, SIDEWAYS, FREE }

    var state = State.UNDECIDED
        private set

    private var totalX = 0f
    private var totalY = 0f
    private var held = 0f // up-down movement swallowed while locked

    /** Takes one step of finger movement and returns the movement to apply. */
    fun step(dx: Float, dy: Float): Pair<Float, Float> {
        when (state) {
            State.UNDECIDED -> {
                totalX += dx; totalY += dy
                if (hypot(totalX, totalY) >= DECIDE_DISTANCE) {
                    state = if (abs(totalX) >= LOCK_RATIO * abs(totalY)) State.SIDEWAYS else State.FREE
                }
                return if (state == State.SIDEWAYS) dx to 0f else dx to dy
            }
            State.SIDEWAYS -> {
                held += dy
                if (abs(held) > RELEASE_DISTANCE) {
                    state = State.FREE
                    return dx to dy
                }
                return dx to 0f
            }
            State.FREE -> return dx to dy
        }
    }

    companion object {
        /** The drag's direction is decided once it has moved this far (pixels). */
        const val DECIDE_DISTANCE = 24f

        /** Sideways enough to lock: the sideways movement is at least this times the up-down. */
        const val LOCK_RATIO = 1.5f

        /** Up-down movement (pixels) that releases the lock. */
        const val RELEASE_DISTANCE = 90f
    }
}
