// SPDX-License-Identifier: GPL-3.0-only
// Download, ErrorOutline, InfoOutline and Code are the Material Icons "download", "error_outline", "info" (outlined)
// and "code" (Apache-2.0), see NOTICE.

package app.tellyscope.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tellyscope.data.Action
import app.tellyscope.data.AppInfo
import app.tellyscope.data.Device

/** The Play Store's package: a TV without it (Fire TV) gets no card and no Tweaks row. */
const val PLAY_STORE = "com.android.vending"

/**
 * Install a home screen is offered on the Apps tab while the TV has the Play Store and no launcher
 * was added, Home or not, and there's no second launcher, until Not now; Tweaks offers it whenever
 * the Store is there, launcher added or not, so there's always a way to try another.
 */
internal fun hasPlayStore(apps: List<AppInfo>) = apps.any { it.packageName == PLAY_STORE && it.enabled }

/** A launcher someone added is on the TV, Home or not: one on `launchers.json`, or one they installed that answers Home. */
internal fun hasAddedLauncher(apps: List<AppInfo>) = apps.any { it.listedLauncher || (it.removable && (it.home || it.answersHome)) }

internal fun offerChoose(apps: List<AppInfo>) = hasPlayStore(apps) && !hasAddedLauncher(apps)

internal fun offerLauncher(apps: List<AppInfo>, hidden: Boolean) =
    !hidden && offerChoose(apps) && apps.none { it.homeInstead != null || it.homeBack != null }

data class LauncherActions(
    val close: () -> Unit = {},
    /** Opens the Store on the TV at that launcher's page, and watches for the install. */
    val install: (String) -> Unit = {},
    /** Back to the picker with that launcher still chosen. */
    val chooseAnother: (String) -> Unit = {},
    /** Yes on the installed launcher, with its accessibility service or without. */
    val switch: (service: Boolean) -> Unit = {},
    val later: () -> Unit = {},
)

/** The Apps tab's card: a launcher without ads, from the Play Store. Not now puts it away on this TV. */
@Composable
fun LauncherCard(current: String?, onNotNow: () -> Unit, onChoose: () -> Unit) {
    HomeNoteCard(Icons.Outlined.Home, Copy.BETTER_HOME, Copy.betterHomeText(current), Copy.NOT_NOW, onNotNow, Copy.CHOOSE_ONE, onChoose)
}

