
package com.wangjianshuo.voicedrop
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import kotlinx.coroutines.launch

class CommunityStore(
    private val httpClient: HttpClient,
    private val auth: AuthStore,
) {
    var posts by mutableStateOf<List<CommunityPost>>(emptyList())
    var isRefreshing by mutableStateOf(false)
    var likedShareIds by mutableStateOf<Set<String>>(emptySet())

    suspend fun refresh() {
        isRefreshing = true
        try {
            val resp: CommunityListResp = httpClient.get("${API.FILES_BASE}/community/list")
            posts = resp.posts
        } catch (e: Exception) { Log.w("Community", "ignored", e) }

        try {
            val feed: Map<String, List<String>> = httpClient.post("${API.AGENT_BASE}/feed/state")
            likedShareIds = (feed["liked"] ?: emptyList()).toSet()
        } catch (e: Exception) { Log.w("Community", "ignored", e) }
        isRefreshing = false
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

@Composable
fun CommunityList(navController: NavController) {
    val httpClient = LocalHttpClient.current
    val auth = LocalAuthStore.current
    val store = remember { CommunityStore(httpClient, auth) }
    val library = LocalLibraryStore.current

    LaunchedEffect(Unit) { store.refresh() }
    LaunchedEffect(library.selectedTab) {
        if (library.selectedTab == LibraryStore.Tab.COMMUNITY) store.refresh()
    }

    if (store.isRefreshing && store.posts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = VDTheme.Primary)
        }
        return
    }

    if (store.posts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(com.wangjianshuo.voicedrop.R.string.community_empty), style = VDTheme.Caption)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
            items(store.posts) { post ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.CommunityPost.createRoute(post.shareId)) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VDTheme.CardBg),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(post.author, style = VDTheme.Caption.copy(fontWeight = FontWeight.Medium))
                    Spacer(Modifier.height(4.dp))
                    if (post.title != null) {
                        Text(
                            post.title,
                            style = VDTheme.Body,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (val ts = post.firstSharedAt) {
                                is Number -> Formatting.formatRelativeTime(ts.toLong())
                                else -> ""
                            },
                            style = VDTheme.Caption.copy(fontSize = 12.sp),
                        )
                        Spacer(Modifier.weight(1f))
                        val liked = store.likedShareIds.contains(post.shareId)
                        IconButton(
                            onClick = {
                                kotlinx.coroutines.MainScope().launch {
                                    store.engage(post.shareId, if (liked) "unlike" else "like")
                                    store.likedShareIds = if (liked)
                                        store.likedShareIds - post.shareId
                                    else
                                        store.likedShareIds + post.shareId
                                }
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                stringResource(com.wangjianshuo.voicedrop.R.string.like),
                                tint = if (liked) VDTheme.Red else VDTheme.TextHint,
                                modifier = Modifier.size(18.dp),
                            )
        }
    }
}
            }
        }
    }
}

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

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = VDTheme.Primary)
        }
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(com.wangjianshuo.voicedrop.R.string.load_error) + ": $error")
        }
        post != null -> {
            val p = post
            if (p != null) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(p.title ?: stringResource(com.wangjianshuo.voicedrop.R.string.no_title), style = VDTheme.H1)
                Spacer(Modifier.height(4.dp))
                Text(p.author, style = VDTheme.Caption)
                Spacer(Modifier.height(16.dp))
                p.articles?.firstOrNull()?.let { article ->
                    ArticleBodyView(body = article.body, ownerScope = auth.scope)
                }
            }
            }
        }
    }
}
