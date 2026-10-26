// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.tellyscope.adb.RemoteKey
import app.tellyscope.data.AppInfo
import app.tellyscope.data.DeviceState
import app.tellyscope.data.Dock
import app.tellyscope.data.Docked
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class RemoteActions(
    /** Suspends until the TV answers, so a held key never queues up presses. False when it didn't take the key. */
    val press: suspend (RemoteKey) -> Boolean = { true },
    /** Returns why the text can't be typed, or null once it's sent. */
    val type: (String) -> String? = { null },
    val reboot: () -> Unit = {},
    val back: () -> Unit = {},
    /** A restart running late: try the TV again and wait another minute. */
    val retryRestart: () -> Unit = {},
    /** Give up waiting for a restart that never came back. */
    val stopWaiting: () -> Unit = {},
    val launch: (Docked) -> Unit = {},
    val setDock: (List<Docked>) -> Unit = {},
    /** Turns the TV's picture off (true) or back on (false). */
    val screenOff: (Boolean) -> Unit = {},
    /** The first-visit tip on Screen off was shown. */
    val screenOffTipSeen: () -> Unit = {},
)

/** A held key repeats after this, then every [REPEAT_EVERY_MS], or slower if the TV is slower. */
private const val REPEAT_AFTER_MS = 400L

/** Android's own key repeat rate. The key helper answers in about 20 ms, which would skip whole lists. */
internal const val REPEAT_EVERY_MS = 50L

/**
 * One press, then repeats while [held] for keys that repeat. Stops at the first key the TV didn't
 * take (refused, dropped, or no TV selected).
 */
