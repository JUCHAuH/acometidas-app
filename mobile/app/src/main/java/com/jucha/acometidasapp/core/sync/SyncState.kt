package com.jucha.acometidasapp.core.sync

enum class SyncState {
    PENDING,  // Creado offline, no sincronizado
    SYNCING,  // En proceso de envío a Supabase
    SYNCED,   // ✓ En servidor
    FAILED    // ✗ Error, necesita retry
}
