package org.octavius.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * Ekran na stosie zakładki.
 *
 * Dla ekranów budowanych z gotowych komponentów (formularz, raport) nie trzeba pisać własnej
 * klasy - wystarczy [ComponentScreen]:
 *
 * ```kotlin
 * AppRouter.navigateTo(ComponentScreen(title) { FormView(formHandler) })
 * ```
 */
interface Screen {
    /** Tytuł ekranu wyświetlany w górnym pasku nawigacji */
    val title: String

    /**
     * Rysuje ekran, dopóki leży na szczycie stosu aktywnej zakładki. Po zejściu ze szczytu albo przełączeniu
     * zakładki ekran wypada z kompozycji i jego `remember` przepada, więc stan, który ma przetrwać powrót,
     * trzyma się poza [Content].
     */
    @Composable
    fun Content()
}

/**
 * Tytuł i opcjonalna ikona, którymi zakładka pokazuje się na pasku zakładek. Ikona stoi nad tytułem.
 */
data class TabOptions(
    val title: String, val icon: Painter? = null
)

/**
 * Zakładka na pasku aplikacji. Feature oddaje ją z `FeatureModule.getTab()`, a `AppRouter` trzyma dla niej
 * osobny stos ekranów, który zostaje nietknięty przy przełączaniu zakładek.
 */
interface Tab {

    /**
     * Stały, unikalny identyfikator, np. `"books"`. Po nim zakładkę wybierają `AppRouter.switchToTab` i
     * `AppRouter.navigateTo` z `tabId`, a przez nie zdarzenia nawigacji przychodzące z API.
     */
    val id: String

    /**
     * Tytuł i ikona na pasku zakładek. Getter jest `@Composable`, żeby ikonę dało się zbudować przez
     * `rememberVectorPainter` albo `painterResource`.
     */
    val options: TabOptions
        @Composable get

    /**
     * Tworzy ekran główny zakładki, czyli dno jej stosu. `AppRouter.initialize` woła to raz, a
     * `AppRouter.goBack` tego ekranu nie zdejmuje.
     */
    fun getInitialScreen(): Screen

    /**
     * `false` chowa zakładkę z paska. Otwiera się ją wtedy tylko po [id], tak jak ustawienia z przycisku w
     * górnym pasku.
     */
    val isVisibleInNavBar: Boolean
        get() = true
}

