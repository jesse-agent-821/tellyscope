// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import com.flyfishxu.kadb.exception.AdbPairAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okio.Buffer
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.InterruptedByTimeoutException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPublicKey

sealed interface ConnectResult {
    class Online(val session: AdbSession) : ConnectResult
    /** Port answered but the TV never accepted our key within the wait. */
    data object NotAllowed : ConnectResult
    /** Wireless debugging port that doesn't know our key yet: pair with the 6-digit code first. */
    data object NeedsPairing : ConnectResult
    data class Unreachable(val reason: String) : ConnectResult
}

sealed interface PairResult {
    data object Paired : PairResult
    data class WrongCode(val reason: String) : PairResult
    /** The pairing port refused, timed out or dropped mid-handshake. */
    data class Unreachable(val reason: String) : PairResult
}

/**
 * Pairs on the port the TV advertises now: [find] browses for it first, and [hint] (a port seen in an
 * earlier scan, or one the user typed) is only the fallback, since every reopen of the TV's pairing
 * dialog picks a new port. A refused port gets one more browse before giving up. Never guesses the
 * connect port: that one doesn't answer pairing.
 */
suspend fun pairOnFreshPort(hint: Int?, find: suspend () -> Int?, pair: suspend (Int) -> PairResult): PairResult {
    val port = find() ?: hint
        ?: return PairResult.Unreachable("the TV isn't offering pairing; keep \"Pair device with pairing code\" open on it")
    val r = pair(port)
    if (r !is PairResult.Unreachable) return r
    return find()?.takeIf { it != port }?.let { pair(it) } ?: r
}

/**
 * Opens ADB connections with one persisted host key, so the TV's "always allow" survives restarts.
 * Call [init] once per process before anything else.
 */
object Connector {
    const val ALLOW_WAIT_MS = 60_000
    private const val TCP_TIMEOUT_MS = 3_000

    fun init(keyFile: File) {
        keyFile.parentFile?.mkdirs()
        KadbCert.configure(
            store = OkioFilePrivateKeyStore(privateKeyPath = keyFile.absolutePath.toPath()),
            policy = KadbCertPolicy(),
            additionalPrivateKeysPem = emptyList(),
        )
        KadbCert.ensureReady()
    }

