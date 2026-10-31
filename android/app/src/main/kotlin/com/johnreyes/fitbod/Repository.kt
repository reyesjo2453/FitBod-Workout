package com.johnreyes.fitbod

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.johnreyes.fitbod.core.Edit
import com.johnreyes.fitbod.core.Merge
import com.johnreyes.fitbod.core.Section
import com.johnreyes.fitbod.core.SyncCodec
import com.johnreyes.fitbod.core.SyncDoc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Done(val at: Long) : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * Single source of truth. The local copy is stored in exactly the sync-file format
 * (`files/fitbod-sync.json`), so what is on the phone and what is in Drive never
 * drift apart in shape.
 */
class Repository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val docFile = File(context.filesDir, DriveClient.FILE)
    private val sessionFile = File(context.filesDir, "session.json")
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()
    private val syncMutex = Mutex()
    private var revision = 0L

    private val _doc = MutableStateFlow(load())
    val doc: StateFlow<SyncDoc> = _doc.asStateFlow()

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<ActiveSession?> = _session.asStateFlow()

    private val _sync = MutableStateFlow<SyncState>(
        prefs.getLong(KEY_LAST_SYNC, 0).takeIf { it > 0 }?.let { SyncState.Done(it) } ?: SyncState.Idle,
    )
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    private val _account = MutableStateFlow(GoogleSignIn.getLastSignedInAccount(context)?.email)
    val account: StateFlow<String?> = _account.asStateFlow()

    /** Set when Google needs the user to grant consent again; the activity launches it. */
    val consentIntent = MutableStateFlow<Intent?>(null)

    private val _pending = MutableStateFlow(dirty().isNotEmpty() || prefs.getBoolean(KEY_UNSYNCED, false))
    val hasUnsynced: StateFlow<Boolean> = _pending.asStateFlow()

    // ---- document edits ------------------------------------------------------------------

    fun edit(block: (SyncDoc) -> Edit) {
        synchronized(lock) {
            val result = block(_doc.value)
            revision++
            _doc.value = result.doc
            markDirty(result.dirty)
            prefs.edit().putBoolean(KEY_UNSYNCED, true).apply()
            _pending.value = true
            persist(result.doc)
        }
    }

    /** Replaces local data with an imported backup, keeping workouts from both. */
    fun import(text: String) {
        val incoming = SyncCodec.decode(text)
        edit { current -> Edit(Merge.merge(incoming, current, Section.entries.toSet(), System.currentTimeMillis()), Section.entries.toSet()) }
    }

    fun export(): String = SyncCodec.encode(_doc.value.copy(exportedAt = System.currentTimeMillis()))

    // ---- active session ------------------------------------------------------------------

    fun updateSession(block: (ActiveSession?) -> ActiveSession?) {
        synchronized(lock) {
            val s = block(_session.value)
            _session.value = s
            if (s == null) sessionFile.delete()
            else sessionFile.writeText(json.encodeToString(ActiveSession.serializer(), s))
        }
    }

    // ---- Google Drive sync ---------------------------------------------------------------

    fun onSignedIn(email: String?) {
        _account.value = email
        prefs.edit().remove(KEY_FILE_ID).apply()
        syncNow()
    }

    fun signedOut() {
        _account.value = null
        prefs.edit().remove(KEY_FILE_ID).remove(KEY_LAST_SYNC).apply()
        _sync.value = SyncState.Idle
    }

    fun syncNow() {
        if (_account.value == null) return
        scope.launch { syncMutex.withLock { runSync() } }
    }

    private fun runSync() {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account ?: run {
            _account.value = null
            return
        }
        _sync.value = SyncState.Syncing
        try {
            var token = GoogleAuthUtil.getToken(context, account, "oauth2:${DriveClient.SCOPE}")
            try {
                pushPull(DriveClient(token))
            } catch (e: DriveClient.AuthExpired) {
                GoogleAuthUtil.clearToken(context, token)
                token = GoogleAuthUtil.getToken(context, account, "oauth2:${DriveClient.SCOPE}")
                pushPull(DriveClient(token))
            }
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
            _sync.value = SyncState.Done(now)
        } catch (e: UserRecoverableAuthException) {
            consentIntent.value = e.intent
            _sync.value = SyncState.Failed("Google needs permission to reach your FitBod folder")
        } catch (e: Exception) {
            _sync.value = SyncState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun pushPull(drive: DriveClient) {
        val (snapshot, dirtyAtStart, rev) = synchronized(lock) { Triple(_doc.value, dirty(), revision) }

        val folderId = drive.findFolder() ?: drive.createFolder()
        val fileId = prefs.getString(KEY_FILE_ID, null)?.takeIf { it.isNotEmpty() } ?: drive.findFile(folderId)
        val remote = fileId?.let { runCatching { SyncCodec.decode(drive.download(it)) }.getOrElse { e ->
            if (e is DriveClient.AuthExpired) throw e
            // A stale cached id (file trashed or replaced): look it up again.
            prefs.edit().remove(KEY_FILE_ID).apply()
            drive.findFile(folderId)?.let { id -> SyncCodec.decode(drive.download(id)) }
        } }
        val now = System.currentTimeMillis()
        val merged = if (remote != null) Merge.merge(snapshot, remote, dirtyAtStart, now) else snapshot.copy(exportedAt = now)
        val text = SyncCodec.encode(merged)

        val id = prefs.getString(KEY_FILE_ID, null) ?: drive.findFile(folderId)
        val savedId = if (id != null) id.also { drive.update(it, text) } else drive.create(folderId, text)
        prefs.edit().putString(KEY_FILE_ID, savedId).apply()

        synchronized(lock) {
            if (revision == rev) {
                _doc.value = merged
                clearDirty()
                prefs.edit().putBoolean(KEY_UNSYNCED, false).apply()
                _pending.value = false
            } else {
                // Edited while syncing: keep those edits on top, and push them next time.
                _doc.value = Merge.merge(_doc.value, merged, dirty(), now)
            }
            persist(_doc.value)
        }
    }

    // ---- storage -------------------------------------------------------------------------

    private fun load(): SyncDoc {
        if (docFile.exists()) runCatching { return SyncCodec.decode(docFile.readText()) }
        return SyncCodec.decode(context.assets.open("seed.json").bufferedReader().readText())
    }

    private fun persist(doc: SyncDoc) {
        val tmp = File(docFile.parentFile, docFile.name + ".tmp")
        tmp.writeText(SyncCodec.encode(doc))
        tmp.renameTo(docFile)
    }

    private fun loadSession(): ActiveSession? =
        runCatching { json.decodeFromString(ActiveSession.serializer(), sessionFile.readText()) }.getOrNull()

    private fun dirty(): Set<Section> =
        prefs.getStringSet(KEY_DIRTY, emptySet()).orEmpty().mapNotNull { n -> Section.entries.firstOrNull { it.name == n } }.toSet()

    private fun markDirty(sections: Set<Section>) {
        if (sections.isEmpty()) return
        prefs.edit().putStringSet(KEY_DIRTY, (dirty() + sections).map { it.name }.toSet()).apply()
    }

    private fun clearDirty() = prefs.edit().remove(KEY_DIRTY).apply()

    companion object {
        private const val KEY_DIRTY = "dirty_sections"
        private const val KEY_FILE_ID = "drive_file_id"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_UNSYNCED = "unsynced"
    }
}