/**
 * Install a home screen, as one full screen: the picker, then the wait while the TV shows the
 * Play Store, then the switch, which ends in the same yes for the accessibility service as Set as
 * default. A TV that didn't open the Store, and a Store closed without installing, each say so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(device: Device, flow: LauncherFlow, apps: List<AppInfo>, actions: LauncherActions) {
    var chosen by rememberSaveable(flow.step, flow.pkg) { mutableStateOf(flow.pkg) }
    var askService by rememberSaveable(flow.step) { mutableStateOf(false) }
    val label = launcherName(flow.pkg)
    val app = apps.firstOrNull { it.packageName == flow.pkg }
    val current = app?.let { homeLabel(apps, it) }
    if (askService && app != null && current != null) {
        ServiceDialog(
            app.label, current,
            onTurnOn = { actions.switch(true) },
            dismissLabel = Copy.SWITCH_WITHOUT, onOther = { actions.switch(false) }, onDismiss = { askService = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (flow.step == LauncherFlow.Step.Pick) Copy.CHOOSE_HOME else Copy.INSTALL_HOME) },
                navigationIcon = { IconButton(onClick = actions.close) { Icon(Icons.Default.Close, contentDescription = "Close") } },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (flow.step) {
                    LauncherFlow.Step.Pick -> Button(onClick = { actions.install(chosen) }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(OpenInNew, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(Copy.OPEN_IN_STORE)
                    }
                    LauncherFlow.Step.Waiting -> TextButton(onClick = { actions.install(flow.pkg) }) { Text(Copy.OPEN_STORE_AGAIN) }
                    LauncherFlow.Step.Installed -> {
                        TextButton(onClick = actions.later) { Text(Copy.NOT_NOW) }
                        Button(onClick = { if (app?.homeService == true) askService = true else actions.switch(false) }, enabled = current != null) {
                            Text(Copy.confirm(Action.Home))
                        }
                    }
                    LauncherFlow.Step.NoStore -> {
                        TextButton(onClick = { actions.chooseAnother(flow.pkg) }) { Text(Copy.CHOOSE_ANOTHER) }
                        Button(onClick = { actions.install(flow.pkg) }) { Text(Copy.TRY_AGAIN) }
                    }
                    LauncherFlow.Step.Left -> {
                        TextButton(onClick = { actions.chooseAnother(flow.pkg) }) { Text(Copy.CHOOSE_ANOTHER) }
                        Button(onClick = { actions.install(flow.pkg) }) { Text(Copy.OPEN_STORE_AGAIN) }
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(DeviceIcons.Tv, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(connectTitle(device), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val c = MaterialTheme.colorScheme
            when (flow.step) {
                LauncherFlow.Step.Pick -> Picker(chosen) { chosen = it }
                LauncherFlow.Step.Waiting -> {
                    Hero(LauncherIcons.Download, c.secondaryContainer, c.onSecondaryContainer)
                    Headed(Copy.INSTALL_IT, bold(Copy.installItText(label), label, "Install"))
                    Surface(shape = MaterialTheme.shapes.large, color = c.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                Text(Copy.storeOn(name(device)), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                            }
                            WaitingLine()
                        }
                    }
                    Text(Copy.DOWNLOADS_TAKE, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
                LauncherFlow.Step.Installed -> {
                    Hero(Icons.Default.Check, c.secondaryContainer, c.onSecondaryContainer)
                    val explain = if (app != null && current != null && app.homeInstead != null) {
                        " " + Copy.explainHome(app.label, app.packageName, current, app.homeInstead, app.homeService)
                    } else {
                        ""
                    }
                    Headed(Copy.MAKE_IT_HOME, AnnotatedString(Copy.isInstalled(label) + explain))
                }
                LauncherFlow.Step.NoStore -> {
                    Hero(LauncherIcons.ErrorOutline, c.errorContainer, c.onErrorContainer)
                    Headed(Copy.NO_STORE, bold(Copy.noStoreText(name(device), label), label))
                    Surface(shape = MaterialTheme.shapes.large, color = c.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(16.dp)) { WaitingLine() }
                    }
                }
                LauncherFlow.Step.Left -> {
                    Hero(LauncherIcons.InfoOutline, c.surfaceContainerHighest, c.onSurfaceVariant)
                    Headed(Copy.notInstalled(label), AnnotatedString(Copy.NOT_INSTALLED_TEXT))
                }
            }
        }
    }
}

/** The four launchers as one segmented list, one chosen: rounded ends, small gaps, the chosen one filled. */
@Composable
private fun Picker(chosen: String, onChoose: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Text(Copy.CHOOSE_HOME_TEXT, style = MaterialTheme.typography.bodyLarge)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(Copy.RECOMMENDED, style = MaterialTheme.typography.labelLarge, color = c.primary, modifier = Modifier.padding(bottom = 8.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RECOMMENDED.forEachIndexed { i, l ->
                val big = 16.dp
                val small = 4.dp
                val shape = RoundedCornerShape(
                    topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small,
                    bottomStart = if (i == RECOMMENDED.lastIndex) big else small, bottomEnd = if (i == RECOMMENDED.lastIndex) big else small,
                )
                val on = l.pkg == chosen
                Surface(
                    shape = shape,
                    color = if (on) c.secondaryContainer else c.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().selectable(on, role = Role.RadioButton) { onChoose(l.pkg) },
                ) {
                    Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, style = MaterialTheme.typography.bodyLarge)
                            Text(l.about, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (l.accessibility) DeviceIcons.Accessibility else LauncherIcons.Code, null, Modifier.size(14.dp), tint = c.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Text(if (l.accessibility) Copy.USES_SERVICE else Copy.NO_SERVICE, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                            }
                        }
                        RadioButton(selected = on, onClick = null)
                    }
                }
            }
        }
        Text(Copy.NOT_AFFILIATED, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Hero(icon: ImageVector, bg: Color, fg: Color) {
    Box(Modifier.size(64.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(32.dp), tint = fg)
    }
}

@Composable
private fun Headed(title: String, text: AnnotatedString) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** No progress to show: the TV says nothing until the launcher is in. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WaitingLine() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LoadingIndicator(Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(Copy.WAITING_INSTALL, style = MaterialTheme.typography.bodyMedium)
    }
}

/** [text] with each of [words] in medium weight, where it first appears. */
private fun bold(text: String, vararg words: String) = buildAnnotatedString {
    append(text)
    words.forEach { w -> text.indexOf(w).takeIf { it >= 0 }?.let { addStyle(SpanStyle(fontWeight = FontWeight.Medium), it, it + w.length) } }
}

private object LauncherIcons {
    // Absolute commands only: Compose's path parser misplaces a relative move after a close.
    val Download = icon("Download", "M5 20H19V18H5ZM19 9H15V3H9V9H5L12 16Z")
    val ErrorOutline = icon("ErrorOutline", "M12 2A10 10 0 1 0 12 22A10 10 0 1 0 12 2ZM12 4A8 8 0 1 1 12 20A8 8 0 1 1 12 4ZM11 7H13V13H11ZM11 15H13V17H11Z")
    val InfoOutline = icon("InfoOutline", "M12 2A10 10 0 1 0 12 22A10 10 0 1 0 12 2ZM12 4A8 8 0 1 1 12 20A8 8 0 1 1 12 4ZM11 11H13V17H11ZM11 7H13V9H11Z")
    val Code = icon("Code", "M9.4 16.6L4.8 12L9.4 7.4L8 6L2 12L8 18ZM14.6 16.6L19.2 12L14.6 7.4L16 6L22 12L16 18Z")

    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
        .build()
}
