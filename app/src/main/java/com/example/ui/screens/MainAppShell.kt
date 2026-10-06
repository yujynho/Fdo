package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.ActiveVideoPlayback
import com.example.ui.MainViewModel
import com.example.ui.ScreenState
import com.example.ui.components.ExoPlayerOverlay
import com.example.ui.components.GoPlayer
import com.example.ui.components.PhotosetLightbox
import com.example.ui.components.SmoothProgressIndicator
import com.example.ui.components.BubbleLoadingAnimation
import com.example.ui.components.HorizontalBubbleLoadingAnimation
import com.example.ui.screens.ResourceLoadingScreen
import androidx.compose.ui.layout.ContentScale
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette
import kotlinx.coroutines.launch

private val DialogScrim = Color.Black.copy(alpha = 0.65f) // BG-FIX

val LocalTopBarContent = compositionLocalOf<MutableState<(@Composable () -> Unit)?>> {
    mutableStateOf(null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell(viewModel: MainViewModel) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val currentScreen by viewModel.screenState.collectAsStateWithLifecycle()
    val navDirection by viewModel.navDirection.collectAsStateWithLifecycle()
    val activeVideo by viewModel.activeVideo.collectAsStateWithLifecycle()
    val activeLightbox by viewModel.activeLightbox.collectAsStateWithLifecycle()
    val resolvingStatus by viewModel.resolvingVideoStatus.collectAsStateWithLifecycle()
    val resolvingCardId by viewModel.resolvingCardId.collectAsStateWithLifecycle()
    val videoResolutionError by viewModel.videoResolutionError.collectAsStateWithLifecycle()
    val currentSettings by viewModel.settings.collectAsStateWithLifecycle()
    val activeInlineVideo by viewModel.activeInlineVideo.collectAsStateWithLifecycle()
    val isAppResourcesLoading by viewModel.isAppResourcesLoading.collectAsStateWithLifecycle()
    val resourceLoadingStatus by viewModel.resourceLoadingStatus.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var isInPipMode by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }

    DisposableEffect(activity) {
        if (activity != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
                isInPipMode = info.isInPictureInPictureMode
                if (info.isInPictureInPictureMode) {
                    if (activeVideo == null && activeInlineVideo != null) {
                        viewModel.enterPipFromInline(
                            activeInlineVideo!!.cardId,
                            viewModel.sharedPlayerManager.getPlayer().currentPosition
                        )
                    }
                } else {
                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    if (viewModel.enteredPipFromInlineCard) {
                        viewModel.returnToInlineFromPip()
                    }
                }
            }
            activity.addOnPictureInPictureModeChangedListener(listener)
            onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
        } else {
            onDispose { }
        }
    }

    // Smooth App Launch Entrance Animation (Matches Add Scene motion)
    var appEntranceVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        appEntranceVisible = true
    }

    // Handle back button press
    BackHandler(enabled = true) {
        if (activeLightbox != null) {
            viewModel.closeLightbox()
        } else if (activeVideo != null) {
            viewModel.closeVideo()
        } else if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else {
            val handled = viewModel.navigateBack()
            if (!handled) {
                // At root, let system handle exit
            }
        }
    }

    val topBarMap = remember { mutableStateMapOf<ScreenState, @Composable () -> Unit>() }
    val topBarContent = remember(currentScreen) {
        val state = mutableStateOf<(@Composable () -> Unit)?>(null)
        object : MutableState<(@Composable () -> Unit)?> {
            override var value: (@Composable () -> Unit)?
                get() = state.value
                set(newValue) {
                    if (state.value !== newValue) {
                        state.value = newValue
                        if (newValue != null) {
                            topBarMap[currentScreen] = newValue
                        }
                    }
                }
            override fun component1(): (@Composable () -> Unit)? = state.value
            override fun component2(): ((@Composable () -> Unit)?) -> Unit = { value = it }
        }
    }
    CompositionLocalProvider(LocalTopBarContent provides topBarContent) {
        if (isInPipMode) {
            activeVideo?.let { video ->
                GoPlayer(
                    title = video.title,
                    qualities = video.qualities,
                    subtitles = video.subtitles,
                    defaultHeaders = video.headers,
                    initialPositionMs = video.initialPositionMs,
                    startInLandscape = false,
                    exoPlayer = viewModel.sharedPlayerManager.getPlayer(),
                    onClose = { viewModel.closeVideo() }
                )
            }
        } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            scrimColor = Color.Black.copy(alpha = 0.5f),
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = palette.cardBg, // BG-FIX
                    drawerContentColor = palette.textPrimary,
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_app_icon_full),
                                contentDescription = "Goony Logo",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                            )
                            Text("Goony", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = palette.textPrimary)
                        }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = palette.border)
                    Spacer(modifier = Modifier.height(12.dp))

                    val isHomeSelected = currentScreen is ScreenState.Home
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_home),
                                contentDescription = "Home",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Home", fontWeight = if (isHomeSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = isHomeSelected,
                        onClick = {
                            viewModel.navigateTo(ScreenState.Home)
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedTextColor = accent,
                            selectedIconColor = accent,
                            unselectedTextColor = palette.textPrimary,
                            unselectedIconColor = palette.textSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    val isActorsSelected = currentScreen is ScreenState.Actors || currentScreen is ScreenState.ActorScenes
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_actor),
                                contentDescription = "Actors",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Actors", fontWeight = if (isActorsSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = isActorsSelected,
                        onClick = {
                            viewModel.navigateTo(ScreenState.Actors)
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedTextColor = accent,
                            selectedIconColor = accent,
                            unselectedTextColor = palette.textPrimary,
                            unselectedIconColor = palette.textSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    val isStudiosSelected = currentScreen is ScreenState.Studios || currentScreen is ScreenState.StudioScenes
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_studio),
                                contentDescription = "Studios",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Studios", fontWeight = if (isStudiosSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = isStudiosSelected,
                        onClick = {
                            viewModel.navigateTo(ScreenState.Studios)
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedTextColor = accent,
                            selectedIconColor = accent,
                            unselectedTextColor = palette.textPrimary,
                            unselectedIconColor = palette.textSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    val isBookmarksSelected = currentScreen is ScreenState.Bookmarks
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_bookmark),
                                contentDescription = "Bookmarks",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Bookmarks", fontWeight = if (isBookmarksSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = isBookmarksSelected,
                        onClick = {
                            viewModel.navigateTo(ScreenState.Bookmarks)
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedTextColor = accent,
                            selectedIconColor = accent,
                            unselectedTextColor = palette.textPrimary,
                            unselectedIconColor = palette.textSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    val isStashDbSelected = currentScreen is ScreenState.StashDb
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_stashdb),
                                contentDescription = "StashDB",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("StashDB", fontWeight = if (isStashDbSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = isStashDbSelected,
                        onClick = {
                            viewModel.navigateTo(ScreenState.StashDb)
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedTextColor = accent,
                            selectedIconColor = accent,
                            unselectedTextColor = palette.textPrimary,
                            unselectedIconColor = palette.textSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(color = palette.border)
                    Spacer(modifier = Modifier.height(12.dp))

                    val isSettingsSelected = currentScreen is ScreenState.Settings
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_settings),
                                contentDescription = "Settings",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Settings", fontWeight = if (isSettingsSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = isSettingsSelected,
                        onClick = {
                            viewModel.navigateTo(ScreenState.Settings)
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedTextColor = accent,
                            selectedIconColor = accent,
                            unselectedTextColor = palette.textPrimary,
                            unselectedIconColor = palette.textSecondary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = palette.bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { _ ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedVisibility(
                    visible = appEntranceVisible,
                    enter = slideInVertically(
                        animationSpec = tween(340, easing = FastOutSlowInEasing)
                    ) { fullHeight -> fullHeight / 5 } + fadeIn(animationSpec = tween(300)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val openDrawerLambda: () -> Unit = { coroutineScope.launch { drawerState.open() } }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            val isBack = navDirection == MainViewModel.NavigationDirection.BACK
                            if (isBack) {
                                (slideInHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> -width / 4 } +
                                        fadeIn(animationSpec = tween(240, easing = LinearOutSlowInEasing)) +
                                        scaleIn(animationSpec = tween(280, easing = FastOutSlowInEasing), initialScale = 0.96f))
                                    .togetherWith(
                                        slideOutHorizontally(animationSpec = tween(260, easing = FastOutSlowInEasing)) { width -> width / 3 } +
                                                fadeOut(animationSpec = tween(200)) +
                                                scaleOut(animationSpec = tween(260), targetScale = 0.96f)
                                    )
                            } else {
                                (slideInHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> width / 3 } +
                                        fadeIn(animationSpec = tween(240, easing = LinearOutSlowInEasing)) +
                                        scaleIn(animationSpec = tween(280, easing = FastOutSlowInEasing), initialScale = 0.96f))
                                    .togetherWith(
                                        slideOutHorizontally(animationSpec = tween(260, easing = FastOutSlowInEasing)) { width -> -width / 4 } +
                                                fadeOut(animationSpec = tween(200)) +
                                                scaleOut(animationSpec = tween(260), targetScale = 0.96f)
                                    )
                            }
                        },
                        label = "screen_motion_transition"
                    ) { screen ->
                        when (screen) {
                            is ScreenState.Home -> HomeScreen(
                                viewModel = viewModel,
                                onOpenDrawer = openDrawerLambda
                            )
                            is ScreenState.Bookmarks -> BookmarksScreen(
                                viewModel = viewModel,
                                onOpenDrawer = openDrawerLambda
                            )
                            is ScreenState.AddEditLink -> AddEditLinkScreen(viewModel, screen.linkId)
                            is ScreenState.Actors -> ActorManagementScreen(viewModel)
                            is ScreenState.AddEditActor -> ActorManagementScreen(viewModel)
                            is ScreenState.ActorScenes -> HomeScreen(
                                viewModel = viewModel,
                                onOpenDrawer = openDrawerLambda
                            )
                            is ScreenState.Studios -> StudioManagementScreen(viewModel)
                            is ScreenState.AddEditStudio -> StudioManagementScreen(viewModel)
                            is ScreenState.StudioScenes -> HomeScreen(
                                viewModel = viewModel,
                                onOpenDrawer = openDrawerLambda
                            )
                            is ScreenState.StashDb -> StashDbScreen(
                                viewModel = viewModel,
                                onOpenDrawer = openDrawerLambda
                            )
                            is ScreenState.Settings -> SettingsScreen(viewModel)
                        }
                    }
                }

                // GoPlayer / ExoPlayer Video Player Overlay
                activeVideo?.let { video ->
                    GoPlayer(
                        title = video.title,
                        qualities = video.qualities,
                        subtitles = video.subtitles,
                        defaultHeaders = video.headers,
                        initialPositionMs = video.initialPositionMs,
                        startInLandscape = video.startInLandscape,
                        exoPlayer = viewModel.sharedPlayerManager.getPlayer(),
                        onClose = { viewModel.closeVideo() }
                    )
                }

                // High-Res Photoset Lightbox Overlay
                activeLightbox?.let { (images, startIndex) ->
                    PhotosetLightbox(
                        images = images,
                        initialIndex = startIndex,
                        onClose = { viewModel.closeLightbox() }
                    )
                }

                // Video Resolving / Debrid Progress Overlay (Only for non-card actions, cards handle inline)
                if (resolvingCardId == null) {
                    resolvingStatus?.let { statusText ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DialogScrim),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .widthIn(max = 320.dp)
                                    .padding(20.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = palette.cardBg)
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_app_icon_full),
                                        contentDescription = "App Icon",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                    )
                                    HorizontalBubbleLoadingAnimation(
                                        bubbleColor = accent,
                                        bubbleCount = 4,
                                        bubbleSize = 11.dp
                                    )
                                    Text(
                                        text = statusText,
                                        color = palette.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Video Resolution / Debrid Error Dialog (Only shown globally if not triggered by an inline card)
                if (resolvingCardId == null) {
                    videoResolutionError?.let { errText ->
                        AlertDialog(
                            onDismissRequest = { viewModel.dismissVideoError() },
                            icon = {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            title = {
                                Text(
                                    text = "Stream Playback Error",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            },
                            text = {
                                Text(
                                    text = errText,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = palette.textSecondary
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.dismissVideoError() },
                                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                                ) {
                                    Text("OK")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
}
}
