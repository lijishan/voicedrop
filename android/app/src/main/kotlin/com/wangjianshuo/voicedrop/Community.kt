package com.wangjianshuo.voicedrop

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import kotlinx.coroutines.launch

class CommunityStore(
    private val httpClient: HttpClient,
    private val auth: AuthStore,
) {
    var posts by mutableStateOf<List<CommunityPost>>(emptyList())
    var timeOrdered by mutableStateOf<List<CommunityPost>>(emptyList())
    var isRefreshing by mutableStateOf(false)
    var likedShareIds by mutableStateOf<Set<String>>(emptySet())
    var likeCounts by mutableStateOf<Map<String, Int>>(emptyMap())
    var replyCounts by mutableStateOf<Map<String, Int>>(emptyMap())

    suspend fun refresh() {
        isRefreshing = true
        try {
            loadViaFeed()
            Log.d("Community", "feed loaded: ${posts.size} posts")
        } catch (e: Exception) {
            Log.w("Community", "feed failed, fallback: ${e.message}")
            try { loadViaListAndRank(); Log.d("Community", "list loaded: ${posts.size} posts") } catch (e2: Exception) { Log.w("Community", "list failed", e2) }
        }
        isRefreshing = false
    }

    private suspend fun loadViaFeed() {
        val resp: CommunityFeedResp = httpClient.get("${API.RECO_BASE}/feed")
        posts = resp.posts
        timeOrdered = resp.posts
        likeCounts = resp.likes ?: emptyMap()
        replyCounts = resp.replies ?: emptyMap()
        likedShareIds = (resp.mineLikes ?: emptyList()).toSet()
    }

    private suspend fun loadViaListAndRank() {
        val resp: CommunityListResp = httpClient.get("${API.FILES_BASE}/community/list")
        posts = resp.posts
        timeOrdered = resp.posts
        try {
            val ranked = rank()
            if (ranked.isNotEmpty()) {
                val orderMap = ranked.withIndex().associate { it.value to it.index }
                posts = posts.sortedBy { orderMap[it.shareId] ?: Int.MAX_VALUE }
            }
            val feed: Map<String, List<String>> = httpClient.post("${API.AGENT_BASE}/feed/state")
            likedShareIds = (feed["liked"] ?: emptyList()).toSet()
        } catch (e: Exception) { Log.w("Community", "rank/feed failed", e) }
    }

    suspend fun share(articleKey: String): CommunityPost {
        return httpClient.post("${API.FILES_BASE}/community/share/$articleKey")
    }

    suspend fun unshare(shareId: String) {
        httpClient.post<Unit>("${API.FILES_BASE}/community/unshare/$shareId")
    }

    suspend fun get(shareId: String): CommunityFullPost {
        return httpClient.get("${API.FILES_BASE}/community/get/$shareId")
    }

    suspend fun report(shareId: String) {
        httpClient.post<Unit>("${API.FILES_BASE}/community/report/$shareId")
    }

    suspend fun engage(shareId: String, action: String) {
        httpClient.post<Unit>("${API.RECO_BASE}/engage/$shareId", mapOf("action" to action))
    }

    suspend fun rank(): List<String> {
        return try {
            val result: Map<String, List<String>> = httpClient.post("${API.RECO_BASE}/rank")
            result["order"] ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPostView(shareId: String, navController: NavController) {
    val httpClient = LocalHttpClient.current
    val auth = LocalAuthStore.current
    val store = remember { CommunityStore(httpClient, auth) }
    var post by remember { mutableStateOf<CommunityFullPost?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(shareId) {
        try {
            post = store.get(shareId)
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.wangjianshuo.voicedrop.R.string.back))
                    }
                }
            )
        },
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VDTheme.Primary)
            }
            error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(com.wangjianshuo.voicedrop.R.string.load_error) + ": $error")
            }
            post != null -> {
                val p = post ?: return@Scaffold
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text(p.title ?: stringResource(com.wangjianshuo.voicedrop.R.string.no_title), style = VDTheme.H1)
                    Spacer(Modifier.height(4.dp))
                    Text("${p.author} · ${Formatting.formatRelativeTime(0)}", style = VDTheme.Caption)
                    Spacer(Modifier.height(16.dp))
                    p.articles?.firstOrNull()?.let { article ->
                        ArticleBodyView(body = article.body, ownerScope = auth.scope)
                    }
                }
            }
        }
    }
}
