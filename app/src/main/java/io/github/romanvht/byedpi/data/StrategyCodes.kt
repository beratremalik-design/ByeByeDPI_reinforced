package io.github.romanvht.byedpi.data

/**
 * U2: условные обозначения стратегий пака — одна заглавная буква латиницы
 * + порядковый номер: 0 → A1 … 9 → A10, 10 → B1 … 19 → B10, 20 → C1 … 29 → C10.
 *
 * Код сам по себе ничего не сообщает о параметрах: последовательность
 * аргументов не показывается ни в одном месте UI, отчётов и уведомлений
 * (в самом APK строки пака закодированы — см. DefaultStrategyPack, U2b).
 *
 * Пользовательские (введённые вручную) строки кодов не получают и
 * показываются как есть — это осознанный ввод самого пользователя.
 */
object StrategyCodes {

    /** Индекс (0-based) → код A1..C10. Отрицательный индекс → "?". */
    fun codeFor(index: Int): String {
        if (index < 0) return "?"
        val letter = 'A' + index / 10
        val number = index % 10 + 1
        return "$letter$number"
    }

    /** Код A1..C10 → индекс пака; неизвестный код → null. */
    fun indexOrNull(code: String): Int? {
        val trimmed = code.trim().uppercase()
        if (trimmed.length !in 2..3) return null
        val letter = trimmed[0]
        if (letter !in 'A'..'Z') return null
        val number = trimmed.substring(1).toIntOrNull() ?: return null
        if (number !in 1..10) return null
        val index = (letter - 'A') * 10 + number - 1
        return if (index < DefaultStrategyPack.PACK.size) index else null
    }

    /**
     * Отображаемое имя для строки аргументов: код пака, если строка
     * совпадает со стратегией пака; иначе строка возвращается как есть.
     *
     * @param sni значение SNI, которое автотест/диагностика подставляли
     * вместо {sni}: перед поиском плейсхолдер восстанавливается, иначе
     * стратегии A15/C2 пака (с {sni}) не совпадут.
     */
    fun displayFor(command: String, sni: String? = null): String {
        var probe = command
        if (!sni.isNullOrBlank()) probe = probe.replace("\"$sni\"", "{sni}")
        val index = DefaultStrategyPack.PACK.indexOf(probe)
        return if (index >= 0) codeFor(index) else command
    }
}
