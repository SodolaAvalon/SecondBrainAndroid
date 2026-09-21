package com.lifeos.secondbrain.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.secondbrain.domain.LifeNote
import com.lifeos.secondbrain.domain.TaskVisibility
import com.lifeos.secondbrain.ui.AppViewModel
import com.lifeos.secondbrain.ui.GlassSurface
import com.lifeos.secondbrain.ui.sectionEnter
import java.time.LocalDate

enum class TaskSegment { TODAY, SOON, ALL, DONE }

/**
 * Today's date for every visibility decision on this screen.
 *
 * Deliberately a single value per composition instead of a `LocalDate.now()` call per item: two
 * calls could straddle midnight and disagree, leaving one row in the wrong segment.
 */
@Composable
private fun todayKey(): LocalDate = remember { LocalDate.now() }

@Composable
fun TasksScreen(vm: AppViewModel, onOpenNote: (LifeNote) -> Unit) {
    val active by vm.tasks.collectAsStateWithLifecycle()
    val all by vm.allTasks.collectAsStateWithLifecycle()
    var segment by remember { mutableStateOf(TaskSegment.TODAY) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today = todayKey()

    // Every segment routes through TaskVisibility so Home and Tasks can never drift apart again.
    val shown = when (segment) {
        TaskSegment.TODAY -> active.filter { TaskVisibility.isTodayTask(it, today) }
        TaskSegment.SOON -> active.filter { TaskVisibility.isUpcoming(it, today) }
        TaskSegment.ALL -> active.filter { TaskVisibility.isOpenTask(it, today) }
        TaskSegment.DONE -> all.filter { TaskVisibility.isDoneTask(it) }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("任务", style = MaterialTheme.typography.headlineLarge) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskSegment.entries.forEach { item ->
                    FilterChip(
                        selected = segment == item,
                        onClick = { segment = item },
                        label = { Text(when (item) { TaskSegment.TODAY -> "今天"; TaskSegment.SOON -> "近期"; TaskSegment.ALL -> "全部"; TaskSegment.DONE -> "完成" }) }
                    )
                }
            }
        }
        if (shown.isEmpty()) item { Text(if (segment == TaskSegment.TODAY) "今天没有非做不可的事。" else "这里暂时很安静。") }
        itemsIndexed(shown, key = { _, n -> n.fileId }) { index, note ->
            TaskRow(
                note = note,
                // Recurring tasks are never "done" as a state — they are done *for today*, which is
                // what the checkbox shows and what makes it clear again tomorrow.
                done = segment == TaskSegment.DONE || TaskVisibility.isCompletedOn(note, today),
                vm = vm,
                today = today,
                reduceMotion = settings.reduceMotion,
                modifier = Modifier
                    .animateItem()
                    .sectionEnter(note.fileId, index, settings.reduceMotion),
                onOpen = { onOpenNote(note) }
            )
        }
    }
}

@Composable
private fun TaskRow(
    note: LifeNote,
    done: Boolean,
    vm: AppViewModel,
    today: LocalDate,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    val recurring = TaskVisibility.isRecurring(note)
    GlassSurface(
        modifier.fillMaxWidth(),
        radius = 20.dp,
        contentPadding = PaddingValues(14.dp),
        reduceMotion = reduceMotion,
        onClick = onOpen,
        onClickLabel = "打开 ${note.title}"
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(done, onCheckedChange = { checked -> if (checked && !done) vm.complete(note) })
            Column(Modifier.padding(start = 8.dp)) {
                Text(note.title, style = MaterialTheme.typography.titleMedium)
                val meta = buildList {
                    note.due?.let { add(it) }
                    note.project?.let { add(it) }
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (recurring) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Repeat,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            recurringHint(note, today, done),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * The habit stays visible after completion instead of vanishing, so "did I do this today?" is
 * answerable from the task itself rather than from a separate history list.
 */
private fun recurringHint(note: LifeNote, today: LocalDate, doneToday: Boolean): String {
    if (doneToday) return "今天已完成"
    val since = TaskVisibility.daysSinceCompletion(note, today) ?: return "每天"
    return when (since) {
        1L -> "每天 · 昨天完成过"
        else -> "每天 · $since 天前完成过"
    }
}
