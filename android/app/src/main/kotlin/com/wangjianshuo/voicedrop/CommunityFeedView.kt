package com.wangjianshuo.voicedrop

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.max
import kotlin.math.min

private enum class FeedTab { RECO, LATEST, REPLIES }

private data class CardItem(
    val post: CommunityPost,
    val width: Float,
)

@Composable
fun CommunityFeedView(
    store: CommunityStore,
    navController: NavController,
) {
    var tab by remember { mutableStateOf(FeedTab.RECO) }
    var containerWidth by remember { mutableFloatStateOf(0f) }

    val posts = remember(store.posts, store.timeOrdered, tab) {
        when (tab) {
            FeedTab.RECO -> store.posts
            FeedTab.LATEST -> store.timeOrdered
            FeedTab.REPLIES -> store.posts.filter { it.replyTo != null }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF3EFE7))) {
        TabRow(tab) { tab = it }

        val density = LocalDensity.current
        val spacing = with(density) { 9.dp.toPx() }
        val padding = with(density) { 24.dp.toPx() }
        val colWidth = if (containerWidth > 0) (containerWidth - spacing) / 2 else 0f

        val (leftCards, rightCards) = remember(posts, colWidth) {
            split(posts, colWidth, spacing)
        }

        if (colWidth > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerWidth = it.width.toFloat() }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 0.dp),
            ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val colDp = with(density) { colWidth.toDp() }
            Column(modifier = Modifier.width(colDp)) {
                leftCards.forEach { (post, _) ->
                    FeedCard(post = post, store = store, widthDp = colDp) {
                        navController.navigate(Screen.CommunityPost.createRoute(post.shareId))
                    }
                    Spacer(Modifier.height(9.dp))
                }
            }
            Column(modifier = Modifier.width(colDp)) {
                rightCards.forEach { (post, _) ->
                    FeedCard(post = post, store = store, widthDp = colDp) {
                        navController.navigate(Screen.CommunityPost.createRoute(post.shareId))
                    }
                    Spacer(Modifier.height(9.dp))
                }
            }
        }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().onSizeChanged { containerWidth = it.width.toFloat() })
        }
    }
}

private fun split(
    posts: List<CommunityPost>,
    colWidth: Float,
    spacing: Float,
): Pair<List<CardItem>, List<CardItem>> {
    val left = mutableListOf<CardItem>()
    val right = mutableListOf<CardItem>()
    var hLeft = 0f
    var hRight = 0f
    for (p in posts) {
        val h = estimatedHeight(p, colWidth) + spacing
        if (hLeft <= hRight) {
            left.add(CardItem(p, colWidth))
            hLeft += h
        } else {
            right.add(CardItem(p, colWidth))
            hRight += h
        }
    }
    return left to right
}

private fun estimatedHeight(post: CommunityPost, width: Float): Float {
    val title = post.title ?: ""
    val titleLen = title.length.coerceAtMost(40)
    if (post.coverPhotoKey != null) {
        val titleLines = min(2, max(1, (titleLen * 15f / max(width - 22f, 1f)).toInt()))
        val replyH = if (post.replyTo != null) 28f else 0f
        return width + titleLines * 21f + 20f + 30f + replyH
    }
    val titleLines = min(3, max(1, (titleLen * 16f / max(width - 26f, 1f)).toInt()))
    val previewLines = if (post.preview.isNullOrEmpty()) 0
        else min(2, max(1, ((post.preview.length.coerceAtMost(60)) * 12.5f / max(width - 26f, 1f)).toInt()))
    val replyH = if (post.replyTo != null) 30f else 0f
    return titleLines * 24f + previewLines * 20f + replyH + 20f + 27f + if (previewLines > 0) 8f else 0f
}

@Composable
private fun TabRow(current: FeedTab, onSelect: (FeedTab) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 18.dp).padding(top = 2.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        listOf(FeedTab.RECO to "推荐", FeedTab.LATEST to "最新", FeedTab.REPLIES to "回应").forEach { (tab, label) ->
            val active = tab == current
            TextButton(onClick = { onSelect(tab) }, contentPadding = PaddingValues(0.dp)) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) VDTheme.TextPrimary else VDTheme.TextHint,
                )
            }
        }
    }
}

