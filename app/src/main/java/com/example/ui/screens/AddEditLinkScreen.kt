package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.entity.LinkEntity
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLinkScreen(
    viewModel: MainViewModel,
    linkId: String?,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val links by viewModel.allLinks.collectAsStateWithLifecycle()
    val actors by viewModel.allActors.collectAsStateWithLifecycle()
    val studios by viewModel.allStudios.collectAsStateWithLifecycle()
    val isFetchingMagnet by viewModel.isFetchingMagnet.collectAsStateWithLifecycle()

    val actorUsageCounts by viewModel.actorSceneCounts.collectAsStateWithLifecycle()
    val studioUsageCounts by viewModel.studioSceneCounts.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelMagnetFetch()
        }
    }

    val existingLink = remember(linkId, links) {
        links.firstOrNull { it.id == linkId }
    }

    var title by remember { mutableStateOf(existingLink?.title ?: "") }
    var coverImage by remember { mutableStateOf(existingLink?.coverImage ?: "") }
    val coverOffset = existingLink?.coverOffset ?: 50f
    val aspectRatio = existingLink?.aspectRatio ?: "16:9"
    var urlHD by remember { mutableStateOf(existingLink?.urlHD ?: "") }
    var url4K by remember { mutableStateOf(existingLink?.url4K ?: "") }
    var magnetHD by remember { mutableStateOf(existingLink?.magnet ?: "") }
    var magnet4K by remember { mutableStateOf(existingLink?.magnet4K ?: "") }
    var torrentUrlHD by remember { mutableStateOf(existingLink?.torrentUrlHD ?: "") }
    var torrentUrl4K by remember { mutableStateOf(existingLink?.torrentUrl4K ?: "") }
    var torrentSiteName by remember { mutableStateOf(existingLink?.torrentSiteName ?: "") }
    var selectedActorIds by remember { mutableStateOf(existingLink?.actorIds ?: emptyList()) }
    var selectedStudioIds by remember { mutableStateOf(existingLink?.studioIds ?: emptyList()) }
    var assignedDate by remember {
        mutableStateOf(existingLink?.assignedDate ?: existingLink?.createdAt ?: System.currentTimeMillis())
    }

    // Populate state safely when existingLink loads asynchronously from Room
    LaunchedEffect(existingLink) {
        if (existingLink != null && title.isEmpty()) {
            title = existingLink.title
            coverImage = existingLink.coverImage
            urlHD = existingLink.urlHD ?: ""
            url4K = existingLink.url4K ?: ""
            magnetHD = existingLink.magnet ?: ""
            magnet4K = existingLink.magnet4K ?: ""
            torrentUrlHD = existingLink.torrentUrlHD ?: ""
            torrentUrl4K = existingLink.torrentUrl4K ?: ""
            torrentSiteName = existingLink.torrentSiteName ?: ""
            selectedActorIds = existingLink.actorIds
            selectedStudioIds = existingLink.studioIds
            assignedDate = existingLink.assignedDate ?: existingLink.createdAt
        }
    }

    var showStudioPicker by remember { mutableStateOf(false) }
    var showActorPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val formattedAssignedDate = remember(assignedDate) {
        SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(assignedDate))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = assignedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { assignedDate = it }
                        showDatePicker = false
                    },
                    shape = CircleShape
                ) {
                    Text("OK", color = accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    shape = CircleShape
                ) {
                    Text("Cancel", color = palette.textSecondary)
                }
            },
            shape = RoundedCornerShape(28.dp),
            colors = DatePickerDefaults.colors(containerColor = palette.cardBg)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = palette.cardBg,
                    titleContentColor = palette.textPrimary,
                    headlineContentColor = palette.textPrimary,
                    weekdayContentColor = palette.textSecondary,
                    subheadContentColor = palette.textSecondary,
                    yearContentColor = palette.textPrimary,
                    currentYearContentColor = accent,
                    selectedYearContentColor = Color.White,
                    selectedYearContainerColor = accent,
                    dayContentColor = palette.textPrimary,
                    selectedDayContentColor = Color.White,
                    selectedDayContainerColor = accent,
                    todayContentColor = accent,
                    todayDateBorderColor = accent
                )
            )
        }
    }

    val sortedStudioItems = remember(studios, studioUsageCounts) {
        studios.map { studio ->
            TagPickerItem(
                id = studio.id,
                name = studio.name,
                usageCount = studioUsageCounts[studio.id] ?: 0
            )
        }.sortedWith(
            compareByDescending<TagPickerItem> { it.usageCount }.thenBy { it.name.lowercase() }
        )
    }

    val sortedActorItems = remember(actors, actorUsageCounts) {
        actors.map { actor ->
            TagPickerItem(
                id = actor.id,
                name = actor.name,
                usageCount = actorUsageCounts[actor.id] ?: 0
            )
        }.sortedWith(
            compareByDescending<TagPickerItem> { it.usageCount }.thenBy { it.name.lowercase() }
        )
    }

    if (showStudioPicker) {
        TagMultiSelectDialog(
            title = "Tag Studio",
            items = sortedStudioItems,
            selectedIds = selectedStudioIds.take(1).toSet(),
            singleSelection = true,
            onConfirm = { updated ->
                selectedStudioIds = updated.take(1).toList()
                showStudioPicker = false
            },
            onDismiss = { showStudioPicker = false }
        )
    }

    if (showActorPicker) {
        TagMultiSelectDialog(
            title = "Tag Actors",
            items = sortedActorItems,
            selectedIds = selectedActorIds.toSet(),
            singleSelection = false,
            onConfirm = { updated ->
                selectedActorIds = updated.toList()
                showActorPicker = false
            },
            onDismiss = { showActorPicker = false }
        )
    }

    val cardShape = RoundedCornerShape(20.dp)
    val chipShape = RoundedCornerShape(24.dp)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existingLink != null) "Edit Scene" else "Add Scene",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (title.isNotBlank()) {
                                val linkToSave = (existingLink ?: LinkEntity(id = UUID.randomUUID().toString(), title = "")).copy(
                                    title = title.trim(),
                                    coverImage = coverImage.trim(),
                                    coverOffset = coverOffset,
                                    aspectRatio = aspectRatio,
                                    urlHD = urlHD.trim().ifEmpty { null },
                                    url4K = url4K.trim().ifEmpty { null },
                                    magnet = magnetHD.trim().ifEmpty { null },
                                    magnet4K = magnet4K.trim().ifEmpty { null },
                                    torrentUrlHD = torrentUrlHD.trim().ifEmpty { null },
                                    torrentUrl4K = torrentUrl4K.trim().ifEmpty { null },
                                    torrentSiteName = torrentSiteName.trim().ifEmpty { null },
                                    actorIds = selectedActorIds,
                                    studioIds = selectedStudioIds,
                                    assignedDate = assignedDate
                                )
                                viewModel.saveLink(linkToSave)
                                viewModel.navigateBack()
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.testTag("save_scene_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_action_save),
                            contentDescription = "Save",
                            tint = if (title.isNotBlank()) accent else palette.textMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface,
                    titleContentColor = palette.textPrimary,
                    navigationIconContentColor = palette.textPrimary,
                    actionIconContentColor = palette.textSecondary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    keyboardController?.hide()
                }
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Title Input Row with Fetch Magnet Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SceneInputField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Title *",
                    trailingIcon = {
                        PasteTrailingIcon(onPaste = { title = it })
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "scene_title_input"
                )

                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = if (isFetchingMagnet) 0.5f else 1f),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("fetch_magnet_button")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                enabled = !isFetchingMagnet,
                                onClick = {
                                    triggerVibrate(context)
                                    val selectedActors = actors.filter { selectedActorIds.contains(it.id) }
                                    val selectedStudios = studios.filter { selectedStudioIds.contains(it.id) }
                                    viewModel.fetchMagnet(
                                        title = title,
                                        selectedActors = selectedActors,
                                        selectedStudios = selectedStudios,
                                        assignedDate = assignedDate,
                                        urlHD = urlHD,
                                        url4K = url4K,
                                        onSuccess = { result ->
                                            triggerVibrate(context)
                                            val oldHD = magnetHD
                                            val old4K = magnet4K
                                            var replacedAny = false
                                            val filledQualities = mutableListOf<String>()

                                            if (result.magnet1080p != null) {
                                                if (magnetHD.isNotBlank() && magnetHD != result.magnet1080p) {
                                                    replacedAny = true
                                                }
                                                magnetHD = result.magnet1080p
                                                filledQualities.add("1080p")
                                            }
                                            if (result.url1080p != null) {
                                                torrentUrlHD = result.url1080p
                                            }

                                            if (result.magnet2160p != null) {
                                                if (magnet4K.isNotBlank() && magnet4K != result.magnet2160p) {
                                                    replacedAny = true
                                                }
                                                magnet4K = result.magnet2160p
                                                filledQualities.add("4K")
                                            }
                                            if (result.url2160p != null) {
                                                torrentUrl4K = result.url2160p
                                            }

                                            if (result.sourceSite.isNotBlank()) {
                                                torrentSiteName = result.sourceSite
                                            }

                                            val qStr = if (filledQualities.isNotEmpty()) " (${filledQualities.joinToString(" + ")})" else ""
                                            Toast.makeText(
                                                context,
                                                "Magnet links retrieved successfully!$qStr",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            if (replacedAny) {
                                                coroutineScope.launch {
                                                    val snackResult = snackbarHostState.showSnackbar(
                                                        message = "Magnet replaced",
                                                        actionLabel = "Undo",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (snackResult == SnackbarResult.ActionPerformed) {
                                                        magnetHD = oldHD
                                                        magnet4K = old4K
                                                    }
                                                }
                                            }
                                        },
                                        onNoResult = {
                                            Toast.makeText(
                                                context,
                                                "No verified torrent found for this actor/studio on this date.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isFetchingMagnet) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_magnet),
                                contentDescription = "Fetch Magnet",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // 2. Date & Cover on the Same Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val dateInteractionSource = remember { MutableInteractionSource() }
                LaunchedEffect(dateInteractionSource) {
                    dateInteractionSource.interactions.collect { interaction ->
                        if (interaction is PressInteraction.Release) {
                            showDatePicker = true
                        }
                    }
                }

                SceneInputField(
                    value = formattedAssignedDate,
                    onValueChange = { },
                    readOnly = true,
                    placeholder = "Date",
                    interactionSource = dateInteractionSource,
                    trailingIcon = {
                        IconButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_app_calendar),
                                contentDescription = "Select Date",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "scene_date_input"
                )

                SceneInputField(
                    value = coverImage,
                    onValueChange = { coverImage = it },
                    placeholder = "Cover",
                    trailingIcon = {
                        PasteTrailingIcon(onPaste = { coverImage = it })
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "cover_image_input"
                )
            }

            // 3. Preview Card (16:9 Ratio with Rounded Corners)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Preview",
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = palette.cardBg),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    if (coverImage.isNotBlank()) {
                        AsyncImage(
                            model = coverImage,
                            contentDescription = "Cover Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "No cover image",
                                    color = palette.textMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // 4. Stream Section
            Text(
                text = "Stream",
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SceneInputField(
                    value = magnetHD,
                    onValueChange = { magnetHD = it },
                    placeholder = "HD Magnet",
                    trailingIcon = {
                        PasteTrailingIcon(onPaste = { magnetHD = it })
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "hd_magnet_input"
                )
                SceneInputField(
                    value = magnet4K,
                    onValueChange = { magnet4K = it },
                    placeholder = "4K Magnet",
                    trailingIcon = {
                        PasteTrailingIcon(onPaste = { magnet4K = it })
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "4k_magnet_input"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SceneInputField(
                    value = urlHD,
                    onValueChange = { urlHD = it },
                    placeholder = "HD URL",
                    trailingIcon = {
                        PasteTrailingIcon(onPaste = { urlHD = it })
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "hd_url_input"
                )
                SceneInputField(
                    value = url4K,
                    onValueChange = { url4K = it },
                    placeholder = "4K URL",
                    trailingIcon = {
                        PasteTrailingIcon(onPaste = { url4K = it })
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "4k_url_input"
                )
            }

            // 5. Native Tag Actors Section
            val selectedActorsList = remember(selectedActorIds, actors) {
                actors.filter { selectedActorIds.contains(it.id) }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tag Actors ${if (selectedActorsList.isNotEmpty()) "(${selectedActorsList.size})" else ""}",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.14f),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { showActorPicker = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_app_add),
                                contentDescription = "Add Actor",
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (selectedActorsList.isEmpty()) {
                    Text(
                        text = "No actors tagged. Tap + to choose from your actors.",
                        color = palette.textMuted,
                        fontSize = 12.sp
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(selectedActorsList, key = { it.id }) { actor ->
                            InputChip(
                                selected = true,
                                onClick = {
                                    selectedActorIds = selectedActorIds - actor.id
                                },
                                label = { Text(actor.name, fontSize = 12.sp) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                shape = chipShape,
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.18f),
                                    selectedLabelColor = palette.textPrimary
                                )
                            )
                        }
                    }
                }
            }

            // 6. Native Tag Studio Section
            val selectedStudiosList = remember(selectedStudioIds, studios) {
                studios.filter { selectedStudioIds.contains(it.id) }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tag Studio ${if (selectedStudiosList.isNotEmpty()) "(${selectedStudiosList.size})" else ""}",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.14f),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { showStudioPicker = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_app_add),
                                contentDescription = "Add Studio",
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (selectedStudiosList.isEmpty()) {
                    Text(
                        text = "No studios tagged. Tap + to choose from your studios.",
                        color = palette.textMuted,
                        fontSize = 12.sp
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(selectedStudiosList, key = { it.id }) { studio ->
                            InputChip(
                                selected = true,
                                onClick = {
                                    selectedStudioIds = selectedStudioIds - studio.id
                                },
                                label = { Text(studio.name, fontSize = 12.sp) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                shape = chipShape,
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.18f),
                                    selectedLabelColor = palette.textPrimary
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
