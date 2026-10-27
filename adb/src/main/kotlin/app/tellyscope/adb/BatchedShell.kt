// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

/**
 * Runs a set of commands as one shell line, then answers them from that read; any other command
 * goes to the TV. Each command keeps its own output and exit code. A line cut short leaves the
 * commands after the cut unanswered, so those run on their own when asked.
 */
class BatchedShell private constructor(
    private val shell: AdbShell,
    private val answers: Map<String, ShellResult>,
) : AdbShell {
    override suspend fun shell(command: String): ShellResult = answers[command] ?: shell.shell(command)

    companion object {
        /** Everything an app list reads through [Actions] and `pm`, in one round trip instead of up to ten. */
        suspend fun appList(shell: AdbShell) = of(
            shell,
            listOf(
                Commands.HOME_ACTIVITY,
                Commands.HOME_ACTIVITIES,
                Commands.SYSTEM_PACKAGES,
                "pm list packages -d",
                Commands.USER_PACKAGES,
                Commands.LAUNCHABLE,
                "${Commands.TV_LAUNCHABLE} 2>/dev/null",
                "pm list packages --show-versioncode",
                Commands.A11Y_SERVICES,
                Commands.A11Y_ON,
            ),
        )

        suspend fun of(shell: AdbShell, commands: List<String>): BatchedShell {
            val distinct = commands.distinct()
            // `{ }` keeps a command's own `;` and redirects inside its section.
            val line = distinct.joinToString("; ") { "{ $it; }; echo \"${Commands.SEP}$?\"" }
            return BatchedShell(shell, split(shell.shell(line).output, distinct))
        }

        internal fun split(output: String, commands: List<String>): Map<String, ShellResult> {
            val answers = mutableMapOf<String, ShellResult>()
            var from = 0
            for (command in commands) {
                val end = END.find(output, from) ?: break
                answers[command] = ShellResult(output.substring(from, end.range.first), end.groupValues[1].toInt())
                from = end.range.last + 1
            }
            return answers
        }

        private val END = Regex("${Regex.escape(Commands.SEP)}(\\d+)\\r?\\n?")
    }
}
