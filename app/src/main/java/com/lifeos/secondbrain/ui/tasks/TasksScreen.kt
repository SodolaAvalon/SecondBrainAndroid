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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.secondbrain.domain.LifeNote
import com.lifeos.secondbrain.ui.AppViewModel
import com.lifeos.secondbrain.ui.GlassSurface
import com.lifeos.secondbrain.ui.sectionEnter
import java.time.LocalDate

enum class TaskSegment { TODAY, SOON, ALL, DONE }

@Composable
fun TasksScreen(vm: AppViewModel, onOpenNote: (LifeNote) -> Unit) {
    val active by vm.tasks.collectAsStateWithLifecycle()
    val all by vm.allTasks.collectAsStateWithLifecycle()
    var segment by remember { mutableStateOf(TaskSegment.TODAY) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val shown = when (segment) {
        TaskSegment.TODAY -> active.filter { note -> note.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { !it.isAfter(today) } ?: false }
        TaskSegment.SOON -> active.filter { note -> note.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it.isAfter(today) && !it.isAfter(today.plusDays(14)) } ?: false }
        TaskSegment.ALL -> active
        TaskSegment.DONE -> all.filter { it.status.equals("done", ignoreCase = true) }
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
                done = segment == TaskSegment.DONE,
                vm = vm,
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
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    GlassSurface(
        modifier.fillMaxWidth(),
        radius = 20.dp,
        contentPadding = PaddingValues(14.dp),
        reduceMotion = reduceMotion,
        onClick = onOpen,
        onClickLabel = "打开 ${note.title}"
    ) {
        Row(Modifier.fillMaxWidth()) {
            Checkbox(done, onCheckedChange = { checked -> if (checked && !done) vm.complete(note) })
            Column(Modifier.padding(start = 8.dp)) {
                Text(note.title, style = MaterialTheme.typography.titleMedium)
                val meta = listOfNotNull(note.due, note.project).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
