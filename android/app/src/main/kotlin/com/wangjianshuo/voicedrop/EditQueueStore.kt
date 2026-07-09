package com.wangjianshuo.voicedrop

import android.content.Context
import com.google.gson.Gson

class EditQueueStore(private val context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("edit_queue", Context.MODE_PRIVATE)

    data class PersistedEdit(
        val id: String,
        val text: String,
        val articleIndex: Int = 0,
        val stem: String,
    )

    fun saveAll(stem: String, edits: List<PersistedEdit>) {
        prefs.edit().putString("queue_$stem", gson.toJson(edits)).apply()
    }

    fun loadAll(stem: String): List<PersistedEdit> {
        val json = prefs.getString("queue_$stem", null) ?: return emptyList()
        return try {
            gson.fromJson(json, Array<PersistedEdit>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(stem: String) {
        prefs.edit().remove("queue_$stem").apply()
    }
}

class CommandQueueStore(private val context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("cmd_queue", Context.MODE_PRIVATE)

    data class PersistedCmd(
        val id: String,
        val text: String,
    )

    fun save(cmds: List<PersistedCmd>) {
        prefs.edit().putString("queue", gson.toJson(cmds)).apply()
    }

    fun load(): List<PersistedCmd> {
        val json = prefs.getString("queue", null) ?: return emptyList()
        return try {
            gson.fromJson(json, Array<PersistedCmd>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear() {
        prefs.edit().remove("queue").apply()
    }
}