    /**
     * This phone's key as the TV's "Allow USB debugging?" box prints it: the MD5 of the ADB public key,
     * as colon-separated hex pairs, the way Android's AdbDebuggingManager formats it. Null before [init].
     */
    fun fingerprint(): String? = runCatching {
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(KadbCert.ensureReady().certificatePem.inputStream())
        MessageDigest.getInstance("MD5").digest(adbPublicKey(cert.publicKey as RSAPublicKey))
            .joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    /**
     * The public key in adb's own layout, the bytes the TV hashes: word count, -1/n mod 2^32,
     * the modulus, R^2 mod n, then the exponent. All little-endian, least significant word first.
     */
    internal fun adbPublicKey(key: RSAPublicKey): ByteArray {
        val n = key.modulus
        val words = n.bitLength() / 32
        val r32 = BigInteger.ONE.shiftLeft(32)
        val rr = BigInteger.ONE.shiftLeft(words * 32).pow(2).mod(n)
        val buf = ByteBuffer.allocate(4 * (3 + 2 * words)).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(words)
        buf.putInt(r32.subtract(n.mod(r32).modInverse(r32)).toInt())
        fun putWords(v: BigInteger) = repeat(words) { i -> buf.putInt(v.shiftRight(32 * i).toInt()) }
        putWords(n)
        putWords(rr)
        buf.putInt(key.publicExponent.toInt())
        return buf.array()
    }

    /**
     * Connects to [host]:[port]. The call blocks while the Allow prompt is on the TV, up to
     * [allowWaitMs]; the UI shows WAITING for that whole time. Cancelling it closes the attempt.
     */
    suspend fun connect(host: String, port: Int, allowWaitMs: Int = ALLOW_WAIT_MS): ConnectResult {
        // InetSocketAddress throws IllegalArgumentException outside this range.
        if (port !in 1..65535) return ConnectResult.Unreachable("port $port is not between 1 and 65535")
        // Probe TCP first: a refused or silent port is "unreachable", not "not allowed".
        try {
            runInterruptible(Dispatchers.IO) { Socket().use { it.connect(InetSocketAddress(host, port), TCP_TIMEOUT_MS) } }
        } catch (e: IOException) {
            return ConnectResult.Unreachable(e.message ?: e.javaClass.simpleName)
        }
        val kadb = Kadb.create(host, port, connectTimeout = TCP_TIMEOUT_MS, socketTimeout = allowWaitMs)
        return try {
            val out = runInterruptible(Dispatchers.IO) { kadb.shell("echo ok").output }
            if (out.trim() != "ok") throw IOException("unexpected probe output")
            ConnectResult.Online(KadbSession(kadb))
        } catch (e: CancellationException) {
            kadb.close()
            throw e
        } catch (e: AdbPairAuthException) {
            kadb.close()
            ConnectResult.NeedsPairing
        } catch (e: Exception) {
            // Kadb also throws IllegalStateException on protocol surprises; never let it escape.
            kadb.close()
            // No answer times out; Deny makes adbd drop the socket. Both read as "not allowed".
            if (e.isAuthFailure()) ConnectResult.NotAllowed else ConnectResult.Unreachable(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Android 11+ wireless debugging: pair once with the 6-digit code, then [connect] to the connect
     * port. Never throws, except to cancel.
     */
    suspend fun pair(host: String, pairingPort: Int, code: String): PairResult {
        if (code.length != 6 || !code.all(Char::isDigit)) return PairResult.WrongCode("pairing code must be 6 digits")
        if (pairingPort !in 1..65535) return PairResult.Unreachable("port $pairingPort is not between 1 and 65535")
        return try {
            Kadb.pair(host, pairingPort, code)
            PairResult.Paired
        } catch (e: CancellationException) {
            throw e
        } catch (e: AdbPairAuthException) {
            PairResult.WrongCode(e.message ?: "pairing failed")
        } catch (e: Exception) {
            // A Wi-Fi drop mid-handshake surfaces as an IOException, or as Kadb's IllegalStateException.
            PairResult.Unreachable(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun Throwable.isAuthFailure(): Boolean =
        generateSequence(this) { it.cause }.any {
                it is java.io.EOFException || it is InterruptedIOException || it is InterruptedByTimeoutException
            }
}

internal class KadbSession(private val kadb: Kadb) : AdbSession {
    /**
     * The connection this session opened, or an IOException once it has dropped. Kadb would open a
     * fresh one behind our back, which puts a new "Allow debugging?" prompt on a TV that took the key
     * back; the link reconnects instead, where a refusal stops it.
     */
    private fun live(): Kadb = kadb.takeIf { it.connectionCheck() } ?: throw IOException("connection closed")

    /**
     * Kadb blocks the calling thread while it waits for the reply; that wait is interruptible, so
     * cancelling one call (a closed screen, a timeout) ends only that stream, not the connection.
     */
    override suspend fun shell(command: String): ShellResult {
        val r = runInterruptible(Dispatchers.IO) { live().shell(command) }
        return ShellResult(r.output, r.exitCode)
    }

    override suspend fun push(bytes: ByteArray, remotePath: String) = runInterruptible(Dispatchers.IO) {
        live().push(Buffer().write(bytes), remotePath, "644".toInt(8), System.currentTimeMillis())
    }

    override fun install(source: Source, size: Long, vararg options: String) = live().install(source, size, *options)

    override fun installMultiple(apks: List<File>, vararg options: String) = live().installMultiple(apks, *options)

    override fun push(source: Source, remotePath: String, mode: Int, lastModifiedMs: Long) =
        live().push(source, remotePath, mode, lastModifiedMs)

    override fun pull(sink: Sink, remotePath: String) = live().pull(sink, remotePath)

    override fun list(remoteDir: String): List<RemoteEntry> = live().openSync().use { sync ->
        sync.list(remoteDir).map { RemoteEntry(it.name, it.mode, it.size, it.mtimeSec) }
    }

    override suspend fun reboot() {
        runInterruptible(Dispatchers.IO) { live().open("reboot:").close() }
    }

    /** `exec:` is adbd's raw service: no pty, so bytes go both ways untouched. */
    override suspend fun exec(command: String): AdbPipe {
        val stream = runInterruptible(Dispatchers.IO) { live().open("exec:$command") }
        return object : AdbPipe {
            // Already buffered: `.buffer()` here would be their internal Buffer, cut off from the TV.
            override val stdin = stream.sink
            override val stdout = stream.source
            override fun close() = stream.close()
        }
    }

    override fun close() = kadb.close()
}
