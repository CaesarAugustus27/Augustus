package org.octavius.feature.books

import org.octavius.contract.ApiModule
import org.octavius.contract.FeatureModule
import org.octavius.contract.ScreenFactory
import org.octavius.feature.books.navigation.BooksTab
import org.octavius.navigation.Tab

object BooksFeature : FeatureModule {

    override val name: String = "books"
    override val order: Int = 30
    override val schema: String = "books"

    // Zwracamy naszą nową zakładkę
    override fun getTab(): Tab = BooksTab()

    override fun getApiModules(): List<ApiModule>? = null
    override fun getScreenFactories(): List<ScreenFactory>? = null
}
