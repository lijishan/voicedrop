package com.wangjianshuo.voicedrop

import com.google.gson.annotations.SerializedName

// --- Article Models ---

data class ArticleDoc(
    val schema: Int? = null,
    val id: String? = null,
    val sourceAudio: String? = null,
    val createdAt: Any? = null,
    val transcript: String? = null,
    val srt: String? = null,
    val articles: List<MinedArticle> = emptyList(),
    val versions: List<ArticleVersionEntry>? = null,
    val head: Int? = null,
    val tags: List<String>? = null,
    val questions: List<FollowupQuestion>? = null,
    @SerializedName("wechatMediaId") val _wechatMediaId: String? = null,
    // v1 fallback
    val title: String? = null,
    val body: String? = null,
)

data class MinedArticle(
    val title: String,
    val body: String,
    val style: Int? = null,
    val wechatMediaId: String? = null,
)

data class ArticleVersionEntry(
    val v: Int,
    val savedAt: Any? = null,
    val source: String? = null,
    val articles: List<MinedArticle>,
)

data class FollowupQuestion(
    val id: String,
    val articleIndex: Int,
    val text: String,
    val status: QuestionStatus = QuestionStatus.PENDING,
    val createdAt: Any? = null,
)

enum class QuestionStatus {
    @SerializedName("pending") PENDING,
    @SerializedName("answered") ANSWERED,
    @SerializedName("skipped") SKIPPED
}

// --- Recording ---

data class Recording(
    val audioName: String,
    val serverKey: String = audioName,
    val uploaded: Boolean = false,
    val hasArticles: Boolean = false,
    val isEmpty: Boolean = false,
    val articleTitle: String? = null,
    val tags: List<String>? = null,
    val coverPhotoKey: String? = null,
    val uploading: Boolean = false,
    var phase: MiningPhase? = null,
    var blockReason: BlockReason? = null,
) {
    val stem: String
        get() = audioName
            .substringBeforeLast(".m4a")
            .substringBeforeLast(".empty")

    val articleKey: String
        get() = "articles/$stem.json"

    val dateTimeLabel: String
        get() = Formatting.formatDateTime(audioName)

    val smartDate: String
        get() = Formatting.formatSmartDate(audioName)

    val durationSec: Int
        get() {
            val parts = audioName.removeSuffix(".m4a").split("-")
            return parts.getOrNull(2)?.toIntOrNull() ?: 0
        }

    val durationLabel: String
        get() = Formatting.formatDurationLabel(durationSec)

    val rowTitle: String
        get() = articleTitle ?: tags?.firstOrNull() ?: dateTimeLabel

    val isProcessed: Boolean
        get() = hasArticles || isEmpty || blockReason != null
}

enum class MiningPhase { asr, mining }

enum class BlockReason { noCredit, tooLong }

// --- Community ---

data class CommunityPost(
    val shareId: String,
    val author: String = "",
    val title: String? = null,
    val firstSharedAt: Any? = null,
    val updatedAt: Any? = null,
    val count: Int? = null,
    val mine: Boolean = false,
    val replyTo: Any? = null,
    val hasPhoto: Boolean = false,
    val coverPhotoKey: String? = null,
    val preview: String? = null,
)

data class CommunityFullPost(
    val shareId: String,
    val author: String,
    val title: String? = null,
    val articles: List<MinedArticle>? = null,
    val owner: String? = null,
    val photos: List<String>? = null,
)

// --- Settings ---

data class WeChatConfig(
    val appid: String = "",
    val secret: String = "",
    val enabled: Boolean = false,
    @SerializedName("thumb_media_id") val thumbMediaId: String? = null,
    @SerializedName("coverMediaIds") val coverMediaIds: Map<String, String>? = null,
)

data class StyleDoc(
    val schema: Int = 3,
    val head: Int? = null,
    val versions: List<StyleVersion>? = null,
    val createdAt: Any? = null,
    val updatedAt: Any? = null,
    val style: String? = null,
)

data class StyleVersion(
    val v: Int,
    val savedAt: Any? = null,
    val source: String? = null,
    @SerializedName("style") val styleText: String,
)

// --- Usage ---

data class UsageBalance(
    val balanceUy: Long = 0,
    val grantedUy: Long = 0,
    val spentUy: Long = 0,
    @SerializedName("suanli") val suanli: String? = null,
)

data class UsageLedger(
    val items: List<UsageLedgerItem> = emptyList(),
    val count: Int = 0,
)

data class UsageLedgerItem(
    val kind: String = "",
    val reason: String? = null,
    val amountUy: Long = 0,
    val balanceUy: Long = 0,
    val createdAt: Long? = null,
    val detail: Map<String, Any?>? = null,
)

// --- WebSocket Message Protocol ---

data class WSMessage(
    val type: String,
    val id: String? = null,
    val doc: ArticleDoc? = null,
    val text: String? = null,
    val message: String? = null,
    val stem: String? = null,
    val status: String? = null,
    val replyText: String? = null,
)

// --- UI Config ---

data class MenuNode(
    val id: String,
    val label: String,
    val type: String? = null,
    val children: List<MenuNode>? = null,
    val instruction: String? = null,
)

data class LongPressConfig(
    val image: List<MenuNode>? = null,
    val text: List<MenuNode>? = null,
)

data class UIConfig(
    val schema: Int? = null,
    val pages: Map<String, UIPageConfig>? = null,
)

data class UIPageConfig(
    val longpress: LongPressConfig? = null,
)

// --- WeChat Publish ---

data class WeChatPublishResult(
    val ok: Boolean = false,
    val created: Boolean = false,
    val updated: Boolean = false,
    val errcode: Int? = null,
    val errmsg: String? = null,
)

// --- WhoAmI ---

data class WhoAmI(
    val scope: String,
)

// --- Community list response wrapper ---

data class CommunityListResp(val posts: List<CommunityPost>)

data class CommunityFeedResp(
    val posts: List<CommunityPost>,
    val likes: Map<String, Int>? = null,
    val replies: Map<String, Int>? = null,
    val mineLikes: List<String>? = null,
)

// --- API generic responses ---

data class ApiError(
    val error: String,
    val message: String? = null,
)
