package com.lifeos.secondbrain.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.lifeos.secondbrain.domain.LifeNote
import com.lifeos.secondbrain.ui.archive.ArchiveScreen
import com.lifeos.secondbrain.ui.capture.CaptureSheet
import com.lifeos.secondbrain.ui.home.HomeScreen
import com.lifeos.secondbrain.ui.inspiration.InspirationScreen
import com.lifeos.secondbrain.ui.note.NoteDetailScreen
import com.lifeos.secondbrain.ui.settings.SettingsScreen
import com.lifeos.secondbrain.ui.tasks.TasksScreen

private enum class Tab { NOW, TASKS, INSPIRATION, ARCHIVE }

/** Which surface is on top. Detail and settings are peers: both are entered from, and return to, the shell. */
private enum class Surface { SHELL, SETTINGS, DETAIL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel, onAuthorizeDrive: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.NOW) }
    var capture by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var openNote by remember { mutableStateOf<LifeNote?>(null) }
    val snack = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val captureInProgress by vm.captureInProgress.collectAsStateWithLifecycle()
    val reduceMotion = settings.reduceMotion

    val surface = when {
        openNote != null -> Surface.DETAIL
        settingsOpen -> Surface.SETTINGS
        else -> Surface.SHELL
    }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.message.value = null }
    }

    // Without this, system back inside 设置 leaves the app entirely instead of returning to the tabs.
    // The capture sheet installs its own handler as a ModalBottomSheet, so it is not handled here.
    // Enabled only for overlay surfaces, so back on the shell still exits the app as users expect.
    BackHandler(enabled = surface != Surface.SHELL) {
        when (surface) {
            Surface.DETAIL -> openNote = null
            Surface.SETTINGS -> settingsOpen = false
            Surface.SHELL -> Unit
        }
    }

    AnimatedContent(
        targetState = surface,
        transitionSpec = { overlayTransition(reduceMotion) },
        label = "surface"
    ) { current ->
        when (current) {
            Surface.DETAIL -> {
                val note = openNote
                if (note == null) {
                    Box(Modifier.fillMaxSize())
                } else {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        snackbarHost = { SnackbarHost(snack) }
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            NoteDetailScreen(vm, note, onBack = { openNote = null })
                        }
                    }
                }
            }

            Surface.SETTINGS -> Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snack) }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    SettingsScreen(vm, onBack = { settingsOpen = false })
                }
            }

            Surface.SHELL -> Shell(
                vm = vm,
                tab = tab,
                onTabChange = { tab = it },
                reduceMotion = reduceMotion,
                snack = snack,
                sync = sync,
                captureInProgress = captureInProgress,
                onAuthorizeDrive = onAuthorizeDrive,
                onOpenSettings = { settingsOpen = true },
                onOpenNote = { openNote = it },
                onCapture = { capture = true }
            )
        }
    }

    if (capture) CaptureSheet(vm, onDismiss = { capture = false })
}

/**
 * Overlay surfaces slide in from the trailing edge and settle; the shell eases back slightly and
 * fades. The shell deliberately does not slide fully off — a short parallax reads as depth instead
 * of as two unrelated screens swapping places.
 */
private fun overlayTransition(reduceMotion: Boolean): androidx.compose.animation.ContentTransform {
    if (reduceMotion) return EnterTransition.None togetherWith ExitTransition.None
    val enter = slideInHorizontally(
        animationSpec = Motion.page(reduceMotion),
        initialOffsetX = { width -> width }
    ) + fadeIn(tween(Motion.PAGE_MS, easing = Motion.Settle))
    val exit = slideOutHorizontally(
        animationSpec = Motion.page(reduceMotion),
        targetOffsetX = { width -> width }
    ) + fadeOut(tween(Motion.PAGE_MS, easing = Motion.Settle))
    val shellOut = slideOutHorizontally(
        animationSpec = Motion.page(reduceMotion),
        targetOffsetX = { width -> -width / 5 }
    ) + fadeOut(tween(Motion.PAGE_MS / 2, easing = Motion.Settle))
    return enter togetherWith shellOut
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Shell(
    vm: AppViewModel,
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    reduceMotion: Boolean,
    snack: SnackbarHostState,
    sync: com.lifeos.secondbrain.domain.SyncState,
    captureInProgress: Boolean,
    onAuthorizeDrive: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNote: (LifeNote) -> Unit,
    onCapture: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
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
                    .springClickable(reduceMotion = reduceMotion, onClick = onCapture)
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
                GlassNavigationItem(tab == Tab.NOW, reduceMotion, Icons.Rounded.Home, "现在") { onTabChange(Tab.NOW) }
                GlassNavigationItem(tab == Tab.TASKS, reduceMotion, Icons.Rounded.CheckCircle, "任务") { onTabChange(Tab.TASKS) }
                GlassNavigationItem(tab == Tab.INSPIRATION, reduceMotion, Icons.Rounded.AutoAwesome, "灵感") { onTabChange(Tab.INSPIRATION) }
                GlassNavigationItem(tab == Tab.ARCHIVE, reduceMotion, Icons.Rounded.Search, "档案") { onTabChange(Tab.ARCHIVE) }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = sync.isSyncing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                // Tab switches slide a short distance in the direction of travel, so moving between
                // 现在/任务/灵感/档案 reads as lateral movement rather than as a hard cut.
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        if (reduceMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val forward = targetState.ordinal > initialState.ordinal
                            val distance = 60
                            (
                                slideInHorizontally(tween(Motion.ENTER_MS, easing = Motion.Settle)) {
                                    if (forward) it / distance else -it / distance
                                } + fadeIn(tween(Motion.ENTER_MS, easing = Motion.Settle))
                                ) togetherWith (
                                slideOutHorizontally(tween(Motion.ENTER_MS, easing = Motion.Settle)) {
                                    if (forward) -it / distance else it / distance
                                } + fadeOut(tween(Motion.ENTER_MS / 2))
                                )
                        }
                    },
                    label = "tab"
                ) { current ->
                    when (current) {
                        Tab.NOW -> HomeScreen(vm, onAuthorizeDrive, onOpenNote = onOpenNote)
                        Tab.TASKS -> TasksScreen(vm, onOpenNote = onOpenNote)
                        Tab.INSPIRATION -> InspirationScreen(vm, onOpenNote = onOpenNote)
                        Tab.ARCHIVE -> ArchiveScreen(vm, onOpenNote = onOpenNote)
                    }
                }
            }

            AnimatedVisibility(
                visible = captureInProgress,
                enter = if (reduceMotion) EnterTransition.None
                else slideInVertically(tween(Motion.ENTER_MS, easing = Motion.Settle)) { it / 2 } +
                    fadeIn(tween(Motion.ENTER_MS, easing = Motion.Settle)) +
                    scaleIn(initialScale = 0.94f, animationSpec = Motion.arrive(reduceMotion)),
                exit = if (reduceMotion) ExitTransition.None
                else fadeOut(tween(140)) + scaleOut(targetScale = 0.96f, animationSpec = tween(140)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
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
