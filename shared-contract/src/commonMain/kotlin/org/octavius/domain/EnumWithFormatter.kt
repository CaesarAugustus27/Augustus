package org.octavius.domain

/**
 * Interfejs dla enumów, które potrzebują własnej logiki formatowania do wyświetlania.
 *
 * Używany głównie w komponentach UI, gdy standardowa reprezentacja tekstowa
 * enum nie jest odpowiednia dla użytkownika końcowego.
 *
 * @param T Typ enuma implementującego ten interfejs.
 */
interface EnumWithFormatter<T : Enum<T>> {
    /**
     * Zwraca tekstową reprezentację wartości enum przeznaczoną do wyświetlania użytkownikowi.
     *
     * @return Sformatowany tekst gotowy do prezentacji.
     */
    fun toDisplayString(): String
}