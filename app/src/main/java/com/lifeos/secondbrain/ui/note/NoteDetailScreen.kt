package com.lifeos.secondbrain.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.lifeos.secondbrain.domain.LifeNote
import com.lifeos.secondbrain.domain.NoteType
import com.lifeos.secondbrain.domain.TaskVisibility
import com.lifeos.secondbrain.ui.AppViewModel
import com.lifeos.secondbrain.ui.GlassSurface
import com.lifeos.secondbrain.ui.formatWhen
import java.time.LocalDate

/**
 * Read-only expanded view of a single note. It deliberately shows the raw markdown body verbatim:
 * Drive holds the source of truth, and this screen exists so the user can read it without leaving
 * the app, not so the app can rewrite it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(vm: AppViewModel, note: LifeNote, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val recurring = TaskVisibility.isRecurring(note)
    // "Done" differs by kind: a one-off task carries status: done, a recurring one is done for today
    // and becomes available again tomorrow. Showing one state for both would misreport one of them.
    val doneToday = TaskVisibility.isCompletedOn(note, today)
    val permanentlyDone = !recurring && note.status.equals("done", ignoreCase = true)
    val body = remember(note.fileId) { note.body.trim() }
    val blocks = remember(note.fileId) { parseMarkdownBlocks(body) }
    val properties = remember(note.fileId) { buildProperties(note) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("详情", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(note.title, style = MaterialTheme.typography.headlineMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, enabled = false, label = { Text(note.type.label()) })
                        when {
                            recurring && doneToday ->
                                AssistChip(onClick = {}, enabled = false, label = { Text("今天已完成") })
                            recurring ->
                                AssistChip(onClick = {}, enabled = false, label = { Text("每天") })
                            permanentlyDone ->
                                AssistChip(onClick = {}, enabled = false, label = { Text("已完成") })
                            else -> note.status?.takeIf { it.isNotBlank() }?.let { status ->
                                AssistChip(onClick = {}, enabled = false, label = { Text(status) })
                            }
                        }
                        note.priority?.takeIf { it.isNotBlank() }?.let { priority ->
                            AssistChip(onClick = {}, enabled = false, label = { Text("优先级 $priority") })
                        }
                    }
                }
            }

            if (properties.isNotEmpty()) {
                item {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        radius = 22.dp,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            properties.forEach { (label, value) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Text(value, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            if (note.tags.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("标签", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            note.tags.forEach { tag ->
                                AssistChip(onClick = {}, enabled = false, label = { Text(tag) })
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("正文", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (blocks.isEmpty()) {
                        Text("这条记录还没有正文。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (blocks.isNotEmpty()) {
                item {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        radius = 22.dp,
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            MarkdownBlocks(blocks)
                        }
                    }
                }
            }

            if (note.type == NoteType.TASK && !permanentlyDone && !(recurring && doneToday)) {
                item {
                    OutlinedButton(onClick = { vm.complete(note) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Text(if (recurring) "  标记今天完成" else "  标记完成")
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        val url = "https://drive.google.com/open?id=${note.fileId}"
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            )
                        }.onFailure { vm.message.value = "没有找到可以打开 Drive 链接的应用。" }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    Text("  在 Drive 中打开原文")
                }
            }

            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                Text(
                    "这个页面只做展示，不会修改 Vault。真正的原文始终以 Google Drive 中的 Markdown 为准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

/** Order matters: the fields a person scans first (place, deadline, project) come before audit-ish ones. */
private fun buildProperties(note: LifeNote): List<Pair<String, String>> = buildList {
    note.project?.takeIf { it.isNotBlank() }?.let { add("项目" to it) }
    note.due?.takeIf { it.isNotBlank() }?.let { add("截止" to it) }
    if (TaskVisibility.isRecurring(note)) {
        add("重复" to "每天")
        note.start?.takeIf { it.isNotBlank() }?.let { add("开始" to it) }
        add("上次完成" to (formatWhen(note.lastCompleted) ?: "还没有"))
    }
    formatWhen(note.created)?.let { add("创建" to it) }
    formatWhen(note.updated)?.let { add("更新" to it) }
    note.source?.takeIf { it.isNotBlank() }?.let { add("来源" to if (it == "voice") "语音" else it) }
    note.processed?.let { add("AI 已整理" to if (it) "是" else "否") }
    note.path?.takeIf { it.isNotBlank() }?.let { add("位置" to it) }
    add("文件" to note.name)
    note.md5Checksum?.takeIf { it.isNotBlank() }?.let { add("校验" to it.take(12)) }
}

private fun NoteType.label(): String = when (this) {
    NoteType.TASK -> "任务"
    NoteType.IDEA -> "想法"
    NoteType.PLAN -> "计划"
    NoteType.WRITING -> "创作"
    NoteType.LEARNING -> "学习"
    NoteType.RAW -> "原始记录"
    NoteType.JOURNAL -> "Journal"
    NoteType.DECISION -> "决策"
    NoteType.REFERENCE -> "资料"
    NoteType.PROJECT -> "项目"
    NoteType.UNKNOWN -> "未分类"
}
