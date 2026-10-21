package app.nocturne.data

// Secrets kept in the OS keystore (Android Keystore, Windows DPAPI, iOS Keychain); null on failure.
expect object SecureStore {
    fun read(name: String): String?
    fun write(name: String, content: String): Boolean
}
