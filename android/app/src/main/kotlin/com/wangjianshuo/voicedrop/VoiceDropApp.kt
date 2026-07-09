package com.wangjianshuo.voicedrop

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VoiceDropApp : Application() {
    lateinit var auth: AuthStore private set
    lateinit var api: API private set
    lateinit var httpClient: HttpClient private set
    lateinit var library: LibraryStore private set
    private var statusSession: StatusSession? = null

    override fun onCreate() {
        super.onCreate()
        auth = AuthStore(this)
        api = API()
        httpClient = HttpClient(auth)
        library = LibraryStore(this, httpClient, auth)
        statusSession = StatusSession(auth, httpClient, library)
        statusSession?.connect()
        Uploader(this, httpClient, auth).drainPending()
        CoroutineScope(Dispatchers.IO).launch {
            try { library.fetchWhoAmI() } catch (e: Exception) { Log.w("VoiceDropApp", "ignored", e) }
        }
    }
}
