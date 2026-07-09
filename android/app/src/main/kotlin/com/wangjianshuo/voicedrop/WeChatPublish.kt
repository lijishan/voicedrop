package com.wangjianshuo.voicedrop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeChatPublish(private val httpClient: HttpClient) {
    suspend fun publish(articleKey: String): WeChatPublishResult {
        return withContext(Dispatchers.IO) {
            httpClient.post("${API.FILES_BASE}/wechat/$articleKey")
        }
    }
}
