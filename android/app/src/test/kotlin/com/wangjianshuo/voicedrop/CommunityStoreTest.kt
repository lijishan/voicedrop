package com.wangjianshuo.voicedrop

import com.google.gson.Gson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CommunityStoreTest {
    private val gson = Gson()

    @Test
    fun `community list response parse`() {
        val json = """{"posts":[{"shareId":"A","author":"B","title":"C",
            "firstSharedAt":1783,"count":1,"mine":false}], "truncated":false}"""
        val resp = gson.fromJson(json, CommunityListResp::class.java)
        assertEquals(1, resp.posts.size)
        assertEquals("C", resp.posts[0].title)
    }

    @Test
    fun `empty community list`() {
        val json = """{"posts":[]}"""
        val resp = gson.fromJson(json, CommunityListResp::class.java)
        assertEquals(0, resp.posts.size)
    }

    @Test
    fun `community post with replyTo as string`() {
        val json = """{"posts":[{"shareId":"A","author":"B","title":"回复",
            "firstSharedAt":1,"count":1,"mine":false,
            "replyTo":"_x2wBq8Kj43G"}]}"""
        val resp = gson.fromJson(json, CommunityListResp::class.java)
        assertEquals(1, resp.posts.size)
        assertEquals("_x2wBq8Kj43G", resp.posts[0].replyTo)
    }
}
