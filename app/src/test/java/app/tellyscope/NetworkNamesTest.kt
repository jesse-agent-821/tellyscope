// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.NetworkNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** Real time throughout: the lookups run on their own threads, as a DNS call does. */
class NetworkNamesTest {

    @Test fun cleansTheName() {
        assertEquals("Alex-MacBook", NetworkNames.clean("Alex-MacBook.local", "192.0.2.47"))
        assertEquals("Alex-MacBook", NetworkNames.clean("Alex-MacBook.LOCAL.", "192.0.2.47"))
        assertEquals("desk.lan", NetworkNames.clean("desk.lan", "192.0.2.47"))
        assertNull(NetworkNames.clean("192.0.2.47", "192.0.2.47")) // no PTR: Java hands the address back
        assertNull(NetworkNames.clean(".local", "192.0.2.47"))
        assertNull(NetworkNames.clean(null, "192.0.2.47"))
    }

    @Test fun namesWhatAnswersAndLeavesOutWhatDoesnt() = runBlocking {
        val names = NetworkNames(resolve = { if (it == "192.0.2.47") "alex-laptop.local" else it })
        assertEquals(mapOf("192.0.2.47" to "alex-laptop"), names.of(listOf("192.0.2.47", "192.0.2.48")))
    }

    @Test fun aSlowLookupNeverHoldsTheScreenUp() = runBlocking {
        val names = NetworkNames(wait = 100.milliseconds, resolve = { Thread.sleep(600); "slow-box" })
        val start = System.nanoTime()
        assertEquals(emptyMap<String, String>(), names.of(listOf("192.0.2.47")))
        assertTrue((System.nanoTime() - start) / 1_000_000 < 500)
        delay(800) // it finishes on its own, so the next read has it
        assertEquals(mapOf("192.0.2.47" to "slow-box"), names.of(listOf("192.0.2.47")))
    }

    @Test fun answersAreKeptUntilTheyAreOld() = runBlocking {
        val asked = AtomicInteger()
        var now = 0L
        val names = NetworkNames(keep = 10.minutes, clock = { now }, resolve = { asked.incrementAndGet(); null })
        names.of(listOf("192.0.2.47"))
        names.of(listOf("192.0.2.47"))
        assertEquals(1, asked.get())
        now += 11 * 60_000
        names.of(listOf("192.0.2.47"))
        assertEquals(2, asked.get())
    }

    @Test fun aQuickAnswerStillRefreshesWhenOld() = runBlocking {
        // Unconfined answers before the lookup is even stored, the race a fast resolver can win on IO.
        val asked = AtomicInteger()
        var now = 0L
        val names = NetworkNames(keep = 10.minutes, clock = { now }, context = Dispatchers.Unconfined,
            resolve = { "box-${asked.incrementAndGet()}" })
        assertEquals(mapOf("192.0.2.47" to "box-1"), names.of(listOf("192.0.2.47")))
        now += 11 * 60_000
        assertEquals(mapOf("192.0.2.47" to "box-2"), names.of(listOf("192.0.2.47")))
    }

    @Test fun aFailingLookupIsNoName() = runBlocking {
        val names = NetworkNames(resolve = { throw java.net.UnknownHostException(it) })
        assertEquals(emptyMap<String, String>(), names.of(listOf("192.0.2.47")))
    }
}
