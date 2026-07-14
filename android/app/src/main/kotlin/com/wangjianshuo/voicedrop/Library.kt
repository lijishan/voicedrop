package com.wangjianshuo.voicedrop

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class LibraryStore(
    private val context: Context,
    private val httpClient: HttpClient,
    private val auth: AuthStore,
) {
    var recordings by mutableStateOf<List<Recording>>(emptyList())
        private set
    var selectedTab by mutableStateOf(Tab.RECORDINGS)
    var isRefreshing by mutableStateOf(false)
    var showRecordSheet by mutableStateOf(false)

    enum class Tab { RECORDINGS, COMMUNITY }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Article metadata cache (disk-backed to prevent HTTP storm on cold start)
    // stem → title. Persisted as JSON in SharedPreferences.
    private val titleCache: MutableMap<String, String>
    private val cachePrefs = context.getSharedPreferences("article_meta", Context.MODE_PRIVATE)
    private val fetchSemaphore = Semaphore(5)
    private val gson = com.google.gson.Gson()

    init {
        val json = cachePrefs.getString("titles", null)
        titleCache = if (json != null) {
            try { gson.fromJson(json, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type) ?: mutableMapOf() }
            catch (_: Exception) { mutableMapOf() }
        } else mutableMapOf()
    }

    private fun persistMetaCache() {
        cachePrefs.edit().putString("titles", gson.toJson(titleCache)).apply()
    }

    fun smartRefresh() {
        if (recordings.isEmpty()) refresh()
    }

    fun addLocalRecording(audioName: String) {
        val rec = Recording(audioName = audioName, uploaded = false)
        recordings = listOf(rec) + recordings.filter { it.audioName != audioName }
        // sync with server in background (delay for uploader to finish)
        scope.launch {
            kotlinx.coroutines.delay(3000)
            try {
                val serverList = loadServerRecordings()
                val merged = (loadLocalRecordings() + serverList)
                    .distinctBy { it.audioName }
                    .sortedByDescending { it.audioName }
                recordings = merged
            } catch (e: Exception) { Log.w("Library", "ignored", e) }
        }
    }

    fun refresh() {
        scope.launch {
            isRefreshing = true
            try {
                val merged = loadLocalRecordings().toMutableList()
                this@LibraryStore.recordings = merged.sortedByDescending { it.audioName }

                try {
                    val serverRecordings = loadServerRecordings()
                    for (server in serverRecordings) {
                        val idx = merged.indexOfFirst { it.audioName == server.audioName }
                        if (idx >= 0) {
                            merged[idx] = merged[idx].copy(
                                uploaded = true,
                                hasArticles = server.hasArticles,
                                isEmpty = server.isEmpty,
                                articleTitle = server.articleTitle,
                            )
                        } else {
                            merged.add(server)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("Library", "server sync failed: ${e.message}")
                }

                this@LibraryStore.recordings = merged.sortedByDescending { it.audioName }
            } catch (e: Exception) {
                Log.e("Library", "refresh error: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    private fun loadLocalRecordings(): List<Recording> {
        val dir = context.filesDir
        return (dir.listFiles() ?: emptyArray())
            .filter { it.name.startsWith("VoiceDrop-") && it.name.endsWith(".m4a") }
            .map { Recording(audioName = it.name, uploaded = false) }
    }

    private suspend fun loadServerRecordings(): List<Recording> {
        val raw: Map<String, Any> = httpClient.get("${API.FILES_BASE}/list")
        val keys = when {
            raw.containsKey("keys") -> (raw["keys"] as? List<*>)?.mapNotNull { it as? String }
            raw.containsKey("files") -> {
                (raw["files"] as? List<*>)?.mapNotNull {
                    when (it) {
                        is String -> it
                        is Map<*, *> -> it["name"] as? String ?: it["key"] as? String
                        else -> null
                    }
                } ?: emptyList()
            }
            raw.containsKey("objects") -> (raw["objects"] as? List<*>)?.mapNotNull {
                (it as? Map<*,*>)?.get("key") as? String
            }
            else -> emptyList()
        } ?: emptyList()
        Log.d("Library", "server sync: got ${keys.size} keys")
        return keys.mapNotNull { key ->
            val shortName = key.substringAfterLast("/")
            if (!shortName.startsWith("VoiceDrop-") || !shortName.endsWith(".m4a")) return@mapNotNull null
            val stem = RecordingName.stem(shortName)
            val hasArticles = keys.any { it.endsWith("articles/$stem.json") }
            val isEmpty = keys.any { it.endsWith("articles/$stem.empty") }
            var articleTitle: String? = titleCache[stem]
            if (hasArticles && articleTitle == null) {
                try {
                    fetchSemaphore.withPermit {
                        val doc: ArticleDoc = httpClient.get("${API.FILES_BASE}/download/articles/$stem.json")
                        val title = doc.articles.firstOrNull()?.title ?: doc.title
                        if (title != null) { titleCache[stem] = title; persistMetaCache() }
                        articleTitle = title
                    }
                } catch (e: Exception) { Log.w("Library", "ignored", e) }
            }
            Recording(
                audioName = shortName,
                serverKey = key,
                uploaded = true,
                hasArticles = hasArticles,
                isEmpty = isEmpty,
                articleTitle = articleTitle,
            )
        }
    }

    fun deleteRecording(audioName: String) {
        scope.launch {
            val rec = recordings.find { it.audioName == audioName }
            val key = rec?.serverKey ?: audioName
            try { httpClient.delete<Unit>("${API.FILES_BASE}/file/articles/${RecordingName.stem(audioName)}.json") } catch (e: Exception) { Log.w("Library", "ignored", e) }
            try { httpClient.delete<Unit>("${API.FILES_BASE}/file/$key") } catch (e: Exception) { Log.w("Library", "ignored", e) }
            try { httpClient.delete<Unit>("${API.FILES_BASE}/file/articles/${RecordingName.stem(audioName)}.empty") } catch (e: Exception) { Log.w("Library", "ignored", e) }
            try { httpClient.delete<Unit>("${API.FILES_BASE}/file/articles/${RecordingName.stem(audioName)}.srt") } catch (e: Exception) { Log.w("Library", "ignored", e) }
            try { httpClient.delete<Unit>("${API.FILES_BASE}/file/articles/${RecordingName.stem(audioName)}.blocked") } catch (e: Exception) { Log.w("Library", "ignored", e) }
            recordings = recordings.filter { it.audioName != audioName }
            titleCache.remove(RecordingName.stem(audioName)); persistMetaCache()
            context.filesDir.listFiles()?.find { it.name == audioName }?.delete()
        }
    }

    fun markPhase(stem: String, phase: MiningPhase) {
        recordings = recordings.map {
            if (it.stem == stem) it.copy(phase = phase) else it
        }
    }

    fun markDone(stem: String, status: String) {
        recordings = recordings.map { rec ->
            if (rec.stem != stem) rec
            else when (status) {
                "ready" -> rec.copy(hasArticles = true, phase = null)
                "empty" -> rec.copy(isEmpty = true, phase = null)
                else -> rec
            }
        }
    }

    suspend fun fetchArticleDoc(stem: String): ArticleDoc {
        return httpClient.get("${API.FILES_BASE}/download/articles/$stem.json")
    }

    suspend fun patchHead(stem: String, head: Int): ArticleDoc {
        return httpClient.patch("${API.FILES_BASE}/articles/$stem/head", mapOf("head" to head))
    }

    suspend fun fetchArticleHistory(stem: String): List<ArticleVersionEntry> {
        val doc: ArticleDoc = httpClient.get("${API.FILES_BASE}/articles/$stem/history")
        return doc.versions ?: emptyList()
    }

    suspend fun shareArticle(articleKey: String): String {
        val result: Map<String, String> = httpClient.get("${API.FILES_BASE}/share/$articleKey")
        return result["url"] ?: throw Exception("no share url")
    }

    suspend fun postWeChat(articleKey: String): WeChatPublishResult {
        return httpClient.post("${API.FILES_BASE}/wechat/$articleKey")
    }

    suspend fun fetchStyle(): StyleDoc? {
        return try {
            httpClient.get("${API.FILES_BASE}/style")
        } catch (_: Exception) { null }
    }

    suspend fun saveStyle(styleText: String): StyleDoc {
        return httpClient.putJson("${API.FILES_BASE}/style", mapOf("style" to styleText))
    }

    suspend fun fetchStyleHistory(): List<StyleVersion> {
        val doc: StyleDoc = httpClient.get("${API.FILES_BASE}/style/history")
        return doc.versions ?: emptyList()
    }

    suspend fun fetchUsage(): UsageBalance {
        return httpClient.get("${API.AGENT_BASE}/usage/balance")
    }

    suspend fun fetchUsageLedger(limit: Int = 50): UsageLedger {
        return httpClient.get("${API.AGENT_BASE}/usage/ledger?limit=$limit")
    }

    suspend fun deleteAccount() {
        httpClient.post<Unit>("${API.FILES_BASE}/account/delete")
    }

    suspend fun saveWeChatConfig(config: WeChatConfig) {
        val json = com.google.gson.Gson().toJson(config)
        httpClient.put("${API.FILES_BASE}/upload/WECHAT.json", json.toByteArray())
    }

    suspend fun fetchWeChatConfig(): WeChatConfig? {
        return try {
            httpClient.get("${API.FILES_BASE}/download/WECHAT.json")
        } catch (_: Exception) { null }
    }

    suspend fun fetchName(): String {
        return try {
            val raw = httpClient.getRaw("${API.FILES_BASE}/download/CLAUDE.md")
            val body = raw.body?.string() ?: ""
            raw.close()
            val match = Regex("# 我的名字\n(.+)").find(body)
            match?.groupValues?.get(1)?.trim() ?: ""
        } catch (_: Exception) { "" }
    }

    suspend fun saveName(name: String) {
        val body = "# 我的名字\n$name"
        httpClient.put("${API.FILES_BASE}/upload/CLAUDE.md", body.toByteArray())
    }

    suspend fun fetchWhoAmI(): WhoAmI {
        val result: WhoAmI = httpClient.get("${API.FILES_BASE}/whoami")
        auth.scope = result.scope
        return result
    }

    suspend     fun triggerMine(stem: String) {
        httpClient.post<Unit>("${API.FILES_BASE}/mine")
    }

    fun release() {
        scope.cancel()
    }
}