internal suspend fun repeatWhileHeld(key: RemoteKey, held: () -> Boolean, press: suspend (RemoteKey) -> Boolean) {
    if (!press(key) || !key.repeats) return
    delay(REPEAT_AFTER_MS)
    var last = System.nanoTime()
    while (held() && press(key)) {
        delay((REPEAT_EVERY_MS - (System.nanoTime() - last) / 1_000_000).coerceAtLeast(0))
        last = System.nanoTime()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    state: UiState,
    actions: RemoteActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val device = state.selected ?: return
    val restart = state.restarting[device.id]
    val live = device.state == DeviceState.Online && restart == null
    val screenOff = device.id in state.screenOff
    // Android 14 moved the call that turns the picture off out of the shell's reach.
    val screenControl = (device.android?.substringBefore('.')?.toIntOrNull() ?: 0) < 14 || screenOff
    // The keyboard shrinks this screen instead of panning the window, which jumped on every keystroke.
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        if (window == null) return@DisposableEffect onDispose {}
        val before = window.attributes.softInputMode
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { window.setSoftInputMode(before) }
    }
    var confirmReboot by rememberSaveable { mutableStateOf(false) }
    if (confirmReboot) RebootDialog(onRestart = { confirmReboot = false; actions.reboot() }, onDismiss = { confirmReboot = false })
    // Hide keeps a restart in the top bar; the dialog comes back when the wait moves on, or on a tap there.
    val stage = restart?.let { "${it.since}/${it.waitFrom}/${it.phase(state.now)}" }
    var hidden by rememberSaveable { mutableStateOf<String?>(null) }
    if (restart != null && hidden != stage) {
        RestartDialog(
            restart, state.now, name(device),
            onHide = { hidden = stage },
            onRetry = actions.retryRestart,
            onStop = actions.stopWaiting,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    when {
                        restart != null && hidden == stage -> RestartStatus(late = restart.phase(state.now) != Restart.Phase.Waiting, onClick = { hidden = null })
                        // In place of the title, so the keys below never move.
                        screenOff -> ScreenOffStatus()
                        else -> Text("Remote")
                    }
                },
                navigationIcon = {
                    // A chevron, not an arrow, so it can't be mistaken for the TV's Back key.
                    IconButton(onClick = actions.back) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back to the TV's page") }
                },
                actions = {
                    if (screenControl) ScreenOffButton(screenOff, live, showTip = state.screenOffTip, actions)
                    IconButton(onClick = { confirmReboot = true }, enabled = live) { Icon(RemoteIcons.RestartAlt, contentDescription = "Restart TV") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (restart == null && device.state != DeviceState.Online) Text(
                Copy.REMOTE_OFFLINE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CompositionLocalProvider(LocalKeyHaptics provides state.keyHaptics) { Column(
                Modifier.alpha(if (live) 1f else 0.38f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                DPad(live, actions.press)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RoundKey(RemoteKey.BACK, "Back", live, actions.press, Modifier.align(Alignment.CenterStart)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    RoundKey(RemoteKey.HOME, "Home", live, actions.press, size = 80.dp) { Icon(Icons.Outlined.Home, null, Modifier.size(32.dp)) }
                    RoundKey(RemoteKey.MENU, "Menu", live, actions.press, Modifier.align(Alignment.CenterEnd)) { Icon(Icons.Default.Menu, null) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundKey(RemoteKey.MUTE, "Mute or unmute", live, actions.press) { Icon(RemoteIcons.VolumeOff, null) }
                    // One split pill: two halves of the same key, so the gap stays narrow.
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val (outer, inner) = 32.dp to 4.dp
                        KeySurface(
                            RemoteKey.VOLUME_DOWN, "Volume down", live, actions.press, RoundedCornerShape(outer, inner, inner, outer),
                            MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f).height(64.dp),
                        ) { Icon(RemoteIcons.VolumeDown, null) }
                        KeySurface(
                            RemoteKey.VOLUME_UP, "Volume up", live, actions.press, RoundedCornerShape(inner, outer, outer, inner),
                            MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f).height(64.dp),
                        ) { Icon(RemoteIcons.VolumeUp, null) }
                    }
                }
                // Clear space above and below, so a thumb aiming at a volume key doesn't open an app.
                DockSection(state.device.dock, state.device.apps, name(device), live, actions, Modifier.padding(vertical = 4.dp))
                Keyboard(live, actions.type, actions.press)
            } }
        }
    }
}

/** The four arrows around a big OK, shaped like the remote in the user's other hand. */
@Composable
private fun DPad(enabled: Boolean, press: suspend (RemoteKey) -> Boolean) {
    Surface(Modifier.size(272.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box {
            val arrow = Modifier.size(80.dp)
            val tint = MaterialTheme.colorScheme.onSurface
            KeySurface(RemoteKey.UP, "Up", enabled, press, CircleShape, Color.Transparent, arrow.align(Alignment.TopCenter)) {
                Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(40.dp), tint = tint)
            }
            KeySurface(RemoteKey.DOWN, "Down", enabled, press, CircleShape, Color.Transparent, arrow.align(Alignment.BottomCenter)) {
                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(40.dp), tint = tint)
            }
            KeySurface(RemoteKey.LEFT, "Left", enabled, press, CircleShape, Color.Transparent, arrow.align(Alignment.CenterStart)) {
                Icon(Icons.Default.KeyboardArrowLeft, null, Modifier.size(40.dp), tint = tint)
            }
            KeySurface(RemoteKey.RIGHT, "Right", enabled, press, CircleShape, Color.Transparent, arrow.align(Alignment.CenterEnd)) {
                Icon(Icons.Default.KeyboardArrowRight, null, Modifier.size(40.dp), tint = tint)
            }
            KeySurface(
                RemoteKey.OK, "OK", enabled, press, CircleShape, MaterialTheme.colorScheme.primary,
                Modifier.size(112.dp).align(Alignment.Center),
            ) {}
        }
    }
}

/** An icon-only key; [label] is what TalkBack reads. */
@Composable
private fun RoundKey(
    key: RemoteKey,
    label: String,
    enabled: Boolean,
    press: suspend (RemoteKey) -> Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    icon: @Composable () -> Unit,
) = KeySurface(key, label, enabled, press, CircleShape, MaterialTheme.colorScheme.secondaryContainer, modifier.size(size), icon)

/** Off in Settings, a key press doesn't vibrate. */
private val LocalKeyHaptics = staticCompositionLocalOf { true }

/**
 * One remote key. Not a Button: a held [RemoteKey.repeats] key has to keep sending until the
 * finger lifts, and each repeat waits for the TV to take the last one so nothing piles up.
 */
