package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.repository.VaultRepository
import com.example.network.*
import com.example.ui.player.SharedPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class SortMode {
    CARD_NEWEST,      // New (Card Date - Newest)
    CARD_OLDEST,      // Old (Card Date - Oldest)
    RECENTLY_ADDED,   // Recently Added (Added to app date - Newest)
    OLDEST_ADDED,     // Oldest Added (Added to app date - Oldest)
    NEWEST,           // Backward compatibility (maps to CARD_NEWEST)
    OLDEST,           // Backward compatibility (maps to CARD_OLDEST)
    TITLE_AZ,
    TITLE_ZA
}

enum class StashSearchType {
    ACTORS,
    STUDIO,
    SEXMEX
}

enum class SettingsSection {
    MAIN_MENU,
    DISPLAY,
    PRIVACY,
    INTEGRATIONS,
    FILTER,
    DATA_BACKUP,
    SAMPLE_DATA
}

sealed class ScreenState {
    object Home : ScreenState()
    object Bookmarks : ScreenState()
    data class AddEditLink(val linkId: String? = null) : ScreenState()
    object Actors : ScreenState()
    data class AddEditActor(val actorId: String? = null) : ScreenState()
    data class ActorScenes(val actorId: String) : ScreenState()
    object Studios : ScreenState()
    data class AddEditStudio(val studioId: String? = null) : ScreenState()
    data class StudioScenes(val studioId: String) : ScreenState()
    object StashDb : ScreenState()
    object Settings : ScreenState()
}

data class ActiveVideoPlayback(
    val title: String,
    val qualities: List<StreamQuality>,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val initialPositionMs: Long = 0L,
    val startInLandscape: Boolean = false
)

data class ActiveInlineVideoPlayback(
    val cardId: String,
    val title: String,
    val qualities: List<StreamQuality>,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val headers: Map<String, String> = emptyMap()
)

