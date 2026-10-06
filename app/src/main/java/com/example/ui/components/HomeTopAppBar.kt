package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.ActorEntity
import com.example.data.local.entity.StudioEntity
import com.example.ui.ScreenState
import com.example.ui.SortMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(
    currentScreen: ScreenState,
    targetActor: ActorEntity?,
    targetStudio: StudioEntity?,
    actorsMap: Map<String, String>,
    studiosMap: Map<String, String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    currentSort: SortMode,
    onSortChange: (SortMode) -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigateBack: () -> Unit,
    onAddScene: () -> Unit,
    onEditActor: () -> Unit,
    onEditStudio: () -> Unit
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    TopAppBar(
        title = {
            if (isSearchExpanded) {
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("search_scenes_input"),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search scenes, actors, studios...",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            } else {
                val headerTitle = when {
                    targetActor != null -> targetActor.name
                    targetStudio != null -> targetStudio.name
                    currentScreen is ScreenState.ActorScenes -> {
                        val id = currentScreen.actorId
                        actorsMap[id] ?: id
                    }
                    currentScreen is ScreenState.StudioScenes -> {
                        val id = currentScreen.studioId
                        studiosMap[id] ?: id
                    }
                    else -> "Goony"
                }
                Text(
                    text = headerTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                )
            }
        },
        navigationIcon = {
            if (isSearchExpanded) {
                IconButton(
                    onClick = {
                        isSearchExpanded = false
                        onSearchQueryChange("")
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close Search"
                    )
                }
            } else if (targetActor != null || targetStudio != null || currentScreen is ScreenState.ActorScenes || currentScreen is ScreenState.StudioScenes) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            } else {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.testTag("open_drawer_button")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_app_menu),
                        contentDescription = "Open Drawer"
                    )
                }
            }
        },
        actions = {
            if (isSearchExpanded) {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.testTag("clear_search_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_action_cancel),
                            contentDescription = "Clear Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            isSearchExpanded = false
                            onSearchQueryChange("")
                        },
                        modifier = Modifier.testTag("close_search_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_action_cancel),
                            contentDescription = "Close Search"
                        )
                    }
                }
            } else {
                // Search Action
                IconButton(
                    onClick = { isSearchExpanded = true },
                    modifier = Modifier.testTag("search_action_button")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_app_search),
                        contentDescription = "Search"
                    )
                }

                // Sort Action with Dropdown Menu
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("sort_action_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_app_sort),
                            contentDescription = "Sort Mode"
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        val sortOptions = listOf(
                            SortMode.CARD_NEWEST to "New",
                            SortMode.CARD_OLDEST to "Old",
                            SortMode.RECENTLY_ADDED to "Recently Added",
                            SortMode.OLDEST_ADDED to "Oldest Added",
                            SortMode.TITLE_AZ to "A - Z",
                            SortMode.TITLE_ZA to "Z - A"
                        )

                        sortOptions.forEach { (mode, label) ->
                            val isSelected = currentSort == mode ||
                                (mode == SortMode.CARD_NEWEST && currentSort == SortMode.NEWEST) ||
                                (mode == SortMode.CARD_OLDEST && currentSort == SortMode.OLDEST)

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    onSortChange(mode)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                // Edit Actor/Studio Action in Header
                if (targetActor != null) {
                    IconButton(
                        onClick = onEditActor,
                        modifier = Modifier.testTag("edit_actor_header_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_edit_pencil),
                            contentDescription = "Edit Actor"
                        )
                    }
                } else if (targetStudio != null) {
                    IconButton(
                        onClick = onEditStudio,
                        modifier = Modifier.testTag("edit_studio_header_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_edit_pencil),
                            contentDescription = "Edit Studio"
                        )
                    }
                }

                // Add Scene Action - ONLY on Main Screen (Home)
                if (currentScreen is ScreenState.Home) {
                    IconButton(
                        onClick = onAddScene,
                        modifier = Modifier.testTag("add_scene_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_app_add),
                            contentDescription = "Add Scene"
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
