package com.wangjianshuo.voicedrop

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class FeedTab { RECO, LATEST, REPLIES }
private data class CardItem(val post: CommunityPost, val width: Float)

@Composable
fun CommunityFeedView(store: CommunityStore, navController: NavController) {
    var tab by remember { mutableStateOf(FeedTab.RECO) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    var isRefreshing by remember { mutableStateOf(false) }
    var pullOffsetPx by remember { mutableFloatStateOf(0f) }
    val pullThresholdPx = with(density) { 80.dp.toPx() }
    val pullOffset = with(density) { pullOffsetPx.toDp() }

    fun triggerRefresh() { if (!isRefreshing) isRefreshing = true }

    // NestedScrollConnection: intercept over-scroll at top for pull-to-refresh
    val nestedConn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0f && atTop && !isRefreshing) {
                    pullOffsetPx = (pullOffsetPx + available.y).coerceIn(0f, pullThresholdPx * 1.5f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0f && atTop && !isRefreshing) {
                    pullOffsetPx = (pullOffsetPx + available.y).coerceIn(0f, pullThresholdPx * 1.5f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }

    // Detect release via debounce
    LaunchedEffect(pullOffsetPx, isRefreshing) {
        if (!isRefreshing && pullOffsetPx > 0f) {
            delay(180)
            if (pullOffsetPx >= pullThresholdPx) triggerRefresh()
            else pullOffsetPx = 0f
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            store.refresh()
            isRefreshing = false
            pullOffsetPx = 0f
        }
    }
    LaunchedEffect(tab) { if (store.posts.isEmpty()) store.refresh() }

    val posts = when (tab) {
        FeedTab.RECO -> store.posts
        FeedTab.LATEST -> store.timeOrdered
        FeedTab.REPLIES -> store.posts.filter { it.replyTo != null }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF3EFE7))) {
        TabRow(tab) { tab = it }

        Box(modifier = Modifier.fillMaxSize()) {
            // Pull indicator
            if (pullOffset > 0.dp || isRefreshing) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
                    if (isRefreshing) {
                        CircularProgressIndicator(color = VDTheme.Accent, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    } else {
                        CircularProgressIndicator(
                            progress = { (pullOffsetPx / pullThresholdPx).coerceIn(0f, 1f) },
                            color = VDTheme.Accent, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    }
                }
            }

            if (posts.isEmpty() && !isRefreshing && pullOffsetPx == 0f) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.community_empty), style = VDTheme.Caption)
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val colWidthDp = (maxWidth - 9.dp) / 2
                    val (leftCards, rightCards) = remember(posts, colWidthDp) { split(posts, colWidthDp) }

                    // nestedScroll ON LazyColumn (not on parent Box)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedConn)
                            .offset { IntOffset(0, pullOffsetPx.roundToInt()) }
                            .padding(horizontal = 12.dp),
                    ) {
                            items(leftCards.size.coerceAtLeast(rightCards.size)) { i ->
                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                                    if (i < leftCards.size) {
                                        FeedCard(post = leftCards[i].post, store = store, widthDp = colWidthDp) {
                                            navController.navigate(Screen.CommunityPost.createRoute(leftCards[i].post.shareId))
                                        }
                                    } else { Spacer(Modifier.width(colWidthDp)) }
                                    if (i < rightCards.size) {
                                        FeedCard(post = rightCards[i].post, store = store, widthDp = colWidthDp) {
                                            navController.navigate(Screen.CommunityPost.createRoute(rightCards[i].post.shareId))
                                        }
                                    } else { Spacer(Modifier.width(colWidthDp)) }
                                }
                                Spacer(Modifier.height(9.dp))
                            }
                        }
                    }
                }
            }
        }
    }

private fun split(posts: List<CommunityPost>, colWidth: Dp): Pair<List<CardItem>, List<CardItem>> {
    val left = mutableListOf<CardItem>()
    val right = mutableListOf<CardItem>()
    var hLeft = 0f; var hRight = 0f
    for (p in posts) {
        val h = estimatedHeight(p, colWidth) + 9f
        if (hLeft <= hRight) { left.add(CardItem(p, colWidth.value)); hLeft += h }
        else { right.add(CardItem(p, colWidth.value)); hRight += h }
    }
    return left to right
}

private fun estimatedHeight(post: CommunityPost, width: Dp): Float {
    val title = post.title ?: ""
    val w = width.value
    if (post.coverPhotoKey != null) {
        val tl = min(2, max(1, (title.length.coerceAtMost(40) * 15f / max(w - 22f, 1f)).toInt()))
        return w + tl * 21f + 20f + 30f + if (post.replyTo != null) 28f else 0f
    }
    val tl = min(3, max(1, (title.length.coerceAtMost(40) * 16f / max(w - 26f, 1f)).toInt()))
    val pl = if (post.preview.isNullOrEmpty()) 0 else min(2, max(1, ((post.preview.length.coerceAtMost(60)) * 12.5f / max(w - 26f, 1f)).toInt()))
    return tl * 24f + pl * 20f + (if (post.replyTo != null) 30f else 0f) + 20f + 27f + if (pl > 0) 8f else 0f
}

