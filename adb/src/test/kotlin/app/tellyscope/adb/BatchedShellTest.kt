// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchedShellTest {
    private val sep = Commands.SEP

    @Test fun eachCommandGetsItsOwnOutputAndExitCode() {
        val answers = BatchedShell.split("package:a\npackage:b\n${sep}0\n${sep}0\nError\n${sep}255\n", listOf("one", "two", "three"))
        assertEquals(ShellResult("package:a\npackage:b\n", 0), answers["one"])
        assertEquals(ShellResult("", 0), answers["two"])
        assertEquals(ShellResult("Error\n", 255), answers["three"])
    }

    @Test fun aLineCutShortLeavesTheRestUnanswered() {
        val answers = BatchedShell.split("package:a\n${sep}0\npackage:", listOf("one", "two"))
        assertEquals(setOf("one"), answers.keys)
    }

    @Test fun oneRoundTripThenAnswersFromIt() = runTest {
        val tv = FakeShell()
        tv.answer = { if (it.startsWith("{ pm list packages -s; }")) ShellResult("package:sys\n${sep}0\npackage:off\n${sep}0\n", 0) else null }
        val shell = BatchedShell.of(tv, listOf("pm list packages -s", "pm list packages -d", "pm list packages -s"))
        assertEquals(1, tv.ran.size)
        assertEquals("{ pm list packages -s; }; echo \"${sep}$?\"; { pm list packages -d; }; echo \"${sep}$?\"", tv.ran.single())
        assertEquals("package:off\n", shell.shell("pm list packages -d").output)
        assertEquals(1, tv.ran.size)
        // Anything not in the batch still goes to the TV.
        shell.shell("pm list packages")
        assertEquals(listOf("pm list packages"), tv.ran.drop(1))
    }

    @Test fun theAppListsReadsAreOneLine() = runTest {
        val tv = FakeShell()
        tv.answer = { if (it.startsWith("{ ")) ShellResult("${sep}0\n".repeat(10), 0) else null }
        val shell = BatchedShell.appList(tv)
        Actions(shell).homePackages()
        Actions(shell).homeOptions()
        Actions(shell).launchable()
        Actions(shell).tvLaunchable()
        shell.shell(Commands.SYSTEM_PACKAGES)
        shell.shell("pm list packages -d")
        shell.shell(Commands.USER_PACKAGES)
        shell.shell("pm list packages --show-versioncode")
        Actions(shell).serviceOn("a/b")
        assertEquals(1, tv.ran.size)
    }
}
