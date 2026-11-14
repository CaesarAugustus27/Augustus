package org.octavius.modules.spotify.trashbin

/** Pozycja menu kontekstowego tylko dla jednego utworu albo wykonawcy, z napisem zależnym od tego, czy już jest w koszu. */
object TrashMenu {
    private val item = Spicetify.ContextMenu.Item(ADD_LABEL, { uris -> TrashStore.toggle(uris[0]) }, ::shouldAdd, TRASH_ICON)

    fun register() = item.register()

    private fun shouldAdd(uris: Array<String>): Boolean {
        val uri = uris.singleOrNull()
        if (uri == null || !TrashStore.accepts(uri)) return false
        item.name = if (uri in TrashStore) REMOVE_LABEL else ADD_LABEL
        return true
    }
}
