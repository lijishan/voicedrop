package com.wangjianshuo.voicedrop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.navigation.NavController

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryView(navController: NavController) {
    val library = LocalLibraryStore.current

    LaunchedEffect(Unit) { library.smartRefresh() }
    LaunchedEffect(library.selectedTab) { library.smartRefresh() }

    Box(modifier = Modifier.fillMaxSize().background(VDTheme.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Column(modifier = Modifier.statusBarsPadding().offset(y = (-8).dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 0.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.GraphicEq, null, tint = VDTheme.Primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("VoiceDrop 口述", style = VDTheme.Title1.copy(color = VDTheme.TextPrimary), modifier = Modifier.weight(1f))
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, null, tint = VDTheme.TextHint, modifier = Modifier.size(20.dp))
                    }
                }
                TabBar()
            }

            // Content with swipe
            val pagerState = rememberPagerState(pageCount = { 2 })
            LaunchedEffect(library.selectedTab) {
                val target = if (library.selectedTab == LibraryStore.Tab.RECORDINGS) 0 else 1
                if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
            }
            LaunchedEffect(pagerState.currentPage) {
                val tab = if (pagerState.currentPage == 0) LibraryStore.Tab.RECORDINGS else LibraryStore.Tab.COMMUNITY
                if (library.selectedTab != tab) library.selectedTab = tab
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> RecordingList(navController)
                    1 -> CommunityList(navController)
                }
            }
        }

        // Floating record button (transparent background)
        RecordButton(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            onPress = { library.showRecordSheet = true }
        )
    }

    if (library.showRecordSheet) {
        RecordSession(onDismiss = { library.showRecordSheet = false })
    }
}

@Composable
private fun TabBar() {
    val library = LocalLibraryStore.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        listOf(stringResource(com.wangjianshuo.voicedrop.R.string.tab_recordings) to LibraryStore.Tab.RECORDINGS, stringResource(com.wangjianshuo.voicedrop.R.string.tab_community) to LibraryStore.Tab.COMMUNITY).forEach { (label, tab) ->
            val active = library.selectedTab == tab
            Column(
                modifier = Modifier.clickable { library.selectedTab = tab }.width(IntrinsicSize.Max),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = if (active) VDTheme.Primary else VDTheme.TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(6.dp))
                if (active) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(VDTheme.Primary, RoundedCornerShape(2.dp))
                    )
                } else {
                    Spacer(Modifier.height(3.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingList(navController: NavController) {
    val library = LocalLibraryStore.current
    val listState = rememberLazyListState()
    var pullOffset by remember { mutableFloatStateOf(0f) }
    var isPulling by remember { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(targetValue = pullOffset, animationSpec = spring(dampingRatio = 0.5f), label = "pull")

    LaunchedEffect(isPulling) {
        if (isPulling) { library.refresh(); pullOffset = 0f; isPulling = false }
    }

    Box(Modifier.fillMaxSize()
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { pullOffset = 0f },
                onVerticalDrag = { _, amount ->
                    if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                        pullOffset = (pullOffset + amount).coerceIn(0f, 200f)
                    }
                },
                onDragEnd = {
                    if (pullOffset > 100f) { isPulling = true } else { pullOffset = 0f }
                },
                onDragCancel = { pullOffset = 0f },
            )
        }
        .graphicsLayer { translationY = animatedOffset }
    ) {
        if (library.recordings.isEmpty() && !library.isRefreshing) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(com.wangjianshuo.voicedrop.R.string.no_recordings), style = VDTheme.H2)
                Spacer(Modifier.height(8.dp))
                Text("下拉刷新", style = VDTheme.Caption)
            }
            return@Box
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(library.recordings, key = { it.audioName }) { recording ->
                RecordingCard(
                    recording = recording,
                    onClick = { navController.navigate(Screen.RecordingDetail.createRoute(recording.stem)) },
                    onDelete = { library.deleteRecording(recording.audioName) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingCard(recording: Recording, onClick: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val icon = when {
        recording.hasArticles -> Icons.Default.VoiceChat
        recording.isEmpty -> Icons.Default.MicOff
        else -> Icons.Default.Mic
    }

    Card(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showDeleteConfirm = true })
            .border(0.5.dp, VDTheme.Divider, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = VDTheme.CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(VDTheme.Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = VDTheme.Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(recording.rowTitle, style = VDTheme.Body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(recording.smartDate, style = VDTheme.Caption)
                    Spacer(Modifier.width(4.dp))
                    if (recording.durationLabel.isNotEmpty()) {
                        Text(recording.durationLabel, style = VDTheme.Caption)
                    }
                    val (badgeText, badgeColor) = badgeInfo(recording)
                    if (badgeText.isNotEmpty()) {
                        Text(" · $badgeText", style = VDTheme.Caption.copy(fontSize = 12.sp, color = badgeColor, fontWeight = FontWeight.Medium))
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = VDTheme.Divider, modifier = Modifier.size(16.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(com.wangjianshuo.voicedrop.R.string.delete_recording)) },
            text = { Text(stringResource(com.wangjianshuo.voicedrop.R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text(stringResource(com.wangjianshuo.voicedrop.R.string.delete), color = VDTheme.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(com.wangjianshuo.voicedrop.R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun badgeInfo(recording: Recording): Pair<String, Color> {
    return when {
        recording.blockReason == BlockReason.noCredit -> stringResource(com.wangjianshuo.voicedrop.R.string.status_no_credit) to VDTheme.Red
        recording.blockReason == BlockReason.tooLong -> stringResource(com.wangjianshuo.voicedrop.R.string.status_too_long) to VDTheme.Red
        recording.phase == MiningPhase.asr -> stringResource(com.wangjianshuo.voicedrop.R.string.status_asr) to VDTheme.Primary
        recording.phase == MiningPhase.mining -> stringResource(com.wangjianshuo.voicedrop.R.string.status_mining) to VDTheme.Primary
        recording.hasArticles -> stringResource(com.wangjianshuo.voicedrop.R.string.status_done) to VDTheme.Accent
        recording.isEmpty -> stringResource(com.wangjianshuo.voicedrop.R.string.status_empty) to VDTheme.TextSecondary
        !recording.uploaded -> stringResource(com.wangjianshuo.voicedrop.R.string.status_uploading) to VDTheme.TextSecondary
        else -> stringResource(com.wangjianshuo.voicedrop.R.string.status_pending) to VDTheme.TextSecondary
    }
}

@Composable
private fun RecordButton(
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onPress,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC3333)),
            modifier = Modifier.size(64.dp),
            contentPadding = PaddingValues(0.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        ) {
            Icon(Icons.Outlined.Circle, null, tint = VDTheme.White.copy(alpha = 0.4f), modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(com.wangjianshuo.voicedrop.R.string.tap_to_record), style = VDTheme.Caption.copy(fontSize = 11.sp))
    }
}
