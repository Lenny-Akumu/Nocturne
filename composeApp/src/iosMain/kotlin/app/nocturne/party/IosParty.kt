package app.nocturne.party

actual fun startPartyServer(state: () -> String): PartyServer = throw UnsupportedOperationException("Local sessions aren't available on iPhone")

actual fun lanAddresses(): List<String> = emptyList()
