// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.AppInfo
import app.tellyscope.data.Dock
import app.tellyscope.data.DockBook
import app.tellyscope.data.Docked
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DockTest {
    private fun app(pkg: String, enabled: Boolean = true, launchable: Boolean = true) =
        AppInfo(pkg, pkg.substringAfterLast('.'), null, false, enabled, null, "", launchable = launchable)

    private val youtube = Docked("com.google.android.youtube.tv", "YouTube")
    private val netflix = Docked("com.netflix.ninja", "Netflix")
    private val smarttube = Docked("org.smarttube.stable", "SmartTube")

    @Test fun addStopsAtFourAndNeverTwice() {
        val four = (1..4).map { Docked("com.example.app$it", "App $it") }
        assertEquals(four, Dock.add(four, app("com.example.app5")))
        assertEquals(listOf(youtube), Dock.add(listOf(youtube), app(youtube.packageName)))
        assertEquals(listOf(youtube, Docked("com.example.kodi", "kodi")), Dock.add(listOf(youtube), app("com.example.kodi")))
    }

    @Test fun changeKeepsTheSlot() {
        val dock = listOf(youtube, netflix, smarttube)
        assertEquals(listOf(youtube, Docked("com.example.kodi", "kodi"), smarttube), Dock.replace(dock, netflix.packageName, app("com.example.kodi")))
    }

    @Test fun removeAndMove() {
        val dock = listOf(youtube, netflix, smarttube)
        assertEquals(listOf(youtube, smarttube), Dock.remove(dock, netflix.packageName))
        assertEquals(listOf(netflix, smarttube, youtube), Dock.move(dock, 0, 2))
        assertEquals(listOf(smarttube, youtube, netflix), Dock.move(dock, 2, 0))
        assertEquals(dock, Dock.move(dock, 1, 3)) // off the end: nothing moves
    }

    @Test fun anUninstalledAppLeavesADisabledOneStays() {
        val dock = listOf(youtube, netflix, smarttube)
        val apps = listOf(app(youtube.packageName), app(netflix.packageName, enabled = false))
        assertEquals(listOf(youtube, netflix) to listOf(smarttube), Dock.prune(dock, apps))
    }

    @Test fun anEmptyAppListIsAFailedReadNotAnEmptyTv() {
        val dock = listOf(youtube, netflix)
        assertEquals(dock to emptyList<Docked>(), Dock.prune(dock, emptyList()))
    }

    @Test fun thePickerOffersAppsALauncherCanOpen() {
        val apps = listOf(app("com.a"), app("com.b", launchable = false), app("com.c", enabled = false))
        assertEquals(listOf("com.a"), Dock.choices(apps).map { it.packageName })
    }

    @Test fun thePickerLeavesOutBuiltInPhoneStyleApps() {
        val camera = AppInfo("com.android.camera2", "Camera", null, true, true, null, "", launchable = true, tvHome = false)
        val settings = AppInfo("com.android.tv.settings", "Settings", null, true, true, null, "", launchable = true, tvHome = true)
        val sideloaded = app("com.example.phoneapp").copy(tvHome = false)
        assertEquals(listOf("com.android.tv.settings", "com.example.phoneapp"), Dock.choices(listOf(camera, settings, sideloaded)).map { it.packageName })
    }

    @Test fun theBookKeepsOrderAcrossARestartAndFollowsARekey() {
        val file = File(Files.createTempDirectory("dock").toFile(), "docks.tsv")
        DockBook(file).apply {
            set("192.0.2.10:5555", listOf(netflix, youtube))
            set("other", listOf(Docked("com.x", "Tab\tin name")))
            rekey("192.0.2.10:5555", "serial-1")
        }
        val again = DockBook(file)
        assertEquals(listOf(netflix, youtube), again["serial-1"])
        assertEquals(emptyList<Docked>(), again["192.0.2.10:5555"])
        assertEquals(listOf(Docked("com.x", "Tab in name")), again["other"])
    }
}
