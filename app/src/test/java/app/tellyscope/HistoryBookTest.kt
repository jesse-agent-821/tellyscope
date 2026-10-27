// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.Action
import app.tellyscope.data.FakeDeviceSource
import app.tellyscope.data.HistoryBook
import app.tellyscope.data.HistoryEntry
import app.tellyscope.data.historyKey
import app.tellyscope.data.restoreEach
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryBookTest {
    @get:Rule val tmp = TemporaryFolder()

    private val tv = "tv-1"

    @Test fun aSettingsPreviousValuesSurviveARestart() {
        val file = tmp.root.resolve("history.tsv")
        val previous = mapOf("private_dns_mode" to "opportunistic", "private_dns_specifier" to null, "odd" to "a=b,c")
        val change = HistoryBook(file).record(tv, 1_000, Action.Change, "PRIVATE_DNS", "Private DNS set to x", Action.Change, previous = previous)
        assertEquals(listOf(change), HistoryBook(file).entries(tv))
    }

    @Test fun aLaterStepOfASwitchIsKeptOnItsEntry() {
        val file = tmp.root.resolve("history.tsv")
        val switch = HistoryBook(file).record(tv, 1_000, Action.Home, "com.google.android.tvlauncher", "Projectivy", Action.Enable,
            previous = mapOf("from" to "com.google.android.tvlauncher/.Main", "to" to "com.spocky.projengmenu/.Home"))
        HistoryBook(file).apply { amend(switch.id, mapOf("service" to "com.spocky.projengmenu/.Service")); amend(switch.id, mapOf("note" to "dismissed")) }
        val previous = HistoryBook(file).entries(tv).single().previous
        assertEquals("com.spocky.projengmenu/.Service", previous?.get("service"))
        assertEquals("dismissed", previous?.get("note"))
        assertEquals("com.google.android.tvlauncher/.Main", previous?.get("from"))
    }

    @Test fun historyAndUndoSurviveARestart() {
        val file = tmp.root.resolve("history.tsv")
        val before = HistoryBook(file)
        before.setBaseline(tv, setOf("com.old.off"))
        val disabled = before.record(tv, 1_000, Action.Disable, "com.netflix.ninja", "Netflix", undo = Action.Enable)

        // Next launch: same file, new book. The entry is there and can still be undone.
        val after = HistoryBook(file)
        assertEquals(listOf(disabled), after.entries(tv))
        assertTrue(after.hasBaseline(tv))
        after.record(tv, 2_000, Action.Enable, "com.netflix.ninja", "Netflix", undo = Action.Disable, undoing = disabled.id)

        val again = HistoryBook(file).entries(tv)
        assertEquals(2, again.size)
        assertEquals(2_000L, again.single { it.id == disabled.id }.undoneAt)
        assertNull(again.first().undoneAt)
        assertTrue(again.first().id > disabled.id)
    }

    @Test fun aHomeSwitchSurvivesARestartAndRestoreTurnsTheOldLauncherOn() {
        val file = tmp.root.resolve("history.tsv")
        val previous = mapOf("from" to "com.google.android.tvlauncher/.MainActivity", "to" to "com.spocky.projengmenu/.Main")
        val switch = HistoryBook(file).record(tv, 1_000, Action.Home, "com.google.android.tvlauncher", "Projectivy Launcher", Action.Enable, previous = previous)
        val after = HistoryBook(file)
        assertEquals(listOf(switch), after.entries(tv))
        assertEquals(listOf("com.google.android.tvlauncher"), after.restorable(tv))
        assertEquals(switch.id, after.openDisable(tv, "com.google.android.tvlauncher"))

        after.record(tv, 2_000, Action.Enable, "com.google.android.tvlauncher", "Android TV Home", undo = null, undoing = switch.id)
        assertTrue(after.restorable(tv).isEmpty())
    }

    @Test fun anOlderSwitchBackLosesItsUndoOnceANewerChangeTakesHome() {
        val file = tmp.root.resolve("history.tsv")
        val book = HistoryBook(file)
        val back = book.record(tv, 1_000, Action.HomeBack, "fr.neamar.kiss", "Quickstep", Action.Home)
        val elsewhere = book.record("tv-2", 1_000, Action.HomeBack, "fr.neamar.kiss", "Quickstep", Action.Home)
        book.record(tv, 2_000, Action.Disable, "com.netflix.ninja", "Netflix", Action.Enable)
        assertEquals(Action.Home, book.entries(tv).first { it.id == back.id }.undo) // not a home change

        val switch = book.record(tv, 3_000, Action.Home, "com.android.launcher3", "Fossify Launcher", Action.Enable)
        assertNull(HistoryBook(file).entries(tv).first { it.id == back.id }.undo)
        assertEquals(Action.Home, book.entries("tv-2").first { it.id == elsewhere.id }.undo)

        // Undoing a switch makes the old launcher Home again, so it counts too.
        val back2 = book.record(tv, 4_000, Action.HomeBack, "org.fossify.home", "Quickstep", Action.Home)
        assertEquals(Action.Home, book.entries(tv).first { it.id == back2.id }.undo)
        book.record(tv, 5_000, Action.Enable, "com.android.launcher3", "Quickstep", undo = null, undoing = switch.id)
        assertNull(book.entries(tv).first { it.id == back2.id }.undo)
    }

    @Test fun baselineIsWrittenOnlyOnce() {
        val book = HistoryBook(tmp.root.resolve("history.tsv"))
        book.setBaseline(tv, setOf("com.old.off"))
        book.setBaseline(tv, emptySet())
        book.record(tv, 1, Action.Disable, "com.old.off", "Old", Action.Enable)
        assertEquals(emptyList<String>(), book.restorable(tv))
    }

    @Test fun restoreSkipsWhatWasOffBeforeAndWhatWasTurnedBackOn() {
        val book = HistoryBook()
        book.setBaseline(tv, setOf("com.old.off"))
        book.record(tv, 1, Action.Disable, "com.netflix.ninja", "Netflix", Action.Enable)
        book.record(tv, 2, Action.Disable, "org.jellyfin.androidtv", "Jellyfin", Action.Enable)
        book.record(tv, 3, Action.Enable, "org.jellyfin.androidtv", "Jellyfin", Action.Disable)
        book.record(tv, 4, Action.Disable, "com.old.off", "Old", Action.Enable)
        book.record(tv, 5, Action.Stop, "com.google.android.youtube.tv", "YouTube", Action.Open)
        book.record("other-tv", 6, Action.Disable, "com.xgimi.ota", "XGIMI Updates", Action.Enable)
        assertEquals(listOf("com.netflix.ninja"), book.restorable(tv))
    }

    @Test fun damagedLinesCostOnlyThemselves() {
        val file = tmp.root.resolve("history.tsv")
        HistoryBook(file).record(tv, 1, Action.Disable, "com.netflix.ninja", "Net\tflix", Action.Enable)
        file.appendText("v1\te\ttv-1\tnot-a-number\n")
        file.appendText("v2\te\ttv-1\t9\t9\tDisable\tx\tx\t\t\n")
        val entries = HistoryBook(file).entries(tv)
        assertEquals(1, entries.size)
        assertEquals("Net flix", entries.single().label)
    }

    @Test fun uninstallHasNoUndoAndRestoreForgetsTheApp() = runTest {
        val source = FakeDeviceSource()
        val id = "tv"
        source.act(id, Action.Disable, "com.netflix.ninja")
        val gone = source.act(id, Action.Uninstall, "com.netflix.ninja")

        assertNull(gone.undo)
        assertTrue(source.apps(id).none { it.packageName == "com.netflix.ninja" })
        assertEquals(emptyList<String>(), HistoryBook.leftDisabled(source.history(id)))
        assertEquals(emptyList<String>(), source.restoreAll(id).map { it.packageName })
    }

    @Test fun aHomeSwitchThatReplacedNothingLeavesNothingToRestore() {
        val book = HistoryBook()
        book.record("tv", 0L, Action.Home, "com.google.android.tvlauncher", "Projectivy Launcher", undo = Action.Enable)
        book.record("tv", 1L, Action.Home, "me.efesser.flauncher", "FLauncher", undo = null)
        assertEquals(listOf("com.google.android.tvlauncher"), HistoryBook.leftDisabled(book.entries("tv")))
        assertNull(book.openDisable("tv", "me.efesser.flauncher"))
    }

    @Test fun restoreAllTurnsOnOnlyWhatTellyscopeDisabled() = runTest {
        // The fake TV starts with TV Recommendations already disabled.
        val source = FakeDeviceSource()
        val id = "tv"
        source.act(id, Action.Disable, "com.netflix.ninja")
        source.act(id, Action.Disable, "org.jellyfin.androidtv")
        source.act(id, Action.Enable, "com.google.android.tvrecommendations")
        source.act(id, Action.Disable, "com.google.android.tvrecommendations")

        val restored = source.restoreAll(id)

        assertEquals(setOf("com.netflix.ninja", "org.jellyfin.androidtv"), restored.map { it.packageName }.toSet())
        val apps = source.apps(id).associateBy { it.packageName }
        assertTrue(apps.getValue("com.netflix.ninja").enabled)
        assertTrue(apps.getValue("org.jellyfin.androidtv").enabled)
        assertFalse(apps.getValue("com.google.android.tvrecommendations").enabled)
        assertEquals(emptyList<String>(), source.restoreAll(id).map { it.packageName })
    }

    @Test fun historyFollowsTheSerialNotTheAddress() {
        assertEquals("sn:XK03H1234", historyKey(" XK03H1234\n", "a1b2", "tv-1"))
        // Placeholder serials some boxes ship would mix up every TV of that model.
        assertEquals("id:a1b2c3", historyKey("0123456789ABCDEF", "a1b2c3", "tv-1"))
        assertEquals("id:a1b2c3", historyKey("unknown", "a1b2c3\n", "tv-1"))
        assertEquals("tv-1", historyKey("", "0000000000000000", "tv-1"))
        assertEquals("tv-1", historyKey(null, null, "tv-1"))
    }

    @Test fun historyRecordedBeforeTheKeyMovesOverToIt() {
        val file = tmp.root.resolve("history.tsv")
        val book = HistoryBook(file)
        book.setBaseline(tv, setOf("com.old.off"))
        book.record(tv, 1, Action.Disable, "com.netflix.ninja", "Netflix", Action.Enable)
        book.rekey(tv, "sn:XK03H1234")

        val after = HistoryBook(file)
        assertEquals(emptyList<Any>(), after.entries(tv))
        assertEquals(listOf("com.netflix.ninja"), after.entries("sn:XK03H1234").map { it.packageName })
        assertEquals(listOf("com.netflix.ninja"), after.restorable("sn:XK03H1234"))
        assertFalse(after.hasBaseline(tv))
    }

    @Test fun restoreMarksTheDisablesItReversed() = runTest {
        val source = FakeDeviceSource()
        val id = source.known().first().id
        source.act(id, Action.Disable, "com.netflix.ninja")
        source.restoreAll(id)
        val disable = source.history(id).single { it.action == Action.Disable }
        // Already back on: no Undo left to offer.
        assertTrue(disable.undoneAt != null)
    }

    @Test fun restoreAfterTwoSwitchesEndsOnTheTvsOwnHome() = runTest {
        // L→A→B, where L sorts before A: alphabetical order undid L→A first and ended on A.
        val own = "com.google.android.tvlauncher"
        val first = "me.efesser.launcher"
        val book = HistoryBook(tmp.root.resolve("history.tsv"))
        book.record(tv, 1_000, Action.Home, own, "A", Action.Enable, previous = mapOf("from" to "$own/.Main", "to" to "$first/.Main"))
        book.record(tv, 2_000, Action.Home, first, "B", Action.Enable, previous = mapOf("from" to "$first/.Main", "to" to "com.spocky.projengmenu/.Main"))
        // As the source does: turning on a launcher a switch turned off makes it Home again.
        var home = "com.spocky.projengmenu/.Main"
        restoreEach(book.restorable(tv)) { pkg ->
            val switch = book.openDisable(tv, pkg)!!
            book.entries(tv).single { it.id == switch }.previous?.get("from")?.let { home = it }
            book.record(tv, 3_000, Action.Enable, pkg, pkg, null, undoing = switch)
        }
        assertEquals("$own/.Main", home)
        assertTrue(book.restorable(tv).isEmpty())
    }

    @Test fun aStoppedRestoreSaysHowFarItGot() = runTest {
        var calls = 0
        val e = runCatching {
            restoreEach(listOf("a", "b", "c")) { pkg ->
                if (++calls == 3) throw IllegalStateException("the TV refused")
                HistoryEntry(calls.toLong(), tv, 0, Action.Enable, pkg, pkg, Action.Disable, null)
            }
        }.exceptionOrNull()
        assertEquals("turned 2 apps back on, then stopped: the TV refused", e?.message)
    }

    @Test fun aDroppedConnectionStillSaysHowFarItGot() = runTest {
        val e = runCatching {
            restoreEach(listOf("a", "b")) { pkg ->
                if (pkg == "b") throw java.io.IOException("connection reset")
                HistoryEntry(1, tv, 0, Action.Enable, pkg, pkg, Action.Disable, null)
            }
        }.exceptionOrNull()
        assertEquals("turned 1 app back on, then stopped: connection reset", e?.message)
    }
}
