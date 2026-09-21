package com.lifeos.secondbrain.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.unit.IntOffset
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
    // Whether the current surface change moves deeper (shell -> secondary) or back out of it.
    // The transition reads this so going back reverses the motion instead of replaying the forward one.
    var navForward by remember { mutableStateOf(true) }
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
        navForward = false
        when (surface) {
            Surface.DETAIL -> openNote = null
            Surface.SETTINGS -> settingsOpen = false
            Surface.SHELL -> Unit
        }
    }

    AnimatedContent(
        targetState = surface,
        transitionSpec = { overlayTransition(reduceMotion, forward = navForward) },
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
                            NoteDetailScreen(vm, note, onBack = {
                                navForward = false
                                openNote = null
                            })
                        }
                    }
                }
            }

            Surface.SETTINGS -> Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snack) }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    SettingsScreen(vm, onBack = {
                        navForward = false
                        settingsOpen = false
                    })
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
                onOpenSettings = {
                    navForward = true
                    settingsOpen = true
                },
                onOpenNote = {
                    navForward = true
                    openNote = it
                },
                onCapture = { capture = true }
            )
        }
    }

    if (capture) CaptureSheet(vm, onDismiss = { capture = false })
}

/**
 * Depth navigation must reverse when the user goes back, otherwise returning *out* of a screen
 * replays the motion of entering it — the surface is pushed further in the direction it should be
 * leaving, which reads as the app fighting the user.
 *
 * Forward: the overlay arrives from the trailing edge, the shell parallaxes back and dims.
 * Return: exactly mirrored — the overlay retreats to the trailing edge, the shell eases home.
 * The shell never slides fully off; a short parallax reads as depth rather than as two unrelated
 * screens swapping places.
 *
 * `internal` rather than private so the direction contract is covered by unit tests; the motion
 * itself cannot be asserted from a screenshot because a capture costs far longer than the animation.
 */
internal fun overlayTransition(
    reduceMotion: Boolean,
    forward: Boolean
): androidx.compose.animation.ContentTransform {
    if (reduceMotion) return EnterTransition.None togetherWith ExitTransition.None

    // Note the deliberate mismatch: this Compose version types the spec as FiniteAnimationSpec<IntOffset>
    // while the offset lambda is Function1<Int, Int> (container width in, pixel offset out).
    val pageSpec: FiniteAnimationSpec<IntOffset> =
        if (reduceMotion) snap() else tween(Motion.PAGE_MS, easing = Motion.Emphasized)
    val fadeSpec = tween<Float>(Motion.PAGE_MS, easing = Motion.Settle)
    val parallaxFadeSpec = tween<Float>(Motion.PAGE_MS / 2, easing = Motion.Settle)

    // Direction lives in DepthMotion so it stays unit-testable; here it is only turned into pixels.
    // Signs are resolved against the measured width, and IntOffset is only ever built inside those
    // lambdas, which is why the spec above talks about IntOffset while the maths is done in Int.
    val depth = DepthMotion.of(forward)

    return if (forward) {
        val overlayIn = slideInHorizontally(pageSpec) { (it * depth.overlayFrom).toInt() } + fadeIn(fadeSpec)
        val shellBack = slideOutHorizontally(pageSpec) { (it * depth.shellFrom).toInt() } + fadeOut(parallaxFadeSpec)
        overlayIn togetherWith shellBack
    } else {
        val overlayOut = slideOutHorizontally(pageSpec) { (it * depth.overlayFrom).toInt() } + fadeOut(fadeSpec)
        val shellHome = slideInHorizontally(pageSpec) { (it * depth.shellFrom).toInt() } + fadeIn(fadeSpec)
        shellHome togetherWith overlayOut
    }
}

/**
 * Pull-to-refresh answer, pinned to the top-start corner.
 *
 * The stock indicator rides down from the top-centre as the list is dragged, which is the motion
 * that reads as wrong here. This one only fades in place, following the drag distance so the
 * gesture still feels answered, and it disappears the moment the drag is released — ongoing status
 * is the corner chip's job, not this one's.
 *
 * Written against [PullToRefreshState.distanceFraction] rather than the defaults' Indicator, whose
 * parameters are not part of a stable contract across Compose versions.
 */
@Composable
private fun BoxScope.CornerPullIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean
) {
    val drag = state.distanceFraction.coerceIn(0f, 1f)
    val visible = isRefreshing || drag > 0f
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(160)),
        modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 12.dp)
    ) {
        CircularProgressIndicator(
            progress = { if (isRefreshing) 1f else drag },
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { alpha = 0.45f },
            strokeWidth = 2.dp
        )
    }
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
                navigationIcon = {
                    // Living inside the toolbar is what puts the status on the same horizontal line as
                    // the settings action, and it is what keeps page content from shifting: the bar
                    // reserves its height whether or not anything happens to be running.
                    BusyChip(
                        label = when {
                            sync.isSyncing -> "正在同步……"
                            captureInProgress -> "正在保存……"
                            else -> null
                        },
                        reduceMotion = reduceMotion
                    )
                },
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
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = sync.isSyncing,
                onRefresh = { vm.refresh() },
                state = pullState,
                // The stock indicator rides down from the top-centre as you pull, which is the
                // motion that reads as wrong here. Scaled down and blended out it still answers the
                // drag, while the corner chip below carries the actual status.
                indicator = { CornerPullIndicator(state = pullState, isRefreshing = sync.isSyncing) },
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
        }
    }
}

/**
 * Status badge for work in flight, rendered in the toolbar's navigation slot.
 *
 * Sizing is intentionally constant across every state: only alpha and a small scale animate, so the
 * toolbar measures the same whether the chip is visible or not and nothing below it ever shifts.
 * A travelling indicator draws the eye to the motion rather than to the state, so this one only
 * fades — it is either there or it is not.
 */
@Composable
private fun BusyChip(label: String?, reduceMotion: Boolean) {
    AnimatedVisibility(
        visible = label != null,
        enter = if (reduceMotion) EnterTransition.None
        else fadeIn(tween(Motion.ENTER_MS, easing = Motion.Settle)) +
            scaleIn(initialScale = 0.92f, animationSpec = Motion.arrive(reduceMotion)),
        exit = if (reduceMotion) ExitTransition.None
        else fadeOut(tween(140)) + scaleOut(targetScale = 0.94f, animationSpec = tween(140))
    ) {
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .softGlass(radius = 18.dp, emphasized = true, shadow = 6.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(label.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1)
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