private data class StashTabCache(
    val query: String = "",
    val scenes: List<StashScene> = emptyList(),
    val selectedPerformer: StashPerformer? = null,
    val selectedStudio: StashStudio? = null,
    val totalCount: Int = 0,
    val currentPage: Int = 1,
    val canLoadMore: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: VaultRepository

    // App Initial Resource Loading Screen States
    private val _isAppResourcesLoading = MutableStateFlow(true)
    val isAppResourcesLoading: StateFlow<Boolean> = _isAppResourcesLoading.asStateFlow()

    private val _resourceLoadingStatus = MutableStateFlow("جاري فحص وتجهيز الموارد...")
    val resourceLoadingStatus: StateFlow<String> = _resourceLoadingStatus.asStateFlow()

    fun triggerReloadResources() {
        seedInitialDataIfEmpty()
    }

    init {
        val db = AppDatabase.getInstance(application)
        repository = VaultRepository(db)
        seedInitialDataIfEmpty()
    }

    private fun seedInitialDataIfEmpty() {
        viewModelScope.launch(Dispatchers.IO) {
            _isAppResourcesLoading.value = true
            _resourceLoadingStatus.value = "جاري تهيئة قاعدة البيانات المحلية..."
            delay(450L)

            val existingLinks = repository.allLinks.first()
            _resourceLoadingStatus.value = "جاري فحص الروابط والمشاهد والموارد..."

            // Clean obsolete test & demo scenes
            val demoLinksToDelete = existingLinks.filter {
                it.id.startsWith("demo_") || it.id.startsWith("test_scene_") || it.id.startsWith("scene_raissa_")
            }
            demoLinksToDelete.forEach { repository.deleteLinkById(it.id) }

            // Seed default API Keys ONLY ONCE on first install using defaultKeysSeeded flag
            val currentSett = repository.settings.first() ?: SettingsEntity()
            if (!currentSett.defaultKeysSeeded) {
                var updatedSett = currentSett.copy(defaultKeysSeeded = true)
                if (updatedSett.realDebridApiKey.isBlank()) {
                    updatedSett = updatedSett.copy(realDebridApiKey = com.example.data.TestDefaults.RD_KEY)
                }
                if (updatedSett.stashDbApiKey.isBlank()) {
                    updatedSett = updatedSett.copy(stashDbApiKey = com.example.data.TestDefaults.STASHDB_KEY)
                }
                repository.updateSettings(updatedSett)
            }

            // Insert sample test dataset scenes, actors, and studios atomically if empty
            val existingLinkIds = existingLinks.map { it.id }.toSet()
            val existingActors = repository.allActors.first()
            if (existingActors.isEmpty()) {
                repository.insertActors(com.example.data.util.SampleTestDataset.sampleActors)
            }
            val existingStudios = repository.allStudios.first()
            if (existingStudios.isEmpty()) {
                repository.insertStudios(com.example.data.util.SampleTestDataset.sampleStudios)
            }
            val missingSampleScenes = com.example.data.util.SampleTestDataset.sampleScenes.filter { it.id !in existingLinkIds }
            if (missingSampleScenes.isNotEmpty()) {
                repository.insertLinks(missingSampleScenes)
            }

            // Real Resource Pre-caching: Pre-fetch and cache images into Coil Memory & Disk Cache
            try {
                val context = getApplication<Application>()
                val imageLoader = coil.Coil.imageLoader(context)
                val allLinksSample = repository.allLinks.first()
                val allActorsSample = repository.allActors.first()
                val allStudiosSample = repository.allStudios.first()

                val urlsToCache = mutableListOf<String>()
                allLinksSample.take(20).forEach { link ->
                    if (link.coverImage.isNotBlank()) urlsToCache.add(link.coverImage)
                    if (link.galleryUrls.isNotEmpty()) urlsToCache.addAll(link.galleryUrls.take(2))
                }
                allActorsSample.take(15).forEach { actor ->
                    if (actor.imageUrl.isNotBlank()) urlsToCache.add(actor.imageUrl)
                }
                allStudiosSample.take(15).forEach { studio ->
                    studio.logoUrl?.takeIf { it.isNotBlank() }?.let { urlsToCache.add(it) }
                    studio.imageUrl?.takeIf { it.isNotBlank() }?.let { urlsToCache.add(it) }
                }

                urlsToCache.distinct().forEach { url ->
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                    imageLoader.enqueue(request)
                }
            } catch (_: Exception) {}

            delay(150L)
            _isAppResourcesLoading.value = false
        }
    }

    // Navigation Stack / Current Screen & Direction
    enum class NavigationDirection { FORWARD, BACK }

    private val _navDirection = MutableStateFlow(NavigationDirection.FORWARD)
    val navDirection: StateFlow<NavigationDirection> = _navDirection.asStateFlow()

    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Home)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    private val screenStack = Collections.synchronizedList(mutableListOf<ScreenState>(ScreenState.Home))

    // Multi-Key Scroll Position Memory Registry
    private val scrollPositionRegistry = ConcurrentHashMap<String, Pair<Int, Int>>()

    fun saveScrollPosition(key: String, index: Int, offset: Int) {
        scrollPositionRegistry[key] = Pair(index, offset)
    }

    fun getScrollPosition(key: String): Pair<Int, Int> = scrollPositionRegistry[key] ?: Pair(0, 0)

    fun clearScrollPosition(key: String) {
        scrollPositionRegistry.remove(key)
    }

    var homeScrollIndex: Int
        get() = getScrollPosition("feed_home").first
        set(value) = saveScrollPosition("feed_home", value, getScrollPosition("feed_home").second)

    var homeScrollOffset: Int
        get() = getScrollPosition("feed_home").second
        set(value) = saveScrollPosition("feed_home", getScrollPosition("feed_home").first, value)

    var initialSettingsSection: String? = null

    fun navigateTo(screen: ScreenState) {
        if (screen == _screenState.value) return
        synchronized(screenStack) {
            val existingIndex = screenStack.indexOf(screen)
            if (existingIndex >= 0 && existingIndex < screenStack.size - 1) {
                _navDirection.value = NavigationDirection.BACK
                while (screenStack.size > existingIndex + 1) {
                    screenStack.removeAt(screenStack.size - 1)
                }
            } else {
                _navDirection.value = NavigationDirection.FORWARD
                screenStack.add(screen)
            }
        }
        _screenState.value = screen
    }

    fun navigateBack(): Boolean {
        synchronized(screenStack) {
            if (screenStack.size > 1) {
                _navDirection.value = NavigationDirection.BACK
                screenStack.removeAt(screenStack.size - 1)
                _screenState.value = screenStack.last()
                return true
            } else if (_screenState.value != ScreenState.Home) {
                _navDirection.value = NavigationDirection.BACK
                screenStack.clear()
                screenStack.add(ScreenState.Home)
                _screenState.value = ScreenState.Home
                return true
            }
        }
        return false
    }

    // Video Player Overlay & Inline State
    private val _activeVideo = MutableStateFlow<ActiveVideoPlayback?>(null)
    val activeVideo: StateFlow<ActiveVideoPlayback?> = _activeVideo.asStateFlow()

    private val _activeInlineVideo = MutableStateFlow<ActiveInlineVideoPlayback?>(null)
    val activeInlineVideo: StateFlow<ActiveInlineVideoPlayback?> = _activeInlineVideo.asStateFlow()

    val sharedPlayerManager by lazy { SharedPlayerManager(application) }
    private var lastInlineCardId: String? = null

    private val _resolvingCardId = MutableStateFlow<String?>(null)
    val resolvingCardId: StateFlow<String?> = _resolvingCardId.asStateFlow()

    private val _resolvingVideoStatus = MutableStateFlow<String?>(null)
    val resolvingVideoStatus: StateFlow<String?> = _resolvingVideoStatus.asStateFlow()

    private val _videoResolutionError = MutableStateFlow<String?>(null)
    val videoResolutionError: StateFlow<String?> = _videoResolutionError.asStateFlow()

    private var resolveVideoJob: Job? = null

    fun playVideo(rawUrl: String, title: String = "Media Stream", cardId: String? = null) {
        resolveVideoJob?.cancel()
        _videoResolutionError.value = null
        _resolvingCardId.value = cardId

        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            _videoResolutionError.value = "Cannot play empty stream URL."
            _resolvingCardId.value = null
            return
        }

        resolveVideoJob = viewModelScope.launch(Dispatchers.IO) {
            _resolvingVideoStatus.value = if (com.example.network.torrent.MagnetParser.parseHash(trimmed) != null) {
                "Resolving torrent magnet via Debrid..."
            } else {
                "Resolving media stream..."
            }

            try {
                val settingsEntity = repository.getSettingsOnce()
                val orderEnum = when (settingsEntity.debridOrder) {
                    "REAL_DEBRID_FIRST" -> com.example.network.debrid.DebridOrder.REAL_DEBRID_FIRST
                    "TORBOX_FIRST" -> com.example.network.debrid.DebridOrder.TORBOX_FIRST
                    else -> com.example.network.debrid.DebridOrder.AUTO
                }

                val resolved = withTimeout(45_000L) {
                    VideoResolvers.resolve(
                        rawUrl = trimmed,
                        torboxApiKey = settingsEntity.torboxApiKey,
                        realDebridApiKey = settingsEntity.realDebridApiKey,
                        debridOrder = orderEnum
                    )
                }

                if (resolved.qualities.isEmpty()) {
                    _videoResolutionError.value = "No playable media qualities found for this source."
                    _resolvingVideoStatus.value = null
                    return@launch
                }

                val primaryQuality = resolved.qualities.firstOrNull { it.isDefault } ?: resolved.qualities.first()
                _resolvingVideoStatus.value = "Verifying media stream..."

                val validation = MediaUrlValidator.validate(
                    primaryQuality.url,
                    resolved.headers + primaryQuality.headers
                )

                val displayTitle = if (resolved.title.isNotBlank() && resolved.title != "Media Stream") resolved.title else title

                when (validation) {
                    is ValidatedMediaResult.Valid -> {
                        if (cardId != null) {
                            _activeInlineVideo.value = ActiveInlineVideoPlayback(
                                cardId = cardId,
                                title = displayTitle,
                                qualities = resolved.qualities,
                                subtitles = resolved.subtitles,
                                headers = resolved.headers
                            )
                        } else {
                            _activeVideo.value = ActiveVideoPlayback(
                                title = displayTitle,
                                qualities = resolved.qualities,
                                subtitles = resolved.subtitles,
                                headers = resolved.headers
                            )
                        }
                    }
                    is ValidatedMediaResult.Invalid -> {
                        if (primaryQuality.url.startsWith("http://", ignoreCase = true) ||
                            primaryQuality.url.startsWith("https://", ignoreCase = true)
                        ) {
                            if (cardId != null) {
                                _activeInlineVideo.value = ActiveInlineVideoPlayback(
                                    cardId = cardId,
                                    title = displayTitle,
                                    qualities = resolved.qualities,
                                    subtitles = resolved.subtitles,
                                    headers = resolved.headers
                                )
                            } else {
                                _activeVideo.value = ActiveVideoPlayback(
                                    title = displayTitle,
                                    qualities = resolved.qualities,
                                    subtitles = resolved.subtitles,
                                    headers = resolved.headers
                                )
                            }
                        } else {
                            _videoResolutionError.value = validation.reason
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _videoResolutionError.value = e.message ?: "Failed to resolve media stream"
            } finally {
                _resolvingVideoStatus.value = null
                _resolvingCardId.value = null
            }
        }
    }

    fun dismissVideoError() {
        _videoResolutionError.value = null
    }

    fun closeVideo() {
        val currentVideo = _activeVideo.value
        val inlineId = lastInlineCardId
        _activeVideo.value = null

        if (inlineId != null && currentVideo != null) {
            _activeInlineVideo.value = ActiveInlineVideoPlayback(
                cardId = inlineId,
                title = currentVideo.title,
                qualities = currentVideo.qualities,
                subtitles = currentVideo.subtitles,
                headers = currentVideo.headers
            )
            lastInlineCardId = null
        } else {
            sharedPlayerManager.stopPlayer()
        }
    }

    fun closeInlineVideo(cardId: String? = null) {
        if (cardId == null || _activeInlineVideo.value?.cardId == cardId) {
            _activeInlineVideo.value = null
            lastInlineCardId = null
            sharedPlayerManager.stopPlayer()
        }
    }

    var enteredPipFromInlineCard: Boolean = false
        private set

    fun enterPipFromInline(cardId: String, currentPositionMs: Long = 0L) {
        enteredPipFromInlineCard = true
        openFullscreenFromInline(cardId, currentPositionMs, startInLandscape = false)
    }

    fun returnToInlineFromPip() {
        if (enteredPipFromInlineCard) {
            enteredPipFromInlineCard = false
            closeVideo()
        }
    }

    fun openFullscreenFromInline(cardId: String, currentPositionMs: Long = 0L, startInLandscape: Boolean = true) {
        val inline = _activeInlineVideo.value ?: return
        if (inline.cardId == cardId) {
            lastInlineCardId = cardId
            _activeInlineVideo.value = null
            _activeVideo.value = ActiveVideoPlayback(
                title = inline.title,
                qualities = inline.qualities,
                subtitles = inline.subtitles,
                headers = inline.headers,
                initialPositionMs = currentPositionMs,
                startInLandscape = startInLandscape
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPlayerManager.release()
    }

    // Active Photoset Lightbox State
    private val _activeLightbox = MutableStateFlow<Pair<List<String>, Int>?>(null)
    val activeLightbox: StateFlow<Pair<List<String>, Int>?> = _activeLightbox.asStateFlow()

    fun openLightbox(images: List<String>, startIndex: Int = 0) {
        _activeLightbox.value = images to startIndex
    }

    fun closeLightbox() {
        _activeLightbox.value = null
    }

    // ==========================================
    // STASHDB STATE & CACHING
    // ==========================================
    private val _stashSearchQuery = MutableStateFlow("")
    val stashSearchQuery: StateFlow<String> = _stashSearchQuery.asStateFlow()

    private val _stashActiveType = MutableStateFlow(StashSearchType.ACTORS)
    val stashActiveType: StateFlow<StashSearchType> = _stashActiveType.asStateFlow()

    private val _stashPerformerResults = MutableStateFlow<List<StashPerformer>>(emptyList())
    val stashPerformerResults: StateFlow<List<StashPerformer>> = _stashPerformerResults.asStateFlow()

    private val _stashStudioResults = MutableStateFlow<List<StashStudio>>(emptyList())
    val stashStudioResults: StateFlow<List<StashStudio>> = _stashStudioResults.asStateFlow()

    private val _stashScenesList = MutableStateFlow<List<StashScene>>(emptyList())
    val stashScenesList: StateFlow<List<StashScene>> = _stashScenesList.asStateFlow()

    private val _stashSelectedPerformer = MutableStateFlow<StashPerformer?>(null)
    val stashSelectedPerformer: StateFlow<StashPerformer?> = _stashSelectedPerformer.asStateFlow()

    private val _stashSelectedStudio = MutableStateFlow<StashStudio?>(null)
    val stashSelectedStudio: StateFlow<StashStudio?> = _stashSelectedStudio.asStateFlow()

    private val _stashSelectedSceneIds = MutableStateFlow<Set<String>>(emptySet())
    val stashSelectedSceneIds: StateFlow<Set<String>> = _stashSelectedSceneIds.asStateFlow()

    private val _stashTotalScenesCount = MutableStateFlow(0)
    val stashTotalScenesCount: StateFlow<Int> = _stashTotalScenesCount.asStateFlow()

    private val _stashCurrentPage = MutableStateFlow(1)
    val stashCurrentPage: StateFlow<Int> = _stashCurrentPage.asStateFlow()

    private val _stashCanLoadMore = MutableStateFlow(false)
    val stashCanLoadMore: StateFlow<Boolean> = _stashCanLoadMore.asStateFlow()

    private val _isStashLoadingEntities = MutableStateFlow(false)
    val isStashLoadingEntities: StateFlow<Boolean> = _isStashLoadingEntities.asStateFlow()

    private val _isStashLoadingScenes = MutableStateFlow(false)
    val isStashLoadingScenes: StateFlow<Boolean> = _isStashLoadingScenes.asStateFlow()

    private val _isStashLoadingMore = MutableStateFlow(false)
    val isStashLoadingMore: StateFlow<Boolean> = _isStashLoadingMore.asStateFlow()

    private val _stashSearchError = MutableStateFlow<String?>(null)
    val stashSearchError: StateFlow<String?> = _stashSearchError.asStateFlow()

    private val _isStashSearchExpanded = MutableStateFlow(false)
    val isStashSearchExpanded: StateFlow<Boolean> = _isStashSearchExpanded.asStateFlow()

    private var stashSearchJob: Job? = null
    private var stashScenesJob: Job? = null

    // Clean Tab State Cache Map
    private val stashTabCacheMap = mutableMapOf<StashSearchType, StashTabCache>()

    fun setStashSearchQuery(query: String) {
        _stashSearchQuery.value = query
    }

    fun setStashSearchExpanded(expanded: Boolean) {
        _isStashSearchExpanded.value = expanded
    }

    fun setStashActiveType(type: StashSearchType, apiKey: String) {
        if (_stashActiveType.value == type) return

        // 1. Cache current state
        val currentType = _stashActiveType.value
        stashTabCacheMap[currentType] = StashTabCache(
            query = _stashSearchQuery.value,
            scenes = _stashScenesList.value,
            selectedPerformer = _stashSelectedPerformer.value,
            selectedStudio = _stashSelectedStudio.value,
            totalCount = _stashTotalScenesCount.value,
            currentPage = _stashCurrentPage.value,
            canLoadMore = _stashCanLoadMore.value
        )

        _stashActiveType.value = type
        _stashSearchError.value = null
        _stashSelectedSceneIds.value = emptySet()

        // 2. Restore cached state
        val cached = stashTabCacheMap[type] ?: StashTabCache()
        _stashSearchQuery.value = cached.query
        _stashScenesList.value = cached.scenes
        _stashTotalScenesCount.value = cached.totalCount
        _stashCurrentPage.value = cached.currentPage
        _stashCanLoadMore.value = cached.canLoadMore
        _stashSelectedPerformer.value = if (type == StashSearchType.STUDIO) null else cached.selectedPerformer
        _stashSelectedStudio.value = if (type == StashSearchType.STUDIO) cached.selectedStudio else null
    }

    fun toggleStashSceneSelection(sceneId: String) {
        val current = _stashSelectedSceneIds.value
        _stashSelectedSceneIds.value = if (current.contains(sceneId)) current - sceneId else current + sceneId
    }

    fun clearStashSelection() {
        _stashSelectedSceneIds.value = emptySet()
    }

    fun resetStashState() {
        stashSearchJob?.cancel()
        stashScenesJob?.cancel()
        stashTabCacheMap.clear()
        _stashSearchQuery.value = ""
        _isStashSearchExpanded.value = false
        _stashPerformerResults.value = emptyList()
        _stashStudioResults.value = emptyList()
        _stashScenesList.value = emptyList()
        _stashSelectedPerformer.value = null
        _stashSelectedStudio.value = null
        _stashSelectedSceneIds.value = emptySet()
        _stashSearchError.value = null
        _stashCurrentPage.value = 1
        _stashCanLoadMore.value = false
        _isStashLoadingEntities.value = false
        _isStashLoadingScenes.value = false
        _isStashLoadingMore.value = false
    }

    private fun scoreSearchMatch(name: String, aliases: List<String>, query: String): Int {
        val q = query.lowercase()
        val n = name.trim().lowercase()
        val a = aliases.map { it.trim().lowercase() }
        return when {
            n == q -> 100
            a.contains(q) -> 90
            n.startsWith(q) -> 80
            a.any { it.startsWith(q) } -> 70
            n.contains(q) -> 60
            a.any { it.contains(q) } -> 50
            else -> 10
        }
    }

    fun performStashSearch(apiKey: String, query: String? = null) {
        val q = (query ?: _stashSearchQuery.value).trim()
        if (q.isBlank()) return

        stashSearchJob?.cancel()
        stashScenesJob?.cancel()
        val searchTargetType = _stashActiveType.value

        stashSearchJob = viewModelScope.launch(Dispatchers.IO) {
            _isStashLoadingEntities.value = true
            _stashSearchError.value = null
            _stashSelectedPerformer.value = null
            _stashSelectedStudio.value = null
            _stashSelectedSceneIds.value = emptySet()
            _stashScenesList.value = emptyList()
            _stashCurrentPage.value = 1
            _stashCanLoadMore.value = false

            when (searchTargetType) {
                StashSearchType.ACTORS -> {
                    val res = StashDbApiService.searchPerformers(q, apiKey)
                    res.onSuccess { rawPerformers ->
                        val sorted = rawPerformers.sortedWith(
                            compareByDescending<StashPerformer> { scoreSearchMatch(it.name, it.aliases, q) }
                                .thenByDescending { if (!it.imageUrl.isNullOrBlank()) 1 else 0 }
                                .thenBy { it.name.lowercase() }
                        )
                        _stashPerformerResults.value = sorted
                        _isStashLoadingEntities.value = false
                        if (sorted.isNotEmpty()) selectStashPerformer(sorted.first(), apiKey)
                    }.onFailure { err ->
                        _stashSearchError.value = err.message ?: "Failed to search actors"
                        _isStashLoadingEntities.value = false
                    }
                }
                StashSearchType.STUDIO -> {
                    val res = StashDbApiService.searchStudios(q, apiKey)
                    res.onSuccess { rawStudios ->
                        val sorted = rawStudios.sortedWith(
                            compareByDescending<StashStudio> { scoreSearchMatch(it.name, emptyList(), q) }
                                .thenByDescending { if (!it.logoUrl.isNullOrBlank()) 1 else 0 }
                                .thenBy { it.name.lowercase() }
                        )
                        _stashStudioResults.value = sorted
                        _isStashLoadingEntities.value = false
                        if (sorted.isNotEmpty()) selectStashStudio(sorted.first(), apiKey)
                    }.onFailure { err ->
                        _stashSearchError.value = err.message ?: "Failed to search studios"
                        _isStashLoadingEntities.value = false
                    }
                }
                StashSearchType.SEXMEX -> {
                    try {
                        val result = com.example.network.SexMexScraper.searchSexMex(q)
                        _stashPerformerResults.value = result.models
                        _stashSelectedPerformer.value = result.models.firstOrNull()
                        _stashScenesList.value = result.scenes
                        _stashTotalScenesCount.value = result.scenes.size
                        _stashCurrentPage.value = 1
                        _stashCanLoadMore.value = false
                        _isStashLoadingEntities.value = false
                        _isStashLoadingScenes.value = false
                    } catch (e: Exception) {
                        _stashSearchError.value = e.message ?: "Failed to search SexMex"
                        _isStashLoadingEntities.value = false
                        _isStashLoadingScenes.value = false
                    }
                }
            }
        }
    }

    fun exploreLatestSexMex() {
        stashSearchJob?.cancel()
        stashScenesJob?.cancel()
        stashSearchJob = viewModelScope.launch(Dispatchers.IO) {
            _isStashLoadingScenes.value = true
            _stashSearchError.value = null
            _stashPerformerResults.value = emptyList()
            _stashSelectedPerformer.value = null
            _stashSelectedSceneIds.value = emptySet()
            _stashScenesList.value = emptyList()
            _stashCurrentPage.value = 1
            _stashCanLoadMore.value = false
            _stashSearchQuery.value = ""
            try {
                val latest = com.example.network.SexMexScraper.getLatestSexMexScenes()
                _stashScenesList.value = latest
                _stashTotalScenesCount.value = latest.size
                stashTabCacheMap[StashSearchType.SEXMEX] = StashTabCache(scenes = latest, totalCount = latest.size)
                _isStashLoadingScenes.value = false
            } catch (e: Exception) {
                _stashSearchError.value = e.message ?: "Failed to fetch latest SexMex scenes"
                _isStashLoadingScenes.value = false
            }
        }
    }

    private val sexmexCoverRefreshTimestamps = ConcurrentHashMap<String, Long>()
    private val sexmexRefreshSemaphore = kotlinx.coroutines.sync.Semaphore(2)

    fun autoRefreshSexMexCoverIfNeeded(link: LinkEntity) {
        val sceneUrl = link.stashDbId?.trim() ?: return
        if (!sceneUrl.contains("sexmex.xxx", ignoreCase = true) && !sceneUrl.contains("sexmex.com", ignoreCase = true)) {
            return
        }

        val now = System.currentTimeMillis()
        val lastAttempt = sexmexCoverRefreshTimestamps[link.id] ?: 0L
        if (now - lastAttempt < 10 * 60 * 1000L) return
        sexmexCoverRefreshTimestamps[link.id] = now

        viewModelScope.launch(Dispatchers.IO) {
            sexmexRefreshSemaphore.acquire()
            try {
                val freshUrl = com.example.network.SexMexScraper.fetchFreshCoverUrl(sceneUrl)
                if (!freshUrl.isNullOrBlank() && freshUrl != link.coverImage) {
                    repository.updateLink(link.copy(coverImage = freshUrl))
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to auto-refresh SexMex cover for ${link.id}", e)
            } finally {
                sexmexRefreshSemaphore.release()
            }
        }
    }

    fun isStudioBlocked(studioId: String?, studioName: String?): Boolean {
        val current = settings.value
        if (!current.enableStudioFilter) return false
        val sName = studioName?.trim()
        val sId = studioId?.trim()
        if (!sId.isNullOrBlank() && current.blockedStudioIds.contains(sId)) return true
        if (!sName.isNullOrBlank() && current.blockedStudioNames.any { it.equals(sName, ignoreCase = true) }) return true
        return false
    }

    fun blockStudio(studioId: String?, studioName: String?) {
        val name = studioName?.trim() ?: return
        if (name.isBlank()) return
        val current = settings.value
        val updatedNames = (current.blockedStudioNames + name).distinct()
        val updatedIds = if (!studioId.isNullOrBlank()) (current.blockedStudioIds + studioId.trim()).distinct() else current.blockedStudioIds
        updateSettings(current.copy(blockedStudioNames = updatedNames, blockedStudioIds = updatedIds))

        _stashScenesList.value = _stashScenesList.value.filterNot { scene ->
            (scene.studioId != null && updatedIds.contains(scene.studioId)) ||
            (scene.studioName != null && updatedNames.any { it.equals(scene.studioName.trim(), ignoreCase = true) })
        }
    }

    fun unblockStudio(studioName: String) {
        val name = studioName.trim()
        val current = settings.value
        val updatedNames = current.blockedStudioNames.filterNot { it.equals(name, ignoreCase = true) }
        val allStudiosList = allStudios.value
        val unblockedIds = allStudiosList.filter { it.name.trim().equals(name, ignoreCase = true) }.map { it.id }.toSet()
        val updatedIds = current.blockedStudioIds.filterNot { it in unblockedIds }
        updateSettings(current.copy(blockedStudioNames = updatedNames, blockedStudioIds = updatedIds))
    }

    fun clearAllBlockedStudios() {
        val current = settings.value
        updateSettings(current.copy(blockedStudioNames = emptyList(), blockedStudioIds = emptyList()))
    }

    fun selectStashPerformer(performer: StashPerformer, apiKey: String) {
        _stashSelectedPerformer.value = performer
        _stashSelectedStudio.value = null
        _stashSelectedSceneIds.value = emptySet()

        if (_stashActiveType.value == StashSearchType.SEXMEX) {
            stashScenesJob?.cancel()
            stashScenesJob = viewModelScope.launch(Dispatchers.IO) {
                _isStashLoadingScenes.value = true
                _stashCurrentPage.value = 1
                _stashScenesList.value = emptyList()
                _stashSearchError.value = null
                try {
                    val scenes = com.example.network.SexMexScraper.scrapeSexMexPage(performer.id)
                    _stashTotalScenesCount.value = scenes.size
                    _stashScenesList.value = scenes
                    _stashCanLoadMore.value = false
                    _isStashLoadingScenes.value = false
                } catch (e: Exception) {
                    _stashSearchError.value = e.message ?: "Failed to load SexMex scenes"
                    _isStashLoadingScenes.value = false
                }
            }
            return
        }

        stashScenesJob?.cancel()
        stashScenesJob = viewModelScope.launch(Dispatchers.IO) {
            _isStashLoadingScenes.value = true
            _stashCurrentPage.value = 1
            _stashScenesList.value = emptyList()
            _stashSearchError.value = null

            val res = StashDbApiService.queryPerformerScenes(
                performerId = performer.id,
                apiKey = apiKey,
                page = 1,
                perPage = 30
            )
            res.onSuccess { queryResult ->
                val currentSettings = settings.value
                val filteredScenes = if (currentSettings.enableStudioFilter) {
                    queryResult.scenes.filterNot { isStudioBlocked(it.studioId, it.studioName) }
                } else queryResult.scenes
                _stashTotalScenesCount.value = queryResult.count
                _stashScenesList.value = filteredScenes
                _stashCanLoadMore.value = queryResult.scenes.isNotEmpty() && (1 * 30 < queryResult.count)
                _isStashLoadingScenes.value = false
            }.onFailure { err ->
                _stashSearchError.value = err.message ?: "Failed to load scenes"
                _isStashLoadingScenes.value = false
            }
        }
    }

    fun selectStashStudio(studio: StashStudio, apiKey: String) {
        _stashSelectedStudio.value = studio
        _stashSelectedPerformer.value = null
        _stashSelectedSceneIds.value = emptySet()
        stashScenesJob?.cancel()
        stashScenesJob = viewModelScope.launch(Dispatchers.IO) {
            _isStashLoadingScenes.value = true
            _stashCurrentPage.value = 1
            _stashScenesList.value = emptyList()
            _stashSearchError.value = null

            val res = StashDbApiService.queryStudioScenes(
                studioId = studio.id,
                apiKey = apiKey,
                page = 1,
                perPage = 30,
                providedChildIds = studio.childIds
            )
            res.onSuccess { queryResult ->
                val currentSettings = settings.value
                val filteredScenes = if (currentSettings.enableStudioFilter) {
                    queryResult.scenes.filterNot { isStudioBlocked(it.studioId, it.studioName) }
                } else queryResult.scenes
                _stashTotalScenesCount.value = queryResult.count
                _stashScenesList.value = filteredScenes
                _stashCanLoadMore.value = queryResult.scenes.isNotEmpty() && (1 * 30 < queryResult.count)
                _isStashLoadingScenes.value = false
            }.onFailure { err ->
                _stashSearchError.value = err.message ?: "Failed to load studio scenes"
                _isStashLoadingScenes.value = false
            }
        }
    }

    private fun appendStashScenes(newScenes: List<StashScene>, count: Int, page: Int) {
        _stashCurrentPage.value = page
        val current = _stashScenesList.value
        val currentSettings = settings.value
        val filtered = if (currentSettings.enableStudioFilter) {
            newScenes.filterNot { isStudioBlocked(it.studioId, it.studioName) }
        } else newScenes
        val existingIds = current.map { it.id }.toSet()
        _stashScenesList.value = current + filtered.filter { it.id !in existingIds }
        _stashCanLoadMore.value = newScenes.isNotEmpty() && (page * 30 < count)
    }

    fun loadMoreStashScenes(apiKey: String) {
        if (_isStashLoadingMore.value || !_stashCanLoadMore.value) return
        val currentType = _stashActiveType.value
        val selectedPerf = _stashSelectedPerformer.value
        val selectedStud = _stashSelectedStudio.value

        if (currentType == StashSearchType.ACTORS && selectedPerf == null) return
        if (currentType == StashSearchType.STUDIO && selectedStud == null) return
        if (currentType == StashSearchType.SEXMEX) return

        val nextPage = _stashCurrentPage.value + 1

        viewModelScope.launch(Dispatchers.IO) {
            _isStashLoadingMore.value = true

            if (currentType == StashSearchType.ACTORS && selectedPerf != null) {
                val res = StashDbApiService.queryPerformerScenes(
                    performerId = selectedPerf.id,
                    apiKey = apiKey,
                    page = nextPage,
                    perPage = 30
                )
                res.onSuccess { queryResult ->
                    appendStashScenes(queryResult.scenes, queryResult.count, nextPage)
                }.onFailure { err ->
                    _stashSearchError.value = err.message ?: "Failed to load more scenes"
                    _stashCanLoadMore.value = false
                }
            } else if (currentType == StashSearchType.STUDIO && selectedStud != null) {
                val res = StashDbApiService.queryStudioScenes(
                    studioId = selectedStud.id,
                    apiKey = apiKey,
                    page = nextPage,
                    perPage = 30,
                    providedChildIds = selectedStud.childIds
                )
                res.onSuccess { queryResult ->
                    appendStashScenes(queryResult.scenes, queryResult.count, nextPage)
                }.onFailure { err ->
                    _stashSearchError.value = err.message ?: "Failed to load more studio scenes"
                    _stashCanLoadMore.value = false
                }
            }
            _isStashLoadingMore.value = false
        }
    }

    // Batch save progress state
    private val _isSavingWithProgress = MutableStateFlow(false)
    val isSavingWithProgress: StateFlow<Boolean> = _isSavingWithProgress.asStateFlow()

    private val _saveProgressCurrent = MutableStateFlow(0)
    val saveProgressCurrent: StateFlow<Int> = _saveProgressCurrent.asStateFlow()

    private val _saveProgressTotal = MutableStateFlow(0)
    val saveProgressTotal: StateFlow<Int> = _saveProgressTotal.asStateFlow()

    private val _saveCurrentTitle = MutableStateFlow("")
    val saveCurrentTitle: StateFlow<String> = _saveCurrentTitle.asStateFlow()

    private val _saveCurrentPhase = MutableStateFlow("")
    val saveCurrentPhase: StateFlow<String> = _saveCurrentPhase.asStateFlow()

    private val _saveSavedWithTorrentsCount = MutableStateFlow(0)
    val saveSavedWithTorrentsCount: StateFlow<Int> = _saveSavedWithTorrentsCount.asStateFlow()

    private var batchSaveJob: Job? = null

    fun cancelBatchSave() {
        batchSaveJob?.cancel()
        _isSavingWithProgress.value = false
    }

    fun saveSelectedStashScenesWithProgress(
        fetchTorrents: Boolean = true,
        onComplete: (savedCount: Int, torrentsCount: Int) -> Unit
    ) {
        batchSaveJob?.cancel()
        batchSaveJob = viewModelScope.launch(Dispatchers.IO) {
            val selectedIds = _stashSelectedSceneIds.value
            if (selectedIds.isEmpty()) return@launch

            val scenesToSave = _stashScenesList.value.filter { selectedIds.contains(it.id) }
            if (scenesToSave.isEmpty()) return@launch

            _isSavingWithProgress.value = true
            _saveProgressCurrent.value = 0
            _saveProgressTotal.value = scenesToSave.size
            _saveSavedWithTorrentsCount.value = 0
            _saveCurrentTitle.value = ""
            _saveCurrentPhase.value = ""

            var totalSaved = 0
            var torrentsCount = 0

            val selectedPerf = _stashSelectedPerformer.value

            // Optimized: Fetch DB entities once before loop
            val allExistingActors = repository.allActors.first().toMutableList()
            val allExistingStudios = repository.allStudios.first().toMutableList()
            val allExistingLinks = repository.allLinks.first().toMutableList()

            val newActorsToInsert = mutableListOf<ActorEntity>()
            val newStudiosToInsert = mutableListOf<StudioEntity>()

            try {
                for ((index, scene) in scenesToSave.withIndex()) {
                    ensureActive()

                    _saveProgressCurrent.value = index + 1
                    _saveCurrentTitle.value = scene.title
                    _saveCurrentPhase.value = if (fetchTorrents) "Fetching torrent..." else "Preparing scene..."

                    // 1. Process female performers
                    val sortedPerformers = if (selectedPerf != null) {
                        val matching = scene.femalePerformers.filter {
                            it.id == selectedPerf.id || it.name.trim().equals(selectedPerf.name.trim(), ignoreCase = true)
                        }
                        val others = scene.femalePerformers.filterNot {
                            it.id == selectedPerf.id || it.name.trim().equals(selectedPerf.name.trim(), ignoreCase = true)
                        }
                        matching + others
                    } else scene.femalePerformers

                    val actorEntitiesForSearch = mutableListOf<ActorEntity>()
                    val actorIds = mutableListOf<String>()

                    for (perf in sortedPerformers) {
                        val pName = perf.name.trim()
                        if (pName.isBlank()) continue

                        val existing = allExistingActors.find {
                            (it.stashDbId != null && it.stashDbId == perf.id) ||
                            it.name.trim().equals(pName, ignoreCase = true)
                        }

                        if (existing != null) {
                            actorIds.add(existing.id)
                            actorEntitiesForSearch.add(existing)
                        } else {
                            val newActorId = UUID.randomUUID().toString()
                            val actor = ActorEntity(
                                id = newActorId,
                                stashDbId = perf.id,
                                name = perf.name,
                                imageUrl = perf.imageUrl ?: "",
                                originalImageUrl = perf.imageUrl
                            )
                            allExistingActors.add(actor)
                            newActorsToInsert.add(actor)
                            actorIds.add(newActorId)
                            actorEntitiesForSearch.add(actor)
                        }
                    }

                    // 2. Process Studio
                    val studioEntitiesForSearch = mutableListOf<StudioEntity>()
                    val studioIds = mutableListOf<String>()

                    if (!scene.studioName.isNullOrBlank()) {
                        val sName = scene.studioName.trim()
                        val existingStudio = allExistingStudios.find {
                            (scene.studioId != null && it.stashDbId == scene.studioId) ||
                            it.name.trim().equals(sName, ignoreCase = true)
                        }

                        if (existingStudio != null) {
                            studioIds.add(existingStudio.id)
                            studioEntitiesForSearch.add(existingStudio)
                        } else {
                            val newStudioId = UUID.randomUUID().toString()
                            val studio = StudioEntity(
                                id = newStudioId,
                                stashDbId = scene.studioId,
                                name = scene.studioName,
                                logoUrl = scene.studioLogo,
                                imageUrl = scene.studioLogo
                            )
                            allExistingStudios.add(studio)
                            newStudiosToInsert.add(studio)
                            studioIds.add(newStudioId)
                            studioEntitiesForSearch.add(studio)
                        }
                    }

                    // 3. Parse date
                    val parsedDate = StashDbApiService.parseDateToMillis(scene.date)

                    // 4. Fetch torrent magnet if requested
                    val torrentResult = if (fetchTorrents) {
                        try {
                            fetchMagnetInternal(
                                title = scene.title,
                                selectedActors = actorEntitiesForSearch,
                                selectedStudios = studioEntitiesForSearch,
                                assignedDate = parsedDate,
                                urlHD = scene.coverUrl ?: "",
                                url4K = ""
                            )
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            null
                        }
                    } else null

                    val hasTorrent = torrentResult != null && torrentResult.hasResult
                    if (hasTorrent) {
                        torrentsCount++
                        _saveSavedWithTorrentsCount.value = torrentsCount
                    }

                    _saveCurrentPhase.value = "Saving..."

                    // 5. Build & insert LinkEntity
                    val existingLink = allExistingLinks.find {
                        (it.stashDbId != null && it.stashDbId == scene.id) ||
                        (it.title.trim().equals(scene.title.trim(), ignoreCase = true) && it.assignedDate == parsedDate)
                    }

                    val linkToSave = if (existingLink != null) {
                        existingLink.copy(
                            stashDbId = scene.id,
                            title = scene.title,
                            coverImage = if (existingLink.coverImage.isBlank()) (scene.coverUrl ?: "") else existingLink.coverImage,
                            actorIds = (actorIds + existingLink.actorIds).distinct(),
                            studioIds = (studioIds + existingLink.studioIds).distinct(),
                            assignedDate = existingLink.assignedDate ?: parsedDate,
                            magnet = torrentResult?.magnet1080p ?: existingLink.magnet,
                            magnet4K = torrentResult?.magnet2160p ?: existingLink.magnet4K,
                            torrentUrlHD = torrentResult?.url1080p ?: existingLink.torrentUrlHD,
                            torrentUrl4K = torrentResult?.url2160p ?: existingLink.torrentUrl4K,
                            torrentSiteName = torrentResult?.sourceSite ?: existingLink.torrentSiteName
                        )
                    } else {
                        LinkEntity(
                            id = UUID.randomUUID().toString(),
                            stashDbId = scene.id,
                            title = scene.title,
                            coverImage = scene.coverUrl ?: "",
                            actorIds = actorIds.distinct(),
                            studioIds = studioIds.distinct(),
                            assignedDate = parsedDate,
                            magnet = torrentResult?.magnet1080p,
                            magnet4K = torrentResult?.magnet2160p,
                            torrentUrlHD = torrentResult?.url1080p,
                            torrentUrl4K = torrentResult?.url2160p,
                            torrentSiteName = torrentResult?.sourceSite
                        )
                    }

                    allExistingLinks.add(linkToSave)
                    repository.insertLink(linkToSave)
                    totalSaved++
                }

                // Batch insert new actors & studios once
                if (newActorsToInsert.isNotEmpty()) repository.insertActors(newActorsToInsert)
                if (newStudiosToInsert.isNotEmpty()) repository.insertStudios(newStudiosToInsert)

                _stashSelectedSceneIds.value = emptySet()
                withContext(Dispatchers.Main) {
                    onComplete(totalSaved, torrentsCount)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("MainViewModel", "Batch save error", e)
            } finally {
                _isSavingWithProgress.value = false
            }
        }
    }

    fun saveSelectedStashScenesWithProgress(
        fetchTorrents: Boolean = true,
        onComplete: (Int) -> Unit
    ) = saveSelectedStashScenesWithProgress(fetchTorrents) { savedCount, _ -> onComplete(savedCount) }

    fun saveSelectedStashScenes(onComplete: (Int) -> Unit) =
        saveSelectedStashScenesWithProgress { savedCount, _ -> onComplete(savedCount) }

    // Search, Tabs, Filter and Sort
    val searchQuery = MutableStateFlow("")
    val sortMode = MutableStateFlow(SortMode.CARD_NEWEST)
    val homeTab = MutableStateFlow(0)
    val bookmarkedIds = MutableStateFlow<Set<String>>(emptySet())
    val lastFeedRefresh = MutableStateFlow(System.currentTimeMillis())
    val viewFilter = MutableStateFlow("ALL")

    fun toggleBookmark(id: String) {
        val curr = bookmarkedIds.value
        bookmarkedIds.value = if (curr.contains(id)) curr - id else curr + id
    }

    fun refreshFeed() {
        lastFeedRefresh.value = System.currentTimeMillis()
    }

    // Data Flows
    val allLinks = repository.allLinks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allActors = repository.allActors.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allStudios = repository.allStudios.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isInitialDataLoaded: StateFlow<Boolean> = repository.allLinks
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val actorSceneCounts: StateFlow<Map<String, Int>> = allLinks
        .map { links ->
            withContext(Dispatchers.Default) {
                links.flatMap { it.actorIds }.groupingBy { it }.eachCount()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val studioSceneCounts: StateFlow<Map<String, Int>> = allLinks
        .map { links ->
            withContext(Dispatchers.Default) {
                links.flatMap { it.studioIds }.groupingBy { it }.eachCount()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _settingsState = MutableStateFlow<SettingsEntity?>(null)
    val settings: StateFlow<SettingsEntity> = repository.settings
        .map { it ?: SettingsEntity() }
        .onEach { dbSettings ->
            if (_settingsState.value == null) {
                _settingsState.value = dbSettings
            }
        }
        .combine(_settingsState) { dbSettings, localOverride ->
            localOverride ?: dbSettings
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsEntity())

    // High performance filtered & sorted scenes
    val filteredLinks: StateFlow<List<LinkEntity>> = combine(
        combine(allLinks, searchQuery, allActors, allStudios) { links, query, actors, studios ->
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                val matchingActorIds = actors.filter { it.name.lowercase().contains(q) }.map { it.id }.toSet()
                val matchingStudioIds = studios.filter { it.name.lowercase().contains(q) }.map { it.id }.toSet()
                links.filter { link ->
                    link.title.lowercase().contains(q) ||
                    link.actorIds.any { it in matchingActorIds } ||
                    link.studioIds.any { it in matchingStudioIds }
                }
            } else links
        },
        sortMode,
        bookmarkedIds
    ) { searchedLinks, sort, bookmarks ->
        withContext(Dispatchers.Default) {
            when (sort) {
                SortMode.CARD_NEWEST, SortMode.NEWEST -> searchedLinks.sortedByDescending { it.assignedDate ?: it.createdAt }
                SortMode.CARD_OLDEST, SortMode.OLDEST -> searchedLinks.sortedBy { it.assignedDate ?: it.createdAt }
                SortMode.RECENTLY_ADDED -> searchedLinks.sortedByDescending { it.createdAt }
                SortMode.OLDEST_ADDED -> searchedLinks.sortedBy { it.createdAt }
                SortMode.TITLE_AZ -> searchedLinks.sortedBy { it.title.lowercase() }
                SortMode.TITLE_ZA -> searchedLinks.sortedByDescending { it.title.lowercase() }
            }
        }
    }.distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // CRUD operations
    fun saveLink(link: LinkEntity) {
        viewModelScope.launch { repository.insertLink(link) }
    }

    fun deleteLink(id: String) {
        viewModelScope.launch { repository.deleteLinkById(id) }
    }

    fun saveActor(actor: ActorEntity) {
        viewModelScope.launch { repository.insertActor(actor) }
    }

    fun deleteActor(id: String) = deleteActorWithCascade(id)

    fun deleteActorWithCascade(actorId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val links = repository.allLinks.first()
                links.forEach { link ->
                    if (link.actorIds.contains(actorId)) {
                        if (link.actorIds.size > 1) {
                            val updatedActors = link.actorIds.filter { it != actorId }
                            repository.updateLink(link.copy(actorIds = updatedActors))
                        } else {
                            repository.deleteLinkById(link.id)
                        }
                    }
                }
                repository.deleteActorById(actorId)
            } catch (_: Exception) {
                repository.deleteActorById(actorId)
            }
        }
    }

    fun saveStudio(studio: StudioEntity) {
        viewModelScope.launch { repository.insertStudio(studio) }
    }

    fun deleteStudio(id: String) = deleteStudioWithCascade(id)

    fun deleteStudioWithCascade(studioId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val links = repository.allLinks.first()
                links.forEach { link ->
                    if (link.studioIds.contains(studioId)) {
                        if (link.studioIds.size > 1) {
                            val updatedStudios = link.studioIds.filter { it != studioId }
                            repository.updateLink(link.copy(studioIds = updatedStudios))
                        } else {
                            repository.deleteLinkById(link.id)
                        }
                    }
                }
                repository.deleteStudioById(studioId)
            } catch (_: Exception) {
                repository.deleteStudioById(studioId)
            }
        }
    }

    fun updateSettings(newSettings: SettingsEntity) {
        _settingsState.value = newSettings
        viewModelScope.launch { repository.updateSettings(newSettings) }
    }

    // Complete JSON Export preserving all essential fields
    suspend fun exportDataJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        val linksArr = JSONArray()
        repository.allLinks.first().forEach { l ->
            val obj = JSONObject()
            obj.put("id", l.id)
            obj.put("stashDbId", l.stashDbId ?: JSONObject.NULL)
            obj.put("title", l.title)
            obj.put("coverImage", l.coverImage)
            obj.put("urlHD", l.urlHD ?: JSONObject.NULL)
            obj.put("url4K", l.url4K ?: JSONObject.NULL)
            obj.put("magnet", l.magnet ?: JSONObject.NULL)
            obj.put("magnet4K", l.magnet4K ?: JSONObject.NULL)
            obj.put("torrentUrlHD", l.torrentUrlHD ?: JSONObject.NULL)
            obj.put("torrentUrl4K", l.torrentUrl4K ?: JSONObject.NULL)
            obj.put("torrentSiteName", l.torrentSiteName ?: JSONObject.NULL)
            obj.put("aspectRatio", l.aspectRatio)
            obj.put("assignedDate", l.assignedDate ?: JSONObject.NULL)
            obj.put("actorIds", JSONArray(l.actorIds))
            obj.put("studioIds", JSONArray(l.studioIds))
            linksArr.put(obj)
        }
        root.put("links", linksArr)
        root.toString(2)
    }

    suspend fun importJsonData(jsonString: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString.trim())
            val linksArr = root.optJSONArray("links") ?: JSONArray()
            val importedList = mutableListOf<LinkEntity>()
            for (i in 0 until linksArr.length()) {
                val obj = linksArr.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val stashDbId = if (obj.isNull("stashDbId")) null else obj.optString("stashDbId")
                val title = obj.optString("title", "Imported Scene")
                val coverImage = obj.optString("coverImage", "")
                val urlHD = if (obj.isNull("urlHD")) null else obj.optString("urlHD")
                val url4K = if (obj.isNull("url4K")) null else obj.optString("url4K")
                val magnet = if (obj.isNull("magnet")) null else obj.optString("magnet")
                val magnet4K = if (obj.isNull("magnet4K")) null else obj.optString("magnet4K")
                val torrentUrlHD = if (obj.isNull("torrentUrlHD")) null else obj.optString("torrentUrlHD")
                val torrentUrl4K = if (obj.isNull("torrentUrl4K")) null else obj.optString("torrentUrl4K")
                val torrentSiteName = if (obj.isNull("torrentSiteName")) null else obj.optString("torrentSiteName")
                val aspectRatio = obj.optString("aspectRatio", "16:9")
                val assignedDate = if (obj.isNull("assignedDate")) null else obj.optLong("assignedDate")

                val actorIds = mutableListOf<String>()
                obj.optJSONArray("actorIds")?.let { arr ->
                    for (j in 0 until arr.length()) actorIds.add(arr.getString(j))
                }

                val studioIds = mutableListOf<String>()
                obj.optJSONArray("studioIds")?.let { arr ->
                    for (j in 0 until arr.length()) studioIds.add(arr.getString(j))
                }

                importedList.add(
                    LinkEntity(
                        id = id,
                        stashDbId = stashDbId,
                        title = title,
                        coverImage = coverImage,
                        urlHD = urlHD,
                        url4K = url4K,
                        magnet = magnet,
                        magnet4K = magnet4K,
                        torrentUrlHD = torrentUrlHD,
                        torrentUrl4K = torrentUrl4K,
                        torrentSiteName = torrentSiteName,
                        aspectRatio = aspectRatio,
                        assignedDate = assignedDate,
                        actorIds = actorIds,
                        studioIds = studioIds
                    )
                )
            }
            if (importedList.isNotEmpty()) {
                repository.insertLinks(importedList)
            }
            Result.success(importedList.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importSampleDataset(onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertActors(com.example.data.util.SampleTestDataset.sampleActors)
            repository.insertStudios(com.example.data.util.SampleTestDataset.sampleStudios)
            repository.insertLinks(com.example.data.util.SampleTestDataset.sampleScenes)
            withContext(Dispatchers.Main) {
                onDone(com.example.data.util.SampleTestDataset.sampleScenes.size)
            }
        }
    }

    fun clearSampleDataset(onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val sceneIds = com.example.data.util.SampleTestDataset.sampleScenes.map { it.id }
            var count = 0
            sceneIds.forEach { id ->
                repository.deleteLinkById(id)
                count++
            }
            withContext(Dispatchers.Main) {
                onDone(count)
            }
        }
    }

    // Torrent Magnet Fetching
    private val _isFetchingMagnet = MutableStateFlow(false)
    val isFetchingMagnet: StateFlow<Boolean> = _isFetchingMagnet.asStateFlow()

    private var fetchMagnetJob: Job? = null

    fun cancelMagnetFetch() {
        fetchMagnetJob?.cancel()
        _isFetchingMagnet.value = false
    }

    suspend fun fetchMagnetInternal(
        title: String,
        selectedActors: List<ActorEntity>,
        selectedStudios: List<StudioEntity>,
        assignedDate: Long?,
        urlHD: String = "",
        url4K: String = ""
    ): com.example.network.torrent.TorrentSearchResult? {
        val cleanTitle = com.example.network.torrent.QueryBuilder.cleanTitle(title)
        val javMatch = com.example.network.torrent.QueryBuilder.extractJavMatch(title)

        val directExtractCandidate = listOf(title, urlHD, url4K)
            .firstOrNull { it.contains("xxxclub.to/torrents/details/", ignoreCase = true) }

        if (directExtractCandidate == null &&
            selectedActors.isEmpty() &&
            selectedStudios.isEmpty() &&
            assignedDate == null &&
            cleanTitle.isBlank() &&
            javMatch == null
        ) return null

        // Step 1: Direct extract shortcut
        if (directExtractCandidate != null) {
            val directUrlMatch = Regex("https?://[^\\s<>\"']*(?:xxxclub\\.to/torrents/details/\\d+[^\\s<>\"']*)")
                .find(directExtractCandidate)?.value ?: directExtractCandidate
            val directRes = com.example.network.torrent.TorrentScraper.directExtract(directUrlMatch, getApplication())
            if (directRes != null && directRes.hasResult) return directRes
        }

        // Step 2: JAV mode
        if (javMatch != null) {
            val sukebeiRes = com.example.network.torrent.TorrentScraper.searchSukebei(javMatch, getApplication())
            return if (sukebeiRes.hasResult) sukebeiRes else null
        }

        // Step 3: XXXClub smart search (western)
        val actorNames = selectedActors.map { it.name }
        val studioNames = selectedStudios.map { it.name }
        val dateStr = com.example.network.torrent.DatePatterns.formatDateStr(assignedDate)
        val datePatterns = com.example.network.torrent.DatePatterns.generatePatterns(
            dateStr = dateStr,
            targetDateMs = assignedDate
        )
        val queries = com.example.network.torrent.QueryBuilder.buildCascadeQueries(
            title = title,
            actors = actorNames,
            studios = studioNames,
            dateStr = dateStr,
            extraSearchText = "$urlHD $url4K".trim()
        )

        var searchRes = com.example.network.torrent.TorrentScraper.executeQueriesLoop(
            queries = queries,
            datePatterns = datePatterns,
            studios = studioNames,
            actors = actorNames,
            context = getApplication()
        )

        // Step 4: StashDB parent/child fallback if nothing found
        if ((searchRes == null || !searchRes.hasResult) && selectedStudios.isNotEmpty()) {
            val currentSettings = repository.getSettingsOnce()
            val stashApiKey = currentSettings.stashDbApiKey
            if (stashApiKey.isNotBlank()) {
                searchRes = com.example.network.torrent.TorrentScraper.tryStashDbStudioFallback(
                    primaryActor = actorNames.firstOrNull(),
                    dateStr = dateStr,
                    selectedStudios = selectedStudios,
                    stashDbApiKey = stashApiKey,
                    datePatterns = datePatterns,
                    actors = actorNames,
                    context = getApplication()
                )
            }
        }

        return if (searchRes != null && searchRes.hasResult) searchRes else null
    }

    fun fetchMagnet(
        title: String,
        selectedActors: List<ActorEntity>,
        selectedStudios: List<StudioEntity>,
        assignedDate: Long?,
        urlHD: String,
        url4K: String,
        onSuccess: (com.example.network.torrent.TorrentSearchResult) -> Unit,
        onNoResult: () -> Unit,
        onError: (String) -> Unit
    ) {
        fetchMagnetJob?.cancel()

        val cleanTitle = com.example.network.torrent.QueryBuilder.cleanTitle(title)
        val javMatch = com.example.network.torrent.QueryBuilder.extractJavMatch(title)
        val directExtractCandidate = listOf(title, urlHD, url4K)
            .firstOrNull { it.contains("xxxclub.to/torrents/details/", ignoreCase = true) }

        if (directExtractCandidate == null &&
            selectedActors.isEmpty() &&
            selectedStudios.isEmpty() &&
            assignedDate == null &&
            cleanTitle.isBlank() &&
            javMatch == null
        ) {
            onError("Please select a studio, actor, date, or enter a title to search")
            return
        }

        fetchMagnetJob = viewModelScope.launch(Dispatchers.IO) {
            _isFetchingMagnet.value = true
            try {
                val res = fetchMagnetInternal(
                    title = title,
                    selectedActors = selectedActors,
                    selectedStudios = selectedStudios,
                    assignedDate = assignedDate,
                    urlHD = urlHD,
                    url4K = url4K
                )
                withContext(Dispatchers.Main) {
                    if (res != null && res.hasResult) {
                        onSuccess(res)
                    } else {
                        onNoResult()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) {
                    onError("Torrent search failed: ${e.message}")
                }
            } finally {
                _isFetchingMagnet.value = false
            }
        }
    }
}
