package com.lifeos.secondbrain.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Best-effort Markdown → Compose rendering for the detail screen.
 *
 * The note body is shown as-is rather than through a full parser, because Drive holds the source of
 * truth and this screen must never look like it is rewriting the file. So only structure that carries
 * no meaning when displayed literally is interpreted: heading markers become real headings, task
 * markers become checkboxes, list indentation is preserved. Inline markers such as `**` and `` ` ``
 * are stripped because their meaning survives as styling, while links are left verbatim since there
 * is no link UI here and hiding a URL would lose information the user may need.
 */
internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String, val indent: Int, val checked: Boolean?) : MdBlock
    data class Numbered(val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lines: List<String>) : MdBlock
    data class Rule(val index: Int) : MdBlock
}

private val HEADING = Regex("""^(#{1,6})\s*(.+?)\s*$""")
private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val NUMBERED = Regex("""^(\s*)\d+[.)]\s+(.*)$""")
private val QUOTE = Regex("""^>\s?(.*)$""")
private val TASK = Regex("""^\[([ xX])]\s*(.*)$""")
private val RULE = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")

internal fun parseMarkdownBlocks(body: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    var ruleIndex = 0
    var inCode = false
    val codeLines = mutableListOf<String>()

    for (raw in body.lines()) {
        val line = raw.trimEnd()
        val fence = line.trimStart().startsWith("```")

        if (inCode) {
            if (fence) {
                blocks += MdBlock.Code(codeLines.toList())
                codeLines.clear()
                inCode = false
            } else {
                codeLines += line
            }
            continue
        }
        if (fence) {
            inCode = true
            continue
        }

        if (line.isBlank()) continue

        val heading = HEADING.matchEntire(line)
        val bullet = if (heading == null) BULLET.matchEntire(line) else null
        val numbered = if (heading == null && bullet == null) NUMBERED.matchEntire(line) else null
        val quote = if (heading == null && bullet == null && numbered == null) QUOTE.matchEntire(line) else null

        when {
            heading != null ->
                blocks += MdBlock.Heading(heading.groupValues[1].length, stripInline(heading.groupValues[2]))

            RULE.matches(line) -> blocks += MdBlock.Rule(ruleIndex++)

            bullet != null -> {
                val indent = indentLevel(bullet.groupValues[1])
                val task = TASK.matchEntire(bullet.groupValues[2])
                blocks += if (task != null) {
                    MdBlock.Bullet(stripInline(task.groupValues[2]), indent, task.groupValues[1] != " ")
                } else {
                    MdBlock.Bullet(stripInline(bullet.groupValues[2]), indent, null)
                }
            }

            numbered != null ->
                blocks += MdBlock.Numbered(stripInline(numbered.groupValues[2]), indentLevel(numbered.groupValues[1]))

            quote != null ->
                blocks += MdBlock.Quote(stripInline(quote.groupValues[1]))

            else -> blocks += MdBlock.Paragraph(stripInline(line))
        }
    }

    if (codeLines.isNotEmpty()) blocks += MdBlock.Code(codeLines.toList())
    return blocks
}

/** Two spaces or one tab per nesting level, matching how Obsidian writes nested lists. */
private fun indentLevel(leading: String): Int {
    val tabs = leading.count { it == '\t' }
    val spaces = leading.count { it == ' ' }
    return tabs + spaces / 2
}

/**
 * Removes markers whose meaning is preserved by styling. Bold/italic/code markers and hard line
 * breaks carry nothing once styled; link syntax is intentionally left alone.
 */
private fun stripInline(text: String): String = text
    .replace("**", "")
    .replace("__", "")
    .replace(Regex("""(?<!\w)[*_](?=\S)(.+?)(?<=\S)[*_](?!\w)"""), "$1")
    .replace(Regex("""`([^`]+)`"""), "$1")
    .replace(Regex("""\s{2,}$"""), "")
    .trim()

/**
 * Renders blocks inside an existing Column scope rather than a LazyColumn: notes are short, and
 * nesting lazy lists inside the scrolling detail list is a known source of measurement trouble.
 */
@Composable
internal fun MarkdownBlocks(blocks: List<MdBlock>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    block.text,
                    style = headingStyle(block.level),
                    color = MaterialTheme.colorScheme.onSurface
                )

                is MdBlock.Paragraph -> Text(
                    block.text,
                    style = MaterialTheme.typography.bodyLarge
                )

                is MdBlock.Bullet -> BulletRow(block.indent, block.checked, block.text)

                is MdBlock.Numbered -> Row(
                    Modifier.padding(start = (block.indent * 20).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("•", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(block.text, style = MaterialTheme.typography.bodyLarge)
                }

                is MdBlock.Quote -> Box(
                    Modifier
                        .fillMaxWidth()
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(2.dp)
                        )
                        .padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Text(
                        block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is MdBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        block.lines.joinToString("\n"),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is MdBlock.Rule -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                )
            }
        }
    }
}

@Composable
private fun BulletRow(indent: Int, checked: Boolean?, text: String) {
    Row(
        Modifier.padding(start = (indent * 20).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (checked == null) {
            // A real bullet rather than a checkbox keeps plain lists visually distinct from tasks.
            Text(
                "•",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .background(
                        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (checked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(6.dp)
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked == true) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.headlineSmall
    2 -> MaterialTheme.typography.titleLarge
    3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    4 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.labelLarge
}
