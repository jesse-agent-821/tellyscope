// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Network names for the other computers in Who has access, by reverse DNS: the name the router or
 * the device gave itself, so any device can claim any name. The caller waits [wait] at most; a
 * lookup still running then finishes on its own (a DNS call can't be interrupted) and its answer
 * is there next time. Answers, none included, are kept for [keep], so a screen that reads access
 * every second doesn't ask again each time.
 */
class NetworkNames(
    private val wait: Duration = 1.5.seconds,
    private val keep: Duration = 10.minutes,
    private val clock: () -> Long = System::currentTimeMillis,
    private val resolve: (String) -> String? = { ip -> InetAddress.getByName(ip).canonicalHostName },
    context: CoroutineContext = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + context)
    private val known = ConcurrentHashMap<String, Pair<Long, String?>>()
    private val asking = ConcurrentHashMap<String, Deferred<String?>>()

    /** [addresses] to their names; an address with no name, or none yet, is left out. */
    suspend fun of(addresses: Collection<String>): Map<String, String> {
        val now = clock()
        val asks = addresses.distinct()
            .filter { a -> known[a]?.let { now - it.first > keep.inWholeMilliseconds } ?: true }
            // Started only once it's in [asking], so a quick answer can't remove itself before it's there.
            .map { a -> asking.computeIfAbsent(a) { ask(a) }.also { it.start() } }
        if (asks.isNotEmpty()) withTimeoutOrNull(wait) { asks.awaitAll() }
        return addresses.mapNotNull { a -> known[a]?.second?.let { a to it } }.toMap()
    }

    private fun ask(address: String) = scope.async(start = CoroutineStart.LAZY) {
        val name = runCatching { clean(resolve(address), address) }.getOrNull()
        known[address] = clock() to name
        asking.remove(address)
        name
    }

    companion object {
        /** Null for no answer or the address itself; a trailing dot and a `.local` suffix go. */
        fun clean(name: String?, address: String): String? =
            name?.trim()?.removeSuffix(".")?.let { if (it.endsWith(".local", ignoreCase = true)) it.dropLast(6) else it }
                ?.takeIf { it.isNotEmpty() && it != address }
    }
}