@Composable
private fun TabRow(current: FeedTab, onSelect: (FeedTab) -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 18.dp).padding(top = 2.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        listOf(FeedTab.RECO to "推荐", FeedTab.LATEST to "最新", FeedTab.REPLIES to "回应").forEach { (tab, label) ->
            val active = tab == current
            TextButton(onClick = { onSelect(tab) }, contentPadding = PaddingValues(0.dp)) {
                Text(label, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) VDTheme.TextPrimary else VDTheme.TextHint)
            }
        }
    }
}

@Composable
private fun FeedCard(post: CommunityPost, store: CommunityStore, widthDp: Dp, onClick: () -> Unit) {
    Card(modifier = Modifier.width(widthDp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        if (post.coverPhotoKey != null) PhotoCard(post, store, widthDp) else TextCard(post, store)
        FeedMetaRow(post, store)
    }
}

@Composable
private fun PhotoCard(post: CommunityPost, store: CommunityStore, widthDp: Dp) {
    val photoUrl = post.coverPhotoKey?.let { "https://${API.HOST}/files/api/photo/$it" }
    Column {
        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(photoUrl)
            .crossfade(true).placeholder(ColorDrawable(Color(0xFFF0EDE6).toArgb())).build(),
            contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
        Column(modifier = Modifier.padding(10.dp, 8.dp, 10.dp, 0.dp)) {
            if (post.replyTo != null) ReplyBadge()
            Spacer(Modifier.height(if (post.replyTo != null) 9.dp else 0.dp))
            Text(post.title ?: stringResource(R.string.no_title), fontSize = 14.5f.sp,
                color = VDTheme.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TextCard(post: CommunityPost, store: CommunityStore) {
    val gradient = rememberGradient(post.shareId)
    Column(modifier = Modifier.fillMaxWidth().background(brush = gradient).padding(15.dp, 12.dp, 15.dp, 0.dp)) {
        if (post.replyTo != null) ReplyBadge()
        Spacer(Modifier.height(if (post.replyTo != null) 9.dp else 0.dp))
        Text(post.title ?: stringResource(R.string.no_title), fontSize = 16.sp, color = VDTheme.TextPrimary,
            maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
        if (!post.preview.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(post.preview ?: "", fontSize = 12.5f.sp, color = Color(0xFF8A7B63), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReplyBadge() {
    Row(modifier = Modifier.clip(RoundedCornerShape(50)).background(VDTheme.Accent.copy(alpha = 0.1f))
        .padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Default.Reply, null, tint = VDTheme.Accent, modifier = Modifier.size(9.dp))
        Text("回应", fontSize = 11.sp, color = VDTheme.Accent)
    }
}

@Composable
private fun FeedMetaRow(post: CommunityPost, store: CommunityStore) {
    val author = post.author.ifEmpty { "匿名" }
    val likeCount = store.likeCounts[post.shareId] ?: 0
    val replyCount = store.replyCounts[post.shareId] ?: 0
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(rememberAvatarColor(author)),
            contentAlignment = Alignment.Center) {
            Text(author.take(1), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        Text(author, fontSize = 12.sp, color = VDTheme.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(Icons.Default.Favorite, null, tint = VDTheme.Accent, modifier = Modifier.size(10.dp))
            Text("$likeCount", fontSize = 12.sp, color = VDTheme.Accent) }
        if (replyCount > 0) {
            Icon(Icons.Default.Reply, null, tint = VDTheme.TextSecondary, modifier = Modifier.size(10.dp))
            Text("$replyCount", fontSize = 12.sp, color = VDTheme.TextSecondary) }
    }
}

private val cardPalettes = listOf(Color(0xFFFBEFE0) to Color(0xFFF6E3CE), Color(0xFFEDE7DC) to Color(0xFFE2DACB), Color(0xFFE7EDE3) to Color(0xFFD6E0CE))
@Composable
private fun rememberGradient(shareId: String): Brush {
    val idx = remember(shareId) { kotlin.math.abs(shareId.hashCode()) % cardPalettes.size }
    return Brush.linearGradient(listOf(cardPalettes[idx].first, cardPalettes[idx].second))
}
private val avatarColors = listOf(Color(0xFFD8A25B), Color(0xFF8A9A88), Color(0xFFB5794C), Color(0xFF7A6E9A), Color(0xFF5E8A6A), Color(0xFFC98A2E))
@Composable
private fun rememberAvatarColor(name: String): Color {
    return avatarColors[kotlin.math.abs(name.hashCode()) % avatarColors.size]
}
