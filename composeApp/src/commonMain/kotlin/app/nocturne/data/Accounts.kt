package app.nocturne.data

import app.nocturne.FileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Accounts(
    val youtubeCookie: String = "",
    val youtubeName: String? = null,
    val soundcloudToken: String = "",
    val soundcloudUserId: Long? = null,
    val soundcloudName: String? = null,
    val spotifyAccessToken: String = "",
    val spotifyRefreshToken: String = "",
    val spotifyExpiresAt: Long = 0,
    val spotifyName: String? = null,
    val spotifyAvatar: String? = null,
    val spotifyClientId: String = "", // the app the tokens were issued to (needed for refresh)
    val soundcloudAvatar: String? = null,
) {
    val youtubeSignedIn get() = youtubeCookie.contains("SAPISID")
    val soundcloudSignedIn get() = soundcloudToken.isNotBlank()
    val spotifySignedIn get() = spotifyRefreshToken.isNotBlank()
}

class AccountsStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Accounts> = _state.asStateFlow()
    val value: Accounts get() = _state.value

    fun update(transform: (Accounts) -> Accounts) {
        _state.update(transform)
        save(_state.value)
    }

    private fun decode(s: String?) = s?.let { runCatching { json.decodeFromString<Accounts>(it) }.getOrNull() }

    private fun load(): Accounts {
        decode(SecureStore.read(FILE))?.let { return it }
        // migrate the old plaintext file into the keystore
        val legacy = decode(FileStore.read(FILE)) ?: return Accounts()
        save(legacy)
        return legacy
    }

    private fun save(a: Accounts) {
        val text = json.encodeToString(Accounts.serializer(), a)
        if (SecureStore.write(FILE, text)) FileStore.delete(FILE) else FileStore.write(FILE, text)
    }

    companion object {
        private const val FILE = "accounts.json"
    }
}
