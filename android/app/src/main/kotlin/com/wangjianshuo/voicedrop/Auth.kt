package com.wangjianshuo.voicedrop

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

class AuthStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "voicedrop_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var anonToken: String
        get() = prefs.getString("anon_token", null) ?: generateAndStore()
        set(value) { prefs.edit().putString("anon_token", value).apply() }

    var scope: String?
        get() = prefs.getString("scope", null)
        set(value) { prefs.edit().putString("scope", value).apply() }

    var name: String?
        get() = prefs.getString("name", null)
        set(value) { prefs.edit().putString("name", value).apply() }

    var wechatAppId: String?
        get() = prefs.getString("wechat_appid", null)
        set(value) { prefs.edit().putString("wechat_appid", value).apply() }

    var wechatSecret: String?
        get() = prefs.getString("wechat_secret", null)
        set(value) { prefs.edit().putString("wechat_secret", value).apply() }

    private fun generateAndStore(): String {
        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        val hash = MessageDigest.getInstance("SHA-256").digest(random)
        val token = "anon_" + hash.take(32).joinToString("") { "%02x".format(it) }
        prefs.edit().putString("anon_token", token).apply()
        return token
    }

    fun adoptToken(token: String) {
        anonToken = token
        scope = null
    }
}
