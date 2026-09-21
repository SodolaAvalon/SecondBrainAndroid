package com.lifeos.secondbrain.ui.archive

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.lifeos.secondbrain.domain.NoteType
import com.lifeos.secondbrain.domain.TimeParse
import com.lifeos.secondbrain.ui.AppViewModel
import com.lifeos.secondbrain.ui.GlassSurface
import com.lifeos.secondbrain.ui.sectionEnter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ArchiveTypeFilter { ALL, TASK, IDEA, RAW, JOURNAL, PROJECT, REFERENCE }
enum class ArchiveDateFilter { ALL, LAST_30_DAYS, THIS_YEAR }

@Composable
fun ArchiveScreen(vm: AppViewModel, onOpenNote: (LifeNote) -> Unit) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(ArchiveTypeFilter.ALL) }
    var dateFilter by remember { mutableStateOf(ArchiveDateFilter.ALL) }
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val allNotes by vm.notes.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val base = if (query.isBlank()) allNotes else results
    val shown = base.filter { note -> matchesType(note, typeFilter) && matchesDate(note, dateFilter) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("档案", style = MaterialTheme.typography.headlineLarge) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; vm.search(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索我的人生……") },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                singleLine = true
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArchiveTypeFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = typeFilter == filter,
                        onClick = { typeFilter = filter },
                        label = { Text(filter.label()) }
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArchiveDateFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = dateFilter == filter,
                        onClick = { dateFilter = filter },
                        label = { Text(filter.label()) }
                    )
                }
            }
        }
        if (query.isBlank() && shown.isEmpty()) {
            item {
                Text(
                    "这里暂时很安静。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (shown.isEmpty()) {
            item { Text("没有找到，但你的档案还在那里。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        itemsIndexed(shown, key = { _, n -> n.fileId }) { index, note ->
            GlassSurface(
                Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .sectionEnter(note.fileId, index, settings.reduceMotion),
                radius = 20.dp,
                contentPadding = PaddingValues(16.dp),
                reduceMotion = settings.reduceMotion,
                onClick = { onOpenNote(note) },
                onClickLabel = "打开 ${note.title}"
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium)
                    val meta = listOfNotNull(note.type.raw, note.created ?: note.updated).joinToString(" · ")
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    note.summary?.let { Text(it, maxLines = 2) }
                }
            }
        }
    }
}

private fun matchesType(note: LifeNote, filter: ArchiveTypeFilter): Boolean = when (filter) {
    ArchiveTypeFilter.ALL -> true
    ArchiveTypeFilter.TASK -> note.type == NoteType.TASK
    ArchiveTypeFilter.IDEA -> note.type in setOf(NoteType.IDEA, NoteType.PLAN, NoteType.WRITING, NoteType.LEARNING)
    ArchiveTypeFilter.RAW -> note.type == NoteType.RAW
    ArchiveTypeFilter.JOURNAL -> note.type == NoteType.JOURNAL
    ArchiveTypeFilter.PROJECT -> note.type == NoteType.PROJECT
    ArchiveTypeFilter.REFERENCE -> note.type in setOf(NoteType.REFERENCE, NoteType.DECISION)
}

private fun matchesDate(note: LifeNote, filter: ArchiveDateFilter): Boolean {
    if (filter == ArchiveDateFilter.ALL) return true
    val instant = parseTime(note.created ?: note.updated ?: note.modifiedTime) ?: return false
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (filter) {
        ArchiveDateFilter.ALL -> true
        ArchiveDateFilter.LAST_30_DAYS -> !date.isBefore(today.minusDays(30))
        ArchiveDateFilter.THIS_YEAR -> date.year == today.year
    }
}

private fun parseTime(raw: String?): Instant? = TimeParse.instant(raw)

private fun ArchiveTypeFilter.label(): String = when (this) {
    ArchiveTypeFilter.ALL -> "全部"
    ArchiveTypeFilter.TASK -> "任务"
    ArchiveTypeFilter.IDEA -> "想法"
    ArchiveTypeFilter.RAW -> "原始记录"
    ArchiveTypeFilter.JOURNAL -> "Journal"
    ArchiveTypeFilter.PROJECT -> "项目"
    ArchiveTypeFilter.REFERENCE -> "资料"
}

private fun ArchiveDateFilter.label(): String = when (this) {
    ArchiveDateFilter.ALL -> "不限时间"
    ArchiveDateFilter.LAST_30_DAYS -> "近 30 天"
    ArchiveDateFilter.THIS_YEAR -> "今年"
}
