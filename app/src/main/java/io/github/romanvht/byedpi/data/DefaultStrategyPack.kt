package io.github.romanvht.byedpi.data

import android.util.Base64
import io.github.romanvht.byedpi.BuildConfig

/**
 * Встроенный пак из 30 стратегий (порядок = порядок автоподбора, лидеры
 * первыми). Источник эталона — scripts/gen_pack.py + локальный список
 * разработчика (в репозиторий не входит).
 *
 * U2b: строки закодированы (XOR-маска поверх Base64) — наивный поиск по
 * APK (strings/jadx) не находит ни фрагментов аргументов, ни доменов.
 * В UI пак обозначается кодами A1..C10 (см. StrategyCodes); сами
 * последовательности параметров нигде не отображаются.
 *
 * Честная оговорка: это защита от «дешёвого списывания», не от целевого
 * реверс-инжиниринга (ТСПУ адаптируется к трафику, а не к коду).
 */
object DefaultStrategyPack {

    /** Версия пака: при её изменении автоподбор перезапускается сам. */
    const val VERSION = 2

    private val B64 = listOf(
        "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",
        "CFeHLtXh"
    ).joinToString("")

    // Константы XOR-маски (парные к scripts/gen_pack.py)
    private const val K1 = 0x1F3A5C7D
    private const val K2 = 0x2B6E9F41
    private const val K3 = 0x4C8D2E6A
    private const val K4 = 0x5A17E3B2

    /** 30 стратегий; распаковка ленивая, один раз на процесс. */
    val PACK: List<String> by lazy { decode() }

    private fun keyByte(i: Int): Int {
        val a = ((K1 and 0xFFFF) * (i + 17)) and 0xFFFF
        val b = ((K2 and 0xFFFF) * i) and 0xFFFF
        val c = (K3 ushr ((i and 3) * 4)) and 0xFFFF
        val d = (K4 shr (i and 7)) and 0xFF
        return (a xor b xor c xor d) and 0xFF
    }

    private fun decode(): List<String> {
        val raw = Base64.decode(B64, Base64.NO_WRAP)
        val out = ByteArray(raw.size)
        for (i in raw.indices) {
            out[i] = (raw[i].toInt() xor keyByte(i)).toByte()
        }
        val lines = String(out, Charsets.UTF_8).split('\n')
        if (BuildConfig.DEBUG) {
            check(lines.size == 30) { "pack: size " + lines.size + " != 30" }
            check(lines.none { it.isBlank() }) { "pack: blank line" }
        }
        return lines
    }

    /**
     * P6: ядро ротации (индексы в PACK); используется AutopilotManager
     * при byedpi_rotation_enabled (по умолчанию выключена).
     */
    val ROTATION_CORE: List<Int> = listOf(0, 2, 3, 1)

    /**
     * Стелс-порядок при равных баллах: меньше индекс пака => короче
     * полотно / меньше фейков => менее аномальный отпечаток
     * рукопожатия. Реализуется через строгий ">" в pickBest.
     */
    const val THRESHOLD = 24 // из 27; ниже — автоподбор «не уверен»
}