@Composable
private fun KeySurface(
    key: RemoteKey,
    label: String,
    enabled: Boolean,
    press: suspend (RemoteKey) -> Boolean,
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val haptics = LocalKeyHaptics.current
    val interactions = remember { MutableInteractionSource() }
    Surface(
        shape = shape,
        color = color,
        modifier = modifier
            .clip(shape)
            .indication(interactions, ripple())
            .semantics {
                role = Role.Button
                contentDescription = label
                if (enabled) onClick { scope.launch { press(key) }; true }
            }
            .pointerInput(key, enabled, haptics) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val p = PressInteraction.Press(down.position)
                    interactions.tryEmit(p)
                    if (haptics) haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    var held = true
                    scope.launch { repeatWhileHeld(key, { held }, press) }
                    waitForUpOrCancellation()
                    held = false
                    interactions.tryEmit(PressInteraction.Release(p))
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun Keyboard(enabled: Boolean, type: (String) -> String?, press: suspend (RemoteKey) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    fun send() {
        error = type(text)
        if (error == null) text = ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; error = null },
        enabled = enabled,
        label = { Text("Type on the TV") },
        supportingText = { Text(error ?: Copy.TYPE_HINT) },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = {
            Row {
                // A key, not a button: holding it repeats like the D-pad. Each Delete also takes the
                // last character off the phone's field.
                KeySurface(
                    RemoteKey.DEL, Copy.BACKSPACE, enabled,
                    { key -> text = text.dropLast(1); error = null; press(key) },
                    CircleShape, Color.Transparent, Modifier.size(48.dp),
                ) { Icon(RemoteIcons.Backspace, null, tint = LocalContentColor.current.copy(alpha = if (enabled) 1f else 0.38f)) }
                IconButton(onClick = ::send, enabled = enabled && text.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send to the TV")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun RebootDialog(onRestart: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(RemoteIcons.RestartAlt, contentDescription = null) },
        title = { Text(Copy.REBOOT_TITLE) },
        text = { Text(Copy.REBOOT_TEXT) },
        confirmButton = { Button(onClick = onRestart) { Text("Restart") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shown instead of the offline card while a TV restarts: it's expected, not an error. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RestartingCard(restart: Restart, now: Long) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Restarting", style = MaterialTheme.typography.titleSmall)
                Text(Copy.restarting((now - restart.since) / 60_000), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * A restart from the Remote: waiting for a minute, then late, then, after a Retry's minute,
 * lost. Hide and the first Close leave it waiting in the top bar; the last Close stops waiting.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RestartDialog(restart: Restart, now: Long, tvName: String, onHide: () -> Unit, onRetry: () -> Unit, onStop: () -> Unit) {
    val c = MaterialTheme.colorScheme
    when (restart.phase(now)) {
        Restart.Phase.Waiting -> AlertDialog(
            onDismissRequest = onHide,
            // Wavy, not the loading indicator: that one is for waits under a few seconds.
            icon = { CircularWavyProgressIndicator(Modifier.size(48.dp)) },
            title = { Text(Copy.RESTARTING_TITLE) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Copy.RESTARTING_TEXT)
                    Text(Copy.waitingFor(tvName, now - restart.since), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = onHide) { Text("Hide") } },
        )
        Restart.Phase.Late -> AlertDialog(
            onDismissRequest = onHide,
            icon = { StatusShape(RemoteIcons.HourglassTop, c.secondaryContainer, c.onSecondaryContainer) },
            title = { Text(Copy.RESTART_LATE_TITLE) },
            text = { Text(Copy.RESTART_LATE_TEXT) },
            confirmButton = { Button(onClick = onRetry) { Text("Retry") } },
            dismissButton = { TextButton(onClick = onHide) { Text("Close") } },
        )
        Restart.Phase.Lost -> AlertDialog(
            onDismissRequest = onStop,
            icon = { StatusShape(RemoteIcons.LinkOff, c.errorContainer, c.onErrorContainer) },
            title = { Text(Copy.restartLost(tvName)) },
            text = { Text(Copy.RESTART_LOST_TEXT) },
            confirmButton = { Button(onClick = onRetry) { Text("Retry") } },
            dismissButton = { TextButton(onClick = onStop) { Text("Close") } },
        )
    }
}

/** A filled glyph in the cookie the TV header uses, for a state that needs a look rather than a spinner. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusShape(icon: ImageVector, container: Color, content: Color) {
    Box(Modifier.size(56.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
    }
}

/** Filled while the picture is off; a tap turns it back on. Explains itself on the first visit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenOffButton(off: Boolean, live: Boolean, showTip: Boolean, actions: RemoteActions) {
    val tip = rememberTooltipState(isPersistent = true)
    LaunchedEffect(showTip) {
        if (showTip) {
            tip.show()
            actions.screenOffTipSeen()
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = {
            PlainTooltip {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(Copy.SCREEN_OFF, style = MaterialTheme.typography.labelLarge)
                    Text(Copy.SCREEN_OFF_TIP)
                }
            }
        },
        state = tip,
        enableUserInput = false,
    ) {
        FilledIconToggleButton(
            checked = off,
            onCheckedChange = actions.screenOff,
            enabled = live,
            colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = Color.Transparent),
        ) { Icon(RemoteIcons.TvOff, contentDescription = if (off) Copy.SCREEN_ON else Copy.SCREEN_OFF) }
    }
}

@Composable
private fun ScreenOffStatus() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(Copy.SCREEN_OFF, style = MaterialTheme.typography.titleMedium)
        Text(
            " \u00b7 ${Copy.SCREEN_OFF_WAKE}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The top bar's title while a hidden restart runs, overdue once [late]; a tap brings its dialog back. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RestartStatus(late: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = "Show the restart", onClick = onClick).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (late) {
            Icon(RemoteIcons.HourglassTop, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
        } else {
            LoadingIndicator(Modifier.size(28.dp))
        }
        Text(if (late) Copy.RESTART_LATE_STATUS else Copy.RESTARTING_TITLE)
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Up to [Dock.MAX] apps in fixed slots from the left, with "Add to dock" under them until the row
 * is full. A tap opens the app on the TV; a long press opens its menu, and moving while still
 * holding drags it to another slot.
 */
@Composable
private fun DockSection(dock: List<Docked>, apps: List<AppInfo>, tvName: String, enabled: Boolean, actions: RemoteActions, modifier: Modifier = Modifier) {
    // Null: closed. Empty: adding. A package: swapping that app for another.
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    picking?.let { swap ->
        DockPicker(
            dock, apps, tvName, swapping = dock.firstOrNull { it.packageName == swap },
            onPick = { app ->
                picking = null
                actions.setDock(if (swap.isEmpty()) Dock.add(dock, app) else Dock.replace(dock, swap, app))
            },
            onDismiss = { picking = null },
        )
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (dock.isNotEmpty()) DockRow(dock, apps, enabled, actions, onSwap = { picking = it.packageName })
        if (dock.size < Dock.MAX) {
            OutlinedButton(
                onClick = { picking = "" },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(Copy.ADD_TO_DOCK, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DockRow(dock: List<Docked>, apps: List<AppInfo>, enabled: Boolean, actions: RemoteActions, onSwap: (Docked) -> Unit) {
    var menu by remember { mutableStateOf<String?>(null) }
    // The app being dragged, and how far; the others make room for it as it goes.
    var lifted by remember { mutableStateOf<String?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(80.dp)) {
        val slot = constraints.maxWidth / Dock.MAX.toFloat()
        fun from() = dock.indexOfFirst { it.packageName == lifted }
        fun to() = (from() + (dragX / slot).roundToInt()).coerceIn(0, dock.lastIndex)
        val order = if (from() < 0) dock else Dock.move(dock, from(), to())
        dock.forEachIndexed { i, app -> key(app.packageName) {
            val up = app.packageName == lifted
            val x by animateFloatAsState(order.indexOf(app) * slot, label = "dock slot")
            val info = apps.firstOrNull { it.packageName == app.packageName }
                ?: AppInfo(app.packageName, app.label, null, false, true, null, "")
            Box(
                Modifier
                    .width(maxWidth / Dock.MAX)
                    .offset { IntOffset((if (up) i * slot + dragX else x).roundToInt(), 0) }
                    .zIndex(if (up) 1f else 0f),
            ) {
                val moves = listOfNotNull(
                    CustomAccessibilityAction("Move left") { actions.setDock(Dock.move(dock, i, i - 1)); true }.takeIf { i > 0 },
                    CustomAccessibilityAction("Move right") { actions.setDock(Dock.move(dock, i, i + 1)); true }.takeIf { i < dock.lastIndex },
                )
                DockTile(
                    info, up, enabled, moves,
                    onOpen = { actions.launch(app) },
                    onHold = { menu = app.packageName; lifted = app.packageName; dragX = 0f },
                    onDrag = { dx ->
                        dragX = dx
                        // A real move means reordering, so the menu gets out of the way.
                        if (abs(dx) > slot / 4) menu = null
                    },
                    onDrop = {
                        if (from() >= 0 && to() != from()) actions.setDock(Dock.move(dock, from(), to()))
                        lifted = null
                        dragX = 0f
                    },
                    onMenu = { menu = app.packageName },
                )
                DropdownMenu(menu == app.packageName, onDismissRequest = { menu = null }, shape = MaterialTheme.shapes.large) {
                    Row(Modifier.padding(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(info, Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, style = MaterialTheme.typography.titleSmall)
                    }
                    MenuBlock {
                        DropdownMenuItem(
                            text = { Text(Copy.DOCK_CHANGE) },
                            leadingIcon = { Icon(RemoteIcons.SwapHoriz, null) },
                            onClick = { menu = null; onSwap(app) },
                        )
                        DropdownMenuItem(
                            text = { Text(Copy.DOCK_REMOVE, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(RemoteIcons.RemoveCircle, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = null; actions.setDock(Dock.remove(dock, app.packageName)) },
                        )
                    }
                    Text(
                        Copy.DOCK_REORDER,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
            }
        } }
    }
}

/** One docked app: its icon and name, lifted a size up while held. */
@Composable
private fun DockTile(
    app: AppInfo,
    lifted: Boolean,
    enabled: Boolean,
    /** Reordering for a screen reader, which can't drag. */
    moves: List<CustomAccessibilityAction>,
    onOpen: () -> Unit,
    onHold: () -> Unit,
    onDrag: (Float) -> Unit,
    onDrop: () -> Unit,
    onMenu: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (lifted) 1.1f else 1f, label = "dock lift")
    // The gesture outlives a recomposition; it must call the newest handlers, which see the newest dock.
    val open by rememberUpdatedState(onOpen)
    val hold by rememberUpdatedState(onHold)
    val move by rememberUpdatedState(onDrag)
    val drop by rememberUpdatedState(onDrop)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics {
                role = Role.Button
                contentDescription = app.label
                if (enabled) {
                    onClick("Open on the TV") { onOpen(); true }
                    onLongClick("Change or remove") { onMenu(); true }
                    customActions = moves
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Up before the long-press time is a tap; still down after it is a hold.
                    var up: PointerInputChange? = null
                    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { up = waitForUpOrCancellation(); false } ?: true
                    if (!held) {
                        up?.let { it.consume(); open() }
                        return@awaitEachGesture
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    hold()
                    var dx = 0f
                    drag(down.id) { change ->
                        dx += change.positionChange().x
                        change.consume()
                        move(dx)
                    }
                    drop()
                }
            }
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.shadow(if (lifted) 6.dp else 0.dp, MaterialTheme.shapes.medium)) { AppIcon(app, Modifier.size(48.dp)) }
        Text(
            app.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** The apps a launcher can open, to add to the dock or to put in [swapping]'s place. Docked ones show as Added. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DockPicker(dock: List<Docked>, apps: List<AppInfo>, tvName: String, swapping: Docked?, onPick: (AppInfo) -> Unit, onDismiss: () -> Unit) {
    val choices = Dock.choices(apps)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
            Text(swapping?.let { Copy.dockSwap(it.label) } ?: Copy.DOCK_PICK_TITLE, style = MaterialTheme.typography.titleLarge)
            Text(Copy.dockPickFrom(tvName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (choices.isEmpty()) {
            Text(
                Copy.DOCK_NO_APPS,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            return@ModalBottomSheet
        }
        LazyColumn {
            items(choices, key = { it.packageName }) { app ->
                val added = dock.any { it.packageName == app.packageName }
                ListItem(
                    headlineContent = { Text(app.label) },
                    leadingContent = { AppIcon(app) },
                    trailingContent = if (added) ({ Text(Copy.DOCK_ADDED, style = MaterialTheme.typography.labelMedium) }) else null,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.alpha(if (added) 0.38f else 1f).clickable(enabled = !added) { onPick(app) },
                )
            }
            item {
                Text(
                    Copy.DOCK_PICK_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
    }
}

private object RemoteIcons {
    // Absolute commands only: Compose's path parser misplaces a relative move after a close.
    val TvOff = icon(
        "TvOff",
        "M1 3.54L2.53 5.07C1.65 5.28 1 6.06 1 7L1 19C1 20.1 1.9 21 3 21L18.46 21L20.46 23L21.72 21.73L2.27 2.27L1 3.54Z" +
            "M3 19L3 7L4.46 7L16.46 19L3 19Z" +
            "M21 5L13.42 5L16.71 1.7L16 1L12 5L8 1L7.3 1.7L10.58 5L7.52 5L9.52 7L21 7L21 18.48L22.65 20.13C22.87 19.81 23 19.42 23 19L23 7C23 5.89 22.11 5 21 5Z",
    )
    val Backspace = icon(
        "Backspace",
        "M22 3L7 3C6.31 3 5.77 3.35 5.41 3.88L0 12L5.41 20.11C5.77 20.64 6.31 21 7 21L22 21C23.1 21 24 20.1 24 19L24 5C24 3.9 23.1 3 22 3Z" +
            "M19 15.59L17.59 17L14 13.41L10.41 17L9 15.59L12.59 12L9 8.41L10.41 7L14 10.59L17.59 7L19 8.41L15.41 12L19 15.59Z",
    )
    val RestartAlt = icon(
        "RestartAlt",
        "M12 5L12 2L8 6L12 10L12 7C15.31 7 18 9.69 18 13C18 15.97 15.83 18.43 13 18.91L13 20.93C16.95 20.44 20 17.08 20 13C20 8.58 16.42 5 12 5Z" +
            "M6 13C6 11.35 6.67 9.85 7.76 8.76L6.34 7.34C4.9 8.79 4 10.79 4 13C4 17.08 7.05 20.44 11 20.93L11 18.91C8.17 18.43 6 15.97 6 13Z",
    )
    val HourglassTop = icon(
        "HourglassTop",
        "M6 2L6.01 8L10 12L6.01 16.01L6 22L18 22L18 16L14 12L18 8.01L18 2L6 2Z M16 16.5L16 20L8 20L8 16.5L12 12.5L16 16.5Z",
    )
    val LinkOff = icon(
        "LinkOff",
        "M17 7L13 7L13 8.9L17 8.9C18.71 8.9 20.1 10.29 20.1 12C20.1 13.43 19.12 14.63 17.79 14.98L19.25 16.44C20.88 15.61 22 13.95 22 12C22 9.24 19.76 7 17 7Z" +
            "M16 11L13.81 11L15.81 13L16 13Z" +
            "M2 4.27L5.11 7.38C3.29 8.12 2 9.91 2 12C2 14.76 4.24 17 7 17L11 17L11 15.1L7 15.1C5.29 15.1 3.9 13.71 3.9 12C3.9 10.41 5.11 9.1 6.66 8.93" +
            "L8.73 11L8 11L8 13L10.73 13L13 15.27L13 17L14.73 17L18.74 21L20 19.74L3.27 3L2 4.27Z",
    )
    val VolumeUp = icon(
        "VolumeUp",
        "M3 9L3 15L7 15L12 20L12 4L7 9Z M16.5 12C16.5 10.23 15.48 8.71 14 7.97L14 16.02C15.48 15.29 16.5 13.77 16.5 12Z" +
            "M14 3.23L14 5.29C16.89 6.15 19 8.83 19 12C19 15.17 16.89 17.85 14 18.71L14 20.77C18.01 19.86 21 16.28 21 12C21 7.72 18.01 4.14 14 3.23Z",
    )
    val VolumeDown = icon(
        "VolumeDown",
        "M18.5 12C18.5 10.23 17.48 8.71 16 7.97L16 16.02C17.48 15.29 18.5 13.77 18.5 12Z M5 9L5 15L9 15L14 20L14 4L9 9Z",
    )
    val VolumeOff = icon(
        "VolumeOff",
        "M16.5 12C16.5 10.23 15.48 8.71 14 7.97L14 10.18L16.45 12.63C16.48 12.43 16.5 12.22 16.5 12Z" +
            "M19 12C19 12.94 18.8 13.82 18.46 14.64L19.97 16.15C20.63 14.91 21 13.5 21 12C21 7.72 18.01 4.14 14 3.23L14 5.29C16.89 6.15 19 8.83 19 12Z" +
            "M4.27 3L3 4.27L7.73 9L3 9L3 15L7 15L12 20L12 13.27L16.25 17.52C15.58 18.04 14.83 18.45 14 18.7L14 20.76C15.38 20.45 16.63 19.81 17.69 18.95" +
            "L19.73 21L21 19.73L12 10.73L4.27 3Z M12 4L9.91 6.09L12 8.18L12 4Z",
    )
    val SwapHoriz = icon("SwapHoriz", "M6.99 11L3 15L6.99 19L6.99 16L14 16L14 14L6.99 14L6.99 11Z M21 9L17.01 5L17.01 8L10 8L10 10L17.01 10L17.01 13L21 9Z")
    val RemoveCircle = icon(
        "RemoveCircle",
        "M7 11L7 13L17 13L17 11L7 11Z" +
            "M12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 12 2Z" +
            "M12 20C7.59 20 4 16.41 4 12C4 7.59 7.59 4 12 4C16.41 4 20 7.59 20 12C20 16.41 16.41 20 12 20Z",
    )

    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), pathFillType = PathFillType.NonZero, fill = SolidColor(Color.Black))
        .build()
}
