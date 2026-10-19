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
) {
    val youtubeSignedIn get() = youtubeCookie.contains("SAPISID")
    val soundcloudSignedIn get() = soundcloudToken.isNotBlank()
    val spotifySignedIn get() = spotifyRefreshToken.isNotBlank()
}

class AccountsStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(
        FileStore.read(FILE)?.let { runCatching { json.decodeFromString<Accounts>(it) }.getOrNull() } ?: Accounts()
    )
    val state: StateFlow<Accounts> = _state.asStateFlow()
    val value: Accounts get() = _state.value

    fun update(transform: (Accounts) -> Accounts) {
        _state.update(transform)
        FileStore.write(FILE, json.encodeToString(Accounts.serializer(), _state.value))
    }

    companion object {
        private const val FILE = "accounts.json"
    }
}
