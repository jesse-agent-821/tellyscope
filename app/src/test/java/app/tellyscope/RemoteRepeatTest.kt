// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.adb.RemoteKey
import app.tellyscope.ui.REPEAT_EVERY_MS
import app.tellyscope.ui.repeatWhileHeld
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteRepeatTest {
    @Test fun heldKeyStopsWhenTheTvStopsTakingIt() = runTest {
        var presses = 0
        // Still held: only the TV's third "no" ends it.
        repeatWhileHeld(RemoteKey.UP, held = { true }) { presses++ < 2 }
        assertEquals(3, presses)
    }

    @Test fun noTvMeansOnePress() = runTest {
        var presses = 0
        repeatWhileHeld(RemoteKey.UP, held = { true }) { presses++; false }
        assertEquals(1, presses)
    }

    @Test fun releaseEndsTheRepeat() = runTest {
        var presses = 0
        var held = true
        repeatWhileHeld(RemoteKey.VOLUME_UP, held = { held }) { presses++; if (presses == 5) held = false; true }
        assertEquals(5, presses)
    }

    @Test fun aKeyTheTvTakesAtOnceRepeatsAtAndroidsRate() = runTest {
        var presses = 0
        // Held for one second after the first repeat: the TV answering at once must not skip a list.
        repeatWhileHeld(RemoteKey.DOWN, held = { testScheduler.currentTime < 400 + 1000 }) { presses++; true }
        assertEquals(1 + 1000 / REPEAT_EVERY_MS.toInt(), presses)
    }

    @Test fun backspaceDeletesOnceOnATap() = runTest {
        var presses = 0
        repeatWhileHeld(RemoteKey.DEL, held = { false }) { presses++; true }
        assertEquals(1, presses)
    }

    @Test fun heldBackspaceRepeatsUntilReleased() = runTest {
        var presses = 0
        repeatWhileHeld(RemoteKey.DEL, held = { testScheduler.currentTime < 400 + 500 }) { presses++; true }
        assertEquals(1 + 500 / REPEAT_EVERY_MS.toInt(), presses)
    }

    @Test fun okNeverRepeats() = runTest {
        var presses = 0
        repeatWhileHeld(RemoteKey.OK, held = { true }) { presses++; true }
        assertEquals(1, presses)
    }
}
