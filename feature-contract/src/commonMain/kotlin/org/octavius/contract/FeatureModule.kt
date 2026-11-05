package org.octavius.contract

import org.octavius.navigation.Tab


/**
 * Interfejs reprezentujący kompletny, spójny moduł funkcjonalny ("Feature").
 *
 * Każdy moduł w aplikacji (np. Asian Media, Games) implementuje ten interfejs,
 * aby w jednym miejscu zadeklarować wszystkie swoje punkty integracji z główną aplikacją:
 * - Zakładkę w UI (`Tab`)
 * - Endpointy API (`ApiModule`)
 * - Ekrany dostępne z zewnątrz (`ScreenFactory`)
 * - Schemat bazy z własnymi migracjami
 *
 * Implementacją jest `object` w pakiecie `org.octavius`. Główna aplikacja (`desktop-app`) sama znajduje
 * takie obiekty na classpathie i konfiguruje na ich podstawie nawigację, serwer API, routing zdarzeń
 * i migracje. Nigdzie nie trzeba ich dopisywać.
 */
interface FeatureModule {
    /** Nazwa modułu. */
    val name: String

    /**
     * Pozycja featura: mniejsza liczba stoi wcześniej. Wyznacza kolejność zakładek na pasku (pierwsza
     * jest aktywna po starcie) i kolejność migracji schematów featurów. Odstępy co 10 zostawiają miejsce
     * na feature wstawiony pomiędzy.
     */
    val order: Int

    /**
     * Schemat bazy, w którym feature trzyma swoje tabele, albo `null`, jeśli feature własnych tabel nie ma.
     *
     * Schemat trafia do `search_path`, a jego migracje leżą w zasobach modułu pod
     * `db/migration/<schemat>/` i mają własną historię w `<schemat>.octavius_migration_history`.
     * Migrator sam tworzy schemat razem z tą historią, więc pierwsza migracja nie potrzebuje
     * `CREATE SCHEMA`. Migracje featura mogą korzystać z `public` (wykonuje się przed featurami), ale nie
     * z tabel innych featurów. `public` nie należy do żadnego featura.
     */
    val schema: String?

    /**
     * Zwraca definicję zakładki UI dla tego modułu.
     * @return Obiekt `Tab` lub `null`, jeśli moduł nie ma reprezentacji w głównym menu.
     */
    fun getTab(): Tab?

    /**
     * Zwraca listę modułów API, które ten feature chce zarejestrować w serwerze Ktor.
     * @return Lista obiektów `ApiModule` lub `null`, jeśli moduł nie udostępnia API.
     */
    fun getApiModules(): List<ApiModule>?

    /**
     * Zwraca listę fabryk ekranów, które mogą być tworzone na żądanie (np. przez API).
     * @return Lista obiektów `ScreenFactory` lub `null`, jeśli moduł nie ma ekranów dostępnych z zewnątrz.
     */
    fun getScreenFactories(): List<ScreenFactory>?
}