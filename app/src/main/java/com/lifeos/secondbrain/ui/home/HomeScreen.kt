package com.lifeos.secondbrain.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.secondbrain.domain.LifeNote
import com.lifeos.secondbrain.ui.AppViewModel
import com.lifeos.secondbrain.ui.GlassSurface
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

@Composable
fun HomeScreen(vm: AppViewModel, onAuthorizeDrive: () -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val rawNotes by vm.rawNotes.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val forgotten = (tasks + projects)
        .filter { !it.status.equals("done", true) }
        .filter(::isForgotten)
        .distinctBy { it.fileId }
        .take(3)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(greeting(), style = MaterialTheme.typography.headlineLarge)
            when {
                sync.isSyncing -> Text(
                    sync.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                sync.error != null -> Text(
                    sync.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (settings.vaultFolderId == null) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    radius = 28.dp,
                    emphasized = true
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("连接你的人生档案", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Google Drive 继续作为唯一真实数据源，这里只保留可随时重建的本地缓存。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onAuthorizeDrive) { Text("连接 Google Drive") }
                    }
                }
            }
        } else {
            item {
                val focus = tasks.firstOrNull()
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    radius = 30.dp,
                    emphasized = true,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("现在先做这个", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (focus == null) {
                            Text("今天没有非做不可的事。", style = MaterialTheme.typography.headlineSmall)
                            Text("可以放心做点别的。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(focus.title, style = MaterialTheme.typography.headlineSmall)
                            val meta = listOfNotNull(
                                focus.due?.let { "截止 $it" },
                                focus.project?.let { "来自 $it" }
                            ).joinToString(" · ")
                            if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { vm.complete(focus) }) { Text("完成") }
                            }
                        }
                    }
                }
            }

            item { SectionHeading("今天") }
            if (tasks.isEmpty()) {
                item { QuietEmpty("这里暂时很安静。") }
            }
            items(tasks.take(5), key = { "today-${it.fileId}" }) { note ->
                NoteCard(note)
            }

            if (projects.isNotEmpty()) {
                item { SectionHeading("正在进行") }
                items(projects.filter { !it.status.equals("done", true) }.take(4), key = { "project-${it.fileId}" }) { project ->
                    GlassSurface(Modifier.fillMaxWidth(), radius = 22.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(project.title, style = MaterialTheme.typography.titleMedium)
                            project.updated?.let {
                                Text("最近更新 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            project.summary?.let {
                                Text(it, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(2.dp)); SectionHeading("最近想法") }
            if (ideas.isEmpty()) {
                item { QuietEmpty("下一个奇怪想法出现时，把它扔进来。") }
            }
            items(ideas.take(5), key = { "idea-${it.fileId}" }) { note ->
                GlassSurface(Modifier.fillMaxWidth(), radius = 22.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(note.title, style = MaterialTheme.typography.titleMedium)
                        note.summary?.let { Text(it, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            if (rawNotes.isNotEmpty()) {
                item { SectionHeading("最近的我") }
                items(rawNotes.take(4), key = { "raw-${it.fileId}" }) { note ->
                    GlassSurface(Modifier.fillMaxWidth(), radius = 22.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            note.created?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(note.summary ?: note.title, maxLines = 3)
                        }
                    }
                }
            }

            if (forgotten.isNotEmpty()) {
                item { SectionHeading("被我遗忘的事情") }
                item {
                    Text(
                        "这些好像有一阵子没碰了。没有关系，只是把它们轻轻放回来。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                items(forgotten, key = { "forgotten-${it.fileId}" }) { note ->
                    GlassSurface(Modifier.fillMaxWidth(), radius = 22.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(note.title, style = MaterialTheme.typography.titleMedium)
                            note.project?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = { vm.refresh() }) { Text("看看最近有什么变化") }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun QuietEmpty(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NoteCard(note: LifeNote) {
    GlassSurface(Modifier.fillMaxWidth(), radius = 22.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(note.title, style = MaterialTheme.typography.titleMedium)
            val meta = listOfNotNull(note.due, note.project).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..10 -> "早上好。"
    in 11..13 -> "中午好。"
    in 14..17 -> "下午好。"
    else -> "晚上好。"
}

private fun isForgotten(note: LifeNote): Boolean {
    val instant = parseTime(note.updated ?: note.modifiedTime ?: note.created) ?: return false
    return Duration.between(instant, Instant.now()).toDays() >= 30
}

private fun parseTime(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()
}
