// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import app.tellyscope.adb.AdbClient
import app.tellyscope.adb.AllowedKey
import java.io.File

/**
 * Who can use ADB on a TV ([allowed]) and who is connected now ([connected]). Either is null
 * when the TV wouldn't say, which reads as "can't check", never as nobody. The two lists are
 * never tied together: the TV doesn't say which key is behind which connection.
 */
data class TvAccess(
    val connected: List<AccessClient>? = null,
    val allowed: List<AccessKey>? = null,
) {
    /** Connections the warning is about: neither this phone nor an address the user said is theirs. */
    val others: List<AccessClient> get() = connected.orEmpty().filter { it.who == AccessClient.Who.Other || it.who == AccessClient.Who.OnTv }

    val warning: Boolean get() = others.isNotEmpty()

    /** The same, with [names] on the computers they belong to (address to name). */
    fun named(names: Map<String, String>): TvAccess =
        copy(connected = connected?.map { c -> names[c.address]?.let { c.copy(name = it) } ?: c })

    /** Checked, not assumed: both lists read, no other connection, and no allowed key but this phone's. */
    val onlyThisPhone: Boolean get() = connected != null && allowed != null && others.isEmpty() && allowed.none { !it.phone }

    companion object {
        /**
         * Sorts the TV's open connections into this phone, the user's own and the rest. A
         * loopback connection is this phone only when Tellyscope itself reaches the TV over
         * loopback ([tvHost], as through an emulator's forward); otherwise it is an app on the TV.
         */
        fun of(
            clients: List<AdbClient>?,
            keys: List<AllowedKey>?,
            phoneAddresses: Set<String>,
            tvHost: String,
            mine: Set<String>,
            phoneFingerprint: String?,
        ): TvAccess {
            val tvLoopback = tvHost == "localhost" || AdbClient(tvHost, 0).loopback
            val connected = clients?.groupBy { it.address }?.map { (address, rows) ->
                val loopback = rows.first().loopback
                val phone = if (loopback) tvLoopback else address in phoneAddresses
                val who = when {
                    phone -> AccessClient.Who.Phone
                    address in mine -> AccessClient.Who.Mine
                    loopback -> AccessClient.Who.OnTv
                    else -> AccessClient.Who.Other
                }
                AccessClient(address, rows.size, who)
            }?.sortedBy { it.who.ordinal }
            val allowed = keys?.map { AccessKey(it.fingerprint, it.name, it.lastSeen, phone = it.fingerprint == phoneFingerprint) }
                ?.sortedByDescending { it.phone }
            return TvAccess(connected, allowed)
        }
    }
}

/**
 * [name]: the address's network name (see [NetworkNames]), null when none answered in time. It
 * describes the address only, never which key is behind it.
 */
data class AccessClient(val address: String, val connections: Int, val who: Who, val name: String? = null) {
    enum class Who { Phone, Mine, Other, OnTv }
}

/** An allowed computer. [lastSeen] is rough: the TV stamps keys, not sessions. */
data class AccessKey(val fingerprint: String, val name: String?, val lastSeen: Long?, val phone: Boolean = false)

/**
 * Addresses the user marked "It's mine" on each TV, keyed like [NameBook]. `key \t address` per
 * line; null file keeps it in memory (tests, the fake source).
 */
class MineBook(private val file: File? = null) {
    private val mine = linkedMapOf<String, Set<String>>()

    init {
        file?.takeIf { it.exists() }?.let { f ->
            runCatching { f.readLines() }.getOrDefault(emptyList()).forEach { line ->
                val (k, a) = line.split('\t').takeIf { it.size == 2 && it.none(String::isBlank) } ?: return@forEach
                mine[k] = mine[k].orEmpty() + a
            }
        }
    }

    @Synchronized
    operator fun get(key: String): Set<String> = mine[key].orEmpty()

    @Synchronized
    fun add(key: String, address: String) {
        val clean = address.trim().takeIf { it.isNotEmpty() && '\t' !in it && '\n' !in it } ?: return
        mine[key] = mine[key].orEmpty() + clean
        save()
    }

    /** The address stood in until the TV said who it is; the marks move to the real key. */
    @Synchronized
    fun rekey(from: String, to: String) {
        if (from == to) return
        val moved = mine.remove(from) ?: return
        mine[to] = mine[to].orEmpty() + moved
        save()
    }

    private fun save() {
        val f = file ?: return
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(mine.entries.joinToString("") { (k, set) -> set.joinToString("") { "$k\t$it\n" } })
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }
}