@Composable
private fun FeedCard(
    post: CommunityPost,
    store: CommunityStore,
    widthDp: Dp,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(widthDp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        if (post.coverPhotoKey != null) {
            PhotoCard(post, store, widthDp)
        } else {
            TextCard(post, store)
        }
        FeedMetaRow(post, store)
    }
}

@Composable
private fun PhotoCard(post: CommunityPost, store: CommunityStore, widthDp: Dp) {
    val photoUrl = post.coverPhotoKey?.let { "https://${API.HOST}/files/api/photo/$it" }
    Column {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(photoUrl)
                .crossfade(true)
                .placeholder(ColorDrawable(Color(0xFFF0EDE6).toArgb()))
                .build(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(modifier = Modifier.padding(10.dp, 8.dp, 10.dp, 0.dp)) {
            if (post.replyTo != null) ReplyBadge()
            Spacer(Modifier.height(if (post.replyTo != null) 9.dp else 0.dp))
            Text(
                post.title ?: stringResource(R.string.no_title),
                fontSize = 14.5f.sp,
                color = VDTheme.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TextCard(post: CommunityPost, store: CommunityStore) {
    val gradient = rememberGradient(post.shareId)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = gradient)
            .padding(15.dp, 12.dp, 15.dp, 0.dp),
    ) {
        if (post.replyTo != null) ReplyBadge()
        Spacer(Modifier.height(if (post.replyTo != null) 9.dp else 0.dp))
            Text(
                post.title ?: stringResource(R.string.no_title),
                fontSize = 16.sp,
                color = VDTheme.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp,
            )
        if (!post.preview.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                post.preview ?: "",
                fontSize = 12.5f.sp,
                color = Color(0xFF8A7B63),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReplyBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(VDTheme.Accent.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Default.Reply, null,
            tint = VDTheme.Accent,
            modifier = Modifier.size(9.dp),
        )
        Text("回应", fontSize = 11.sp, color = VDTheme.Accent)
    }
}

@Composable
private fun FeedMetaRow(post: CommunityPost, store: CommunityStore) {
    val author = post.author.ifEmpty { "匿名" }
    val avatarColor = rememberAvatarColor(author)
    val likeCount = store.likeCounts[post.shareId] ?: 0
    val replyCount = store.replyCounts[post.shareId] ?: 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                author.take(1),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Text(
            author,
            fontSize = 12.sp,
            color = VDTheme.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(Icons.Default.Favorite, null, tint = VDTheme.Accent, modifier = Modifier.size(10.dp))
            Text("$likeCount", fontSize = 12.sp, color = VDTheme.Accent)
        }
        if (replyCount > 0) {
            Icon(Icons.Default.Reply, null, tint = VDTheme.TextSecondary, modifier = Modifier.size(10.dp))
            Text("$replyCount", fontSize = 12.sp, color = VDTheme.TextSecondary)
        }
    }
}

private val cardPalettes = listOf(
    Color(0xFFFBEFE0) to Color(0xFFF6E3CE),
    Color(0xFFEDE7DC) to Color(0xFFE2DACB),
    Color(0xFFE7EDE3) to Color(0xFFD6E0CE),
)

@Composable
private fun rememberGradient(shareId: String): Brush {
    val idx = remember(shareId) { kotlin.math.abs(shareId.hashCode()) % cardPalettes.size }
    val (top, bottom) = cardPalettes[idx]
    return Brush.linearGradient(listOf(top, bottom))
}

private val avatarColors = listOf(
    Color(0xFFD8A25B), Color(0xFF8A9A88), Color(0xFFB5794C),
    Color(0xFF7A6E9A), Color(0xFF5E8A6A), Color(0xFFC98A2E),
)

@Composable
private fun rememberAvatarColor(name: String): Color {
    val idx = remember(name) { kotlin.math.abs(name.hashCode()) % avatarColors.size }
    return avatarColors[idx]
}
