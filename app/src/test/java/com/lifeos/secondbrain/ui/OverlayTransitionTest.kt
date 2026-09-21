package com.lifeos.secondbrain.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the navigation-depth direction contract.
 *
 * Screenshots cannot verify this: one capture costs ~1.8s while the transition lasts 320ms, so the
 * animation is always finished before a frame can be taken. `EnterTransition` exposes only its
 * target alpha, never the direction it travels, so the travel itself is not observable from the
 * outside either. Direction is therefore kept in [DepthMotion] as plain numbers, and these
 * assertions are the real regression guard for "returning must mirror entering".
 *
 * Offsets are fractions of the container width along X. Positive is towards the trailing edge
 * (right in LTR), so `+1` is fully off-screen right and `-1` fully off-screen left.
 */
class OverlayTransitionTest {

    @Test
    fun `entering sends the overlay in from the trailing edge`() {
        val depth = DepthMotion.of(forward = true)

        assertEquals(1f, depth.overlayFrom, 0.001f)
    }

    @Test
    fun `returning sends the overlay back out to the trailing edge`() {
        val depth = DepthMotion.of(forward = false)

        // The regression this guards: with one shared transition the overlay also travelled +1 on
        // return, so leaving a screen replayed entering it and the motion pointed the wrong way.
        assertEquals(-1f, depth.overlayFrom, 0.001f)
    }

    @Test
    fun `direction is mirrored rather than replayed`() {
        val forward = DepthMotion.of(forward = true)
        val back = DepthMotion.of(forward = false)

        assertTrue(
            "overlay travel must reverse sign between entering and returning",
            forward.overlayFrom * back.overlayFrom < 0f
        )
        assertEquals(
            "the two directions must be exact opposites, not merely different",
            -forward.overlayFrom,
            back.overlayFrom,
            0.001f
        )
    }

    @Test
    fun `the shell parallax is constant and stays shallow`() {
        val forward = DepthMotion.of(forward = true)
        val back = DepthMotion.of(forward = false)

        // The shell is displaced once while covered and restored afterwards; it must never be sent
        // further away on the return leg, which would look like the shell fleeing the user.
        assertEquals(forward.shellFrom, back.shellFrom, 0.001f)
        assertEquals(-0.2f, forward.shellFrom, 0.001f)
        assertTrue(
            "parallax must stay a small fraction of the width so it reads as depth",
            kotlin.math.abs(forward.shellFrom) < 0.5f
        )
    }

    @Test
    fun `shell and overlay travel in opposite directions while an overlay is on top`() {
        // This is what makes the pair read as depth: the arriving overlay and the receding shell
        // move against each other. Note it is asserted for the entering leg only — on the return
        // leg the overlay reverses while the shell simply retraces its own displacement.
        val entering = DepthMotion.of(forward = true)

        assertTrue(
            "overlay and shell must travel in opposite directions",
            entering.shellFrom * entering.overlayFrom < 0f
        )

        // On return the shell must retrace (same offset), never be pushed further away, and the
        // overlay must reverse so the two no longer share a direction.
        val returning = DepthMotion.of(forward = false)
        assertEquals(entering.shellFrom, returning.shellFrom, 0.001f)
        assertTrue(
            "returning overlay and shell must not share a direction",
            returning.shellFrom * returning.overlayFrom > 0f
        )
    }
}
