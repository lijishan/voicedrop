package com.wangjianshuo.voicedrop

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

enum class ArticleSegment {
    TEXT, PHOTO
}

data class BodyChunk(
    val type: ArticleSegment,
    val text: String? = null,
    val photoRelKey: String? = null,
    val lineNumber: Int = 0,
    val imageIndex: Int = 0,
)

object ArticleBody {
    fun segments(body: String, ownerScope: String? = null): List<BodyChunk> {
        val chunks = mutableListOf<BodyChunk>()
        val lines = body.split("\n")
        var lineNum = 0
        var imgIdx = 0
        for (line in lines) {
            lineNum++
            if (line.isBlank()) continue
            val photoMatch = Regex("\\[\\[photo:([^\\]]+)\\]\\]").find(line)
            if (photoMatch != null) {
                imgIdx++
                val relKey = photoMatch.groupValues[1]
                chunks.add(BodyChunk(type = ArticleSegment.PHOTO, photoRelKey = relKey, lineNumber = lineNum, imageIndex = imgIdx))
            } else {
                var text = line
                text = Regex("<!--.*?-->").replace(text, "")
                if (text.isNotBlank()) {
                    chunks.add(BodyChunk(type = ArticleSegment.TEXT, text = text.trim(), lineNumber = lineNum))
                }
            }
        }
        return chunks
    }

    fun stripMarkers(body: String): String {
        return Regex("\\[\\[photo:[^\\]]+\\]\\]").replace(body, "").let { Regex("<!--.*?-->").replace(it, "") }
    }
}

fun LayoutCoordinates.topLeftPos(): Offset {
    var coords: LayoutCoordinates? = this
    var x = 0f
    var y = 0f
    while (coords != null) {
        val pos = coords.positionInRootIfAvailable()
        if (pos != null) { x += pos.x; y += pos.y }
        coords = coords.parentCoordinates
    }
    return Offset(x, y)
}

fun LayoutCoordinates.positionInRootIfAvailable(): Offset? {
    return try {
        val method = this::class.java.getMethod("positionInRoot")
        method.invoke(this) as? Offset
    } catch (_: Exception) {
        try {
            val method = this::class.java.getMethod("getRootCoordinates")
            val rootCoords = method.invoke(this) as? LayoutCoordinates
            rootCoords?.let {
                val m2 = it::class.java.getMethod("positionInWindow")
                m2.invoke(it) as? Offset
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun ArticleBodyView(
    body: String,
    ownerScope: String?,
    onTextLongPress: ((line: Int, text: String, offset: Offset) -> Unit)? = null,
    onImageLongPress: ((relKey: String, offset: Offset) -> Unit)? = null,
) {
    val chunks = ArticleBody.segments(body, ownerScope)
    Column(modifier = Modifier.fillMaxWidth()) {
        chunks.forEach { chunk ->
            when (chunk.type) {
                ArticleSegment.TEXT -> {
                    var textOffset by remember { mutableStateOf(Offset.Zero) }
                    Box(modifier = Modifier.fillMaxWidth().onGloballyPositioned { textOffset = it.topLeftPos() }) {
                        Text(
                            text = chunk.text ?: "",
                            style = VDTheme.Body,
                            modifier = Modifier.padding(vertical = 4.dp).pointerInput(Unit) {
                                detectTapGestures(onLongPress = {
                                    onTextLongPress?.invoke(chunk.lineNumber, chunk.text ?: "", textOffset)
                                })
                            },
                        )
                    }
                }
                ArticleSegment.PHOTO -> {
                    PhotoTile(relKey = chunk.photoRelKey ?: "", ownerScope = ownerScope, onLongPress = { offset ->
                        onImageLongPress?.invoke(chunk.photoRelKey ?: "", offset)
                    })
                }
            }
        }
    }
}

@Composable
fun PhotoTile(relKey: String, ownerScope: String?, onLongPress: ((offset: Offset) -> Unit)? = null) {
    val scope = ownerScope ?: ""
    val fullKey = "$scope$relKey"
    val url = "${API.FILES_BASE}/photo/$fullKey"
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(modifier = Modifier.fillMaxWidth().onGloballyPositioned { offset = it.topLeftPos() }) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
            contentDescription = "Photo",
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(VDTheme.Divider)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress?.invoke(offset) }) },
            contentScale = ContentScale.Crop,
        )
    }
}

// CommunityList and CommunityPostView defined in Community.kt
