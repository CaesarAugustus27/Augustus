package org.octavius.modules.spotify.spicetify.trash

import kotlinx.browser.document
import kotlinx.coroutines.launch
import org.octavius.modules.spotify.localization.SpotifyTr
import org.octavius.modules.spotify.spicetify.Spicetify
import org.octavius.modules.spotify.spicetify.spicetifyScope
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.js.json

/** Ustawienia w menu profilu → „Kosz”. */
object Settings {
    private const val STYLE = """
        .augustus-trashbin { display: grid; gap: 14px; min-inline-size: 360px; }
        .augustus-trashbin label { align-items: center; cursor: pointer; display: flex; gap: 12px; }
        .augustus-trashbin input { accent-color: var(--spice-button); block-size: 18px; inline-size: 18px; margin: 0; }
        .augustus-trashbin p { color: var(--spice-subtext); margin: 0; }
        .augustus-trashbin button {
            background: none; border: 1px solid var(--spice-subtext); border-radius: 999px; color: var(--spice-text);
            cursor: pointer; font-weight: 700; justify-self: start; padding: 6px 16px;
        }
        .augustus-trashbin button:hover { border-color: var(--spice-text); }
    """

    fun register() = Spicetify.Menu.Item(SpotifyTr.Settings.title(), false, { open() }, TRASH_ICON).register()

    private fun open() {
        val view = document.createElement("div") as HTMLElement
        view.className = "augustus-trashbin"
        view.innerHTML = """
            <style>$STYLE</style>
            <label><input type="checkbox" name="widget">${SpotifyTr.Settings.playbarButton()}</label>
            <p class="summary"></p>
            <button type="button" name="restore">${SpotifyTr.Settings.restore()}</button>
            <p>${SpotifyTr.Settings.restoreDescription()}</p>
        """

        val widget = view.querySelector("input[name=\"widget\"]") as HTMLInputElement
        widget.checked = TrashButtons.playbarEnabled
        widget.onchange = { TrashButtons.setPlaybarEnabled(widget.checked) }

        val summary = view.querySelector(".summary") as HTMLElement
        summary.textContent = summaryText()
        (view.querySelector("button[name=\"restore\"]") as HTMLElement).onclick = {
            spicetifyScope.launch {
                TrashCopy.restore()
                summary.textContent = summaryText()
            }
        }

        Spicetify.PopupModal.display(json("title" to SpotifyTr.Settings.title(), "content" to view))
    }

    private fun summaryText() =
        SpotifyTr.Settings.summary(TrashStore.trackUris.size, TrashStore.artistUris.size)
}
