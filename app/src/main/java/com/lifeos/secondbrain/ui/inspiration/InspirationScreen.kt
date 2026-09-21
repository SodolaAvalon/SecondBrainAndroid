package com.lifeos.secondbrain.ui.inspiration

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

private enum class InspirationSegment { IDEA, PLAN, WRITING, LEARNING }

@Composable
fun InspirationScreen(vm: AppViewModel, onOpenNote: (LifeNote) -> Unit) {
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val writings by vm.writings.collectAsStateWithLifecycle()
    val learnings by vm.learnings.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var segment by remember { mutableStateOf(InspirationSegment.IDEA) }
    val shown: List<LifeNote> = when (segment) {
        InspirationSegment.IDEA -> ideas
        InspirationSegment.PLAN -> plans
        InspirationSegment.WRITING -> writings
        InspirationSegment.LEARNING -> learnings
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("灵感", style = MaterialTheme.typography.headlineLarge) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InspirationSegment.entries.forEach { item ->
                    FilterChip(
                        selected = segment == item,
                        onClick = { segment = item },
                        label = { Text(when (item) { InspirationSegment.IDEA -> "想法"; InspirationSegment.PLAN -> "计划"; InspirationSegment.WRITING -> "创作"; InspirationSegment.LEARNING -> "学习" }) }
                    )
                }
            }
        }
        if (shown.isEmpty()) item { Text("下一个奇怪想法出现时，把它扔进来。") }
        itemsIndexed(shown, key = { _, n -> n.fileId }) { index, note ->
            GlassSurface(
                Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .sectionEnter(note.fileId, index, settings.reduceMotion),
                radius = 22.dp,
                contentPadding = PaddingValues(18.dp),
                reduceMotion = settings.reduceMotion,
                onClick = { onOpenNote(note) },
                onClickLabel = "打开 ${note.title}"
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium)
                    note.summary?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    note.created?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
