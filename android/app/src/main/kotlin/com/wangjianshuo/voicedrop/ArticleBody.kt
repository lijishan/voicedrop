package com.wangjianshuo.voicedrop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
                chunks.add(BodyChunk(
                    type = ArticleSegment.PHOTO,
                    photoRelKey = relKey,
                    lineNumber = lineNum,
                    imageIndex = imgIdx,
                ))
            } else {
                var text = line
                text = Regex("<!--.*?-->").replace(text, "")
                if (text.isNotBlank()) {
                    chunks.add(BodyChunk(
                        type = ArticleSegment.TEXT,
                        text = text.trim(),
                        lineNumber = lineNum,
                    ))
                }
            }
        }
        return chunks
    }

    fun stripMarkers(body: String): String {
        return Regex("\\[\\[photo:[^\\]]+\\]\\]").replace(body, "")
            .let { Regex("<!--.*?-->").replace(it, "") }
    }
}

@Composable
fun ArticleBodyView(body: String, ownerScope: String?) {
    val chunks = ArticleBody.segments(body, ownerScope)
    Column(modifier = Modifier.fillMaxWidth()) {
        chunks.forEach { chunk ->
            when (chunk.type) {
                ArticleSegment.TEXT -> {
                    Text(
                        text = chunk.text ?: "",
                        style = VDTheme.Body,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                ArticleSegment.PHOTO -> {
                    PhotoTile(relKey = chunk.photoRelKey ?: "", ownerScope = ownerScope)
                }
            }
        }
    }
}

@Composable
fun PhotoTile(relKey: String, ownerScope: String?) {
    val scope = ownerScope ?: ""
    val fullKey = "$scope$relKey"
    val url = "${API.FILES_BASE}/photo/$fullKey"

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = "Photo",
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(VDTheme.Divider),
        contentScale = ContentScale.Crop,
    )
}

// CommunityList and CommunityPostView defined in Community.kt
