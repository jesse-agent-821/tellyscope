// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tellyscope.data.ScanHit
import app.tellyscope.data.ScanKind
import app.tellyscope.ui.theme.SmallNumber
import app.tellyscope.ui.theme.StatNumber

/**
 * One TV, one thing at a time: the pairing code, the Allow box on the TV, or why it didn't answer.
 * Opened from a found TV's Connect; a successful connect leaves for that TV's Overview on its own.
 * [phoneKey] is this phone's fingerprint, shown so it can be checked against the TV's box.
 * [title] heads a remembered TV by its nickname; a found one goes by its network name.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectScreen(
    hit: ScanHit,
    row: RowState,
    now: Long,
    brand: TvBrand,
    phoneKey: String?,
    onCode: (String) -> Unit,
    onPair: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    title: String? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (row) {
                    RowState.NotAllowed -> {
                        TextButton(onClick = onBack) { Text("Back to TVs") }
                        Button(onClick = onRetry) { Text("Try again") }
                    }
                    is RowState.Code -> {
                        TextButton(onClick = onBack) { Text("Cancel") }
                        val missing = CODE_LENGTH - row.input.length
                        // The button says what's missing instead of sitting there disabled with no reason.
                        Button(onClick = onPair, enabled = !row.busy && missing == 0) {
                            Text(
                                when {
                                    row.busy -> "Pairing…"
                                    missing == 0 -> "Pair"
                                    missing == 1 -> "1 more digit"
                                    else -> "$missing more digits"
                                },
                            )
                        }
                    }
                    else -> TextButton(onClick = onBack) { Text("Cancel") }
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
                Text(
                    title ?: listOfNotNull(hit.hostname ?: "Android device", hit.maker).distinct().joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (row) {
                is RowState.Code -> {
                    Text("Type the pairing code", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "On the TV, open the pairing screen and type the 6 digits it shows.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    InfoBox { Text(marked(brand.pair ?: brands.first().pair.orEmpty()), style = MaterialTheme.typography.bodyMedium) }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CodeBoxes(row.input, onChange = onCode, enabled = !row.busy, isError = row.error != null)
                    }
                    row.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    row.detail?.let { Details(listOf("From the TV" to it)) }
                }
                RowState.NotAllowed -> {
                    Surface(Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Warning, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                    Text("No answer from the TV", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "The TV didn't accept the connection.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Is the TV on and awake?",
                            "Was the box on the TV closed, or Deny pressed? Try again and it asks once more.",
                            "Some TVs switch debugging off after a restart. Turn it back on.",
                        ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                // Wireless debugging TVs that already know this phone connect without asking.
                is RowState.Waiting -> if (hit.kind == ScanKind.Wireless) Connecting() else {
                    Text("Allow on your TV", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        marked(
                            if (phoneKey != null) "A box pops up on your TV. Check its key matches this phone's, tick **Always allow**, then select **Allow**."
                            else "A box pops up on your TV. Tick **Always allow**, then select **Allow**.",
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    phoneKey?.let { key ->
                        InfoBox {
                            Text("This phone's key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // Two lines of eight pairs, so it reads like the TV's and compares at a glance.
                            Text(
                                key.split(':').chunked(8).joinToString("\n") { it.joinToString(":") },
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                    }
                    TvPrompt(phoneKey)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Waiting for the TV… ${((now - row.since) / 1000).coerceAtLeast(0)} s",
                            style = SmallNumber,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                RowState.Found -> Connecting()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Connecting() {
    Text("Connecting", style = MaterialTheme.typography.headlineMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        LoadingIndicator(Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Text("Reaching the TV…", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun InfoBox(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

/**
 * A drawing of the box the TV shows, so people know what to look for. The TV's own colours,
 * not the app theme: it's a picture of another screen. [key] appears where the TV prints it.
 */
@Composable
private fun TvPrompt(key: String?) {
    val accent = Color(0xFF80D4D5)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .background(Color(0xFF2B3131), RoundedCornerShape(10.dp))
                .border(6.dp, Color(0xFF161D1D), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth(0.84f).background(Color(0xFF1C2222), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Allow USB debugging?", color = Color.White, style = MaterialTheme.typography.titleSmall)
                if (key != null) {
                    Text("The computer's RSA key fingerprint is:", color = Color(0xFFBEC8C8), fontSize = 9.sp, lineHeight = 12.sp)
                    Text(
                        key.split(':').chunked(8).joinToString(":\n") { it.joinToString(":") },
                        color = Color.White,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.background(accent.copy(alpha = 0.25f), RoundedCornerShape(3.dp)).padding(horizontal = 2.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(16.dp).background(accent, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color(0xFF003738))
                    }
                    Text("Always allow from this computer", color = Color(0xFFE3E9E9), fontSize = 11.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    Text("Cancel", color = accent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    // Focused, the way the remote shows it.
                    Text(
                        "Allow",
                        color = Color(0xFF003738),
                        fontSize = 12.sp,
                        modifier = Modifier.background(accent, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Box(Modifier.width(56.dp).height(6.dp).background(Color(0xFF161D1D), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)))
    }
}

private const val CODE_LENGTH = 6

/** One box per digit, on the number keypad. A single hidden field underneath, so paste and delete just work. */
@Composable
private fun CodeBoxes(code: String, onChange: (String) -> Unit, enabled: Boolean, isError: Boolean) {
    BasicTextField(
        value = code,
        onValueChange = onChange,
        modifier = Modifier.semantics { contentDescription = "Pairing code" },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = { _ ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(CODE_LENGTH) { i ->
                    val current = enabled && i == code.length
                    val color = when {
                        isError -> MaterialTheme.colorScheme.error
                        current -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Box(
                        Modifier.size(width = 44.dp, height = 56.dp)
                            .border(if (current) 2.dp else 1.dp, color, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(code.getOrNull(i)?.toString().orEmpty(), style = StatNumber)
                    }
                }
            }
        },
    )
}
