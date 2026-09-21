package com.lifeos.secondbrain.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.secondbrain.ui.archive.ArchiveScreen
import com.lifeos.secondbrain.ui.capture.CaptureSheet
import com.lifeos.secondbrain.ui.home.HomeScreen
import com.lifeos.secondbrain.ui.inspiration.InspirationScreen
import com.lifeos.secondbrain.ui.settings.SettingsScreen
import com.lifeos.secondbrain.ui.tasks.TasksScreen

private enum class Tab { NOW, TASKS, INSPIRATION, ARCHIVE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel, onAuthorizeDrive: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.NOW) }
    var capture by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val captureInProgress by vm.captureInProgress.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.message.value = null }
    }

    if (settingsOpen) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                SettingsScreen(vm, onBack = { settingsOpen = false })
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(
                        onClick = { settingsOpen = true },
                        modifier = Modifier.padding(end = 10.dp).softGlass(radius = 18.dp, shadow = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .softGlass(radius = 24.dp, emphasized = true, shadow = 18.dp)
                    .springClickable(reduceMotion = settings.reduceMotion) { capture = true }
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "记录", tint = MaterialTheme.colorScheme.primary)
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .softGlass(radius = 30.dp, emphasized = true, shadow = 16.dp),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                GlassNavigationItem(tab == Tab.NOW, settings.reduceMotion, Icons.Rounded.Home, "现在") { tab = Tab.NOW }
                GlassNavigationItem(tab == Tab.TASKS, settings.reduceMotion, Icons.Rounded.CheckCircle, "任务") { tab = Tab.TASKS }
                GlassNavigationItem(tab == Tab.INSPIRATION, settings.reduceMotion, Icons.Rounded.AutoAwesome, "灵感") { tab = Tab.INSPIRATION }
                GlassNavigationItem(tab == Tab.ARCHIVE, settings.reduceMotion, Icons.Rounded.Search, "档案") { tab = Tab.ARCHIVE }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = sync.isSyncing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                when (tab) {
                    Tab.NOW -> HomeScreen(vm, onAuthorizeDrive)
                    Tab.TASKS -> TasksScreen(vm)
                    Tab.INSPIRATION -> InspirationScreen(vm)
                    Tab.ARCHIVE -> ArchiveScreen(vm)
                }
            }
            if (captureInProgress) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp)
                        .softGlass(radius = 22.dp, emphasized = true, shadow = 10.dp)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("正在保存……", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (capture) CaptureSheet(vm, onDismiss = { capture = false })
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.GlassNavigationItem(
    selected: Boolean,
    reduceMotion: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "nav-$label"
    )
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) },
        label = { Text(label) },
        alwaysShowLabel = true
    )
}
