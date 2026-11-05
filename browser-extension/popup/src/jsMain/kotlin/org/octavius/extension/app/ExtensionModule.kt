package org.octavius.extension.app

import org.octavius.navigation.Screen

/**
 * Definiuje kontrakt dla modułu wtyczki
 */
interface ExtensionModule {

    /** Unikalne ID modułu, np. "asian-media" */
    val id: String


    /**
     * Tworzy ekran na podstawie stringa JSON z danymi specyficznymi dla tego modułu.
     * Moduł sam jest odpowiedzialny za deserializację.
     * @return Screen do wyświetlenia lub null, jeśli dane są nieprawidłowe.
     */
    fun createScreenFromJson(jsonData: String): Screen?
}