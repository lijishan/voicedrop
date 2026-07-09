package com.wangjianshuo.voicedrop

import com.google.gson.Gson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ModelParseTest {
    private val gson = Gson()

    @Test
    fun `community list parse`() {
        val json = """{"posts":[{"shareId":"-JAWMEV","author":"1D6A95",
            "title":"测试","firstSharedAt":1783543639476,"updatedAt":1783543639476,
            "count":1,"mine":false}]}"""
        val resp = gson.fromJson(json, CommunityListResp::class.java)
        assertEquals(1, resp.posts.size)
        assertEquals("-JAWMEV", resp.posts[0].shareId)
    }

    @Test
    fun `file list parse`() {
        val json = """{"files":[{"name":"VoiceDrop-20260708143000-5-Wed-pm.m4a",
            "size":1234,"uploaded":"2026-07-08T06:30:00Z"}]}"""
        val map: Map<String, Any> = gson.fromJson(json, object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type)
        val files = map["files"] as? List<*> ?: emptyList<Any>()
        assertEquals(1, files.size)
        val name = (files[0] as? Map<*,*>)?.get("name") as? String
        assertEquals("VoiceDrop-20260708143000-5-Wed-pm.m4a", name)
    }

    @Test
    fun `article doc createdAt ISO string`() {
        val json = """{"schema":2,"articles":[{"title":"银座夜走","body":"..."}],
            "createdAt":"2026-07-08T05:16:28.086Z"}"""
        val doc = gson.fromJson(json, ArticleDoc::class.java)
        assertNotNull(doc)
        assertEquals("银座夜走", doc.articles.firstOrNull()?.title)
    }

    @Test
    fun `article doc createdAt epoch number`() {
        val json = """{"schema":2,"articles":[{"title":"测试","body":"..."}],
            "createdAt":1783543639476}"""
        val doc = gson.fromJson(json, ArticleDoc::class.java)
        assertNotNull(doc)
        assertEquals("测试", doc.articles.firstOrNull()?.title)
    }

    @Test
    fun `followup question parse`() {
        val json = """{"id":"q1","articleIndex":0,"text":"这个问题只有你知道",
            "status":"pending","createdAt":1783543639476}"""
        val q = gson.fromJson(json, FollowupQuestion::class.java)
        assertEquals(QuestionStatus.PENDING, q.status)
    }

    @Test
    fun `wechat publish result parse`() {
        val json = """{"ok":true,"created":true,"updated":false}"""
        val r = gson.fromJson(json, WeChatPublishResult::class.java)
        assertTrue(r.ok)
        assertTrue(r.created)
    }
}
