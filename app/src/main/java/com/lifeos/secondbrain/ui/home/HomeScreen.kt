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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.secondbrain.domain.LifeNote
import com.lifeos.secondbrain.domain.TaskVisibility
import com.lifeos.secondbrain.domain.TimeParse
import com.lifeos.secondbrain.ui.AppViewModel
import com.lifeos.secondbrain.ui.GlassSurface
import com.lifeos.secondbrain.ui.sectionEnter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(vm: AppViewModel, onAuthorizeDrive: () -> Unit, onOpenNote: (LifeNote) -> Unit) {
    val activeTasks by vm.tasks.collectAsStateWithLifecycle()
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val rawNotes by vm.rawNotes.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    // Shared visibility rule — the same one TasksScreen uses, so the two screens cannot disagree
    // about what "today" means.
    val todayTasks = activeTasks.filter { TaskVisibility.isTodayTask(it, today) }
    val doneToday = activeTasks.count { TaskVisibility.isRecurring(it) && TaskVisibility.isCompletedOn(it, today) }
    val forgotten = (activeTasks + projects)
        .filter { !it.status.equals("done", true) }
        .filterNot { TaskVisibility.isRecurring(it) }
        .filter(::isForgotten)
        .distinctBy { it.fileId }
        .take(3)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(greeting(), style = MaterialTheme.typography.headlineLarge)
            // Progress text now lives in the pinned corner chip; only failures stay inline, where
            // they cannot be mistaken for routine activity that will pass on its own.
            sync.error?.takeIf { !sync.isSyncing }?.let {
                Text(
                    it,
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
                val focus = todayTasks.firstOrNull()
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    radius = 30.dp,
                    emphasized = true,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("现在先做这个", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (focus == null) {
                            Text(
                                if (doneToday > 0) "今天的都做完了。" else "今天没有非做不可的事。",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                if (doneToday > 0) "今天完成了 $doneToday 项周期任务，明天它们会再回来。" else "可以放心做点别的。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(focus.title, style = MaterialTheme.typography.headlineSmall)
                            val meta = listOfNotNull(
                                focus.due?.let { "截止 $it" },
                                focus.project?.let { "来自 $it" },
                                if (TaskVisibility.isRecurring(focus)) "每天" else null
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
            if (todayTasks.isEmpty()) {
                item {
                    QuietEmpty(
                        if (doneToday > 0) "今天的任务已经完成，明天它们会再出现。"
                        else "这里暂时很安静。"
                    )
                }
            }
            itemsIndexed(todayTasks.take(5), key = { _, n -> "today-${n.fileId}" }) { index, note ->
                NoteCard(
                    note = note,
                    reduceMotion = settings.reduceMotion,
                    modifier = Modifier.sectionEnter(note.fileId, index, settings.reduceMotion)
                ) {
                    onOpenNote(note)
                }
            }

            if (projects.isNotEmpty()) {
                item { SectionHeading("正在进行") }
                itemsIndexed(
                    projects.filter { !it.status.equals("done", true) }.take(4),
                    key = { _, p -> "project-${p.fileId}" }
                ) { index, project ->
                    GlassSurface(
                        Modifier
                            .fillMaxWidth()
                            .sectionEnter(project.fileId, index, settings.reduceMotion),
                        radius = 22.dp,
                        reduceMotion = settings.reduceMotion,
                        onClick = { onOpenNote(project) },
                        onClickLabel = "打开 ${project.title}"
                    ) {
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
            itemsIndexed(ideas.take(5), key = { _, n -> "idea-${n.fileId}" }) { index, note ->
                GlassSurface(
                    Modifier
                        .fillMaxWidth()
                        .sectionEnter(note.fileId, index, settings.reduceMotion),
                    radius = 22.dp,
                    reduceMotion = settings.reduceMotion,
                    onClick = { onOpenNote(note) },
                    onClickLabel = "打开 ${note.title}"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(note.title, style = MaterialTheme.typography.titleMedium)
                        note.summary?.let { Text(it, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            if (rawNotes.isNotEmpty()) {
                item { SectionHeading("最近的我") }
                itemsIndexed(rawNotes.take(4), key = { _, n -> "raw-${n.fileId}" }) { index, note ->
                    GlassSurface(
                        Modifier
                            .fillMaxWidth()
                            .sectionEnter(note.fileId, index, settings.reduceMotion),
                        radius = 22.dp,
                        reduceMotion = settings.reduceMotion,
                        onClick = { onOpenNote(note) },
                        onClickLabel = "打开 ${note.title}"
                    ) {
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
                itemsIndexed(forgotten, key = { _, n -> "forgotten-${n.fileId}" }) { index, note ->
                    GlassSurface(
                        Modifier
                            .fillMaxWidth()
                            .sectionEnter(note.fileId, index, settings.reduceMotion),
                        radius = 22.dp,
                        reduceMotion = settings.reduceMotion,
                        onClick = { onOpenNote(note) },
                        onClickLabel = "打开 ${note.title}"
                    ) {
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
private fun NoteCard(note: LifeNote, reduceMotion: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassSurface(
        modifier.fillMaxWidth(),
        radius = 22.dp,
        reduceMotion = reduceMotion,
        onClick = onClick,
        onClickLabel = "打开 ${note.title}"
    ) {
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
    val instant = TimeParse.instant(note.updated ?: note.modifiedTime ?: note.created) ?: return false
    return Duration.between(instant, Instant.now()).toDays() >= 30
}
