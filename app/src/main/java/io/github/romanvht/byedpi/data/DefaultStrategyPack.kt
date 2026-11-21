package io.github.romanvht.byedpi.data

/**
 * D2: встроенный пак из 30 стратегий по умолчанию (порядок = порядок
 * автоподбора, лидеры первыми). Синхронизируется дословно с эталоном
 * download/default_pack_30.txt (см. scripts/gen_pack.py), строки 1-4 —
 * референсы V3a/V4b/C/Z1 из паспорта v5.1 §5 и раунда 6.
 *
 * Правила паспорта §7 соблюдены: минимум 4 эшелона; в общем -An только
 * "-o1 -r-5+se -a1"; фейк-SNI только белый пул ядра / {sni}; позиции
 * полотна по возрастанию; тройка (полотно × усиление × добивка) не
 * повторяется в соседних строках — единственное задокументированное
 * исключение: строки 1-3 (референсы, исторически C — база V3a/V4b).
 *
 * Не показывается в UI; используется автоподбором (AutopilotManager)
 * и источником "pack" в настройках проверки.
 */
object DefaultStrategyPack {
    /** Версия пака: при её изменении автоподбор перезапускается сам. */
    const val VERSION = 1

    /** 30 стратегий, порядок = порядок автоподбора (лидеры первыми). */
    val PACK: List<String> = listOf(
        // 1: V3a_ref (27/27 x2)
        "-H:\"google.com\" -d1+s -o2 -s5 -r5 -a1 -An -H:\"youtubei.googleapis.com\" -d1 -s1+s -s4+s -s7+s -s9+s -s12+s -s15+s -s17+s -s20+s -s24+s -s27+s -s30+s -s33+s -s36+s -s38+s -n ya.ru -Qr -Q m=450:650 -f-1 -J 2 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -d4+s -s7+s -d10+s -s14+s -d18+s -n pool -Qr -Q m=450:650 -f-1 -J 3 -a1 -As -d2+s -s6+s -d10+s -J 3 -S -a1",
        // 2: V4b_ref (26/27 x2, stealth)
        "-H:\"google.com\" -d1+s -o2 -s5 -r5 -a1 -An -H:\"youtubei.googleapis.com\" -d1 -s1+s -s4+s -s7+s -s9+s -s12+s -s15+s -s17+s -s20+s -s24+s -s27+s -s30+s -s33+s -s36+s -s38+s -n ya.ru -Q o -f-1 -J 2 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -d4+s -s7+s -d10+s -s14+s -d18+s -n pool -Qr -Q m=450:650 -f-1 -J 3 -a1 -As -d2+s -s6+s -d10+s -J 3 -S -a1",
        // 3: C_ref (26/27)
        "-H:\"google.com\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -d4+s -s7+s -d10+s -s14+s -d18+s -n pool -Qr -Q m=450:650 -f-1 -J 3 -a1 -As -d2+s -s6+s -d10+s -J 3 -S -a1",
        // 4: Z1_ref (26/27 x2)
        "-H:\"google.com\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s1+s -s4+s -s7+s -s9+s -s12+s -s15+s -s17+s -s20+s -s24+s -s27+s -s30+s -s33+s -s36+s -s38+s -J 2 -a1 -As -d2+s -s6+s -d10+s -S -a1",
        // 5: V3a_yt_a (V3a family, youtubei group)
        "-H:\"ya.ru\" -d1+s -o2 -s5 -r5 -a1 -An -H:\"youtubei.googleapis.com\" -d1 -s3+s -d7+s -s11+s -s15+s -n ya.ru -Q o -f-1 -J 2 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -d4+s -s7+s -d10+s -s14+s -d18+s -n pool -Qr -Q m=450:650 -f-1 -J 3 -a1 -As -d2+s -s6+s -d10+s -S -a1",
        // 6: C_ya (C family)
        "-H:\"google.com\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -d4+s -s7+s -d10+s -s14+s -d18+s -n ya.ru -Q o -f-1 -J 2 -a1 -As -d2+s -s6+s -d10+s -J 3 -S -a1",
        // 7: C_msft (C family)
        "-H:\"google.com\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s1+s -s4+s -s7+s -s9+s -s12+s -s15+s -s17+s -s20+s -s24+s -s27+s -s30+s -s33+s -s36+s -s38+s -n microsoft.com -Qr -Q m=450:650 -f-1 -J 3 -a1 -As -d2+s -s6+s -d10+s -S -a1",
        // 8: V4b_yt_a (V4b family, youtubei group)
        "-H:\"google.com\" -s1+s -o2 -d4+s -r4 -a1 -An -H:\"youtubei.googleapis.com\" -d1 -s2+s -d5+s -s8+s -d12+s -s16+s -d20+s -s25+s -d29+s -s34+s -n ya.ru -Qr -Q m=450:650 -f-1 -J 2 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -d4+s -s7+s -d10+s -s14+s -d18+s -n pool -Qr -Q m=450:650 -f-1 -J 3 -a1 -As -s2+s -d5+s -s9+s -S -a1",
        // 9: Z1_msft (Z1 family)
        "-H:\"microsoft.com\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s2+s -d5+s -s8+s -d12+s -s16+s -d20+s -s25+s -d29+s -s34+s -n microsoft.com -Qr -Q m=450:650 -f-1 -J 3 -a1 -As -d1+s -s4+s -d8+s -J 2 -S -a1",
        // 10: C_light_a (C family, jitter 2)
        "-H:\"google.com\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s2+s -d5+s -s8+s -d12+s -s16+s -d20+s -s25+s -d29+s -s34+s -J 2 -a1 -As -s2+s -d5+s -s9+s -S -a1",
        // 11: Z1_hf_yt (Z1 family, youtubei group)
        "-H:\"ya.ru\" -d1+s -o2 -s5 -r5 -a1 -An -H:\"youtubei.googleapis.com\" -d1 -s1+s -s4+s -s7+s -s9+s -s12+s -s15+s -s17+s -s20+s -s24+s -s27+s -s30+s -s33+s -s36+s -s38+s -n huggingface.co -Qr -Q m=450:650 -f-1 -J 2 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s1+s -s4+s -s7+s -s9+s -s12+s -s15+s -s17+s -s20+s -s24+s -s27+s -s30+s -s33+s -s36+s -s38+s -n pool -Qr -Q m=450:650 -f-1 -J 3 -a1 -As -d2+s -s6+s -d10+s -J 3 -S -a1",
        // 12: V3a_yt_b (V3a family, youtubei group)
        "-H:\"google.com\" -s1+s -o2 -d4+s -r4 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s3+s -d7+s -s11+s -s15+s -n ya.ru -Q o -f-1 -J 2 -a1 -As -d2+s -s6+s -d10+s -S -a1",
        // 13: Z1_msft_b (Z1 family)
        "-H:\"ya.ru\" -o1 -d2+s -s6+s -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s5+s -d9+s -s13+s -d17+s -s21+s -d25+s -n microsoft.com -Qr -Q m=450:650 -f-1 -J 3 -a1 -As -s2+s -d5+s -s9+s -S -a1",
        // 14: V4b_yt_b (V4b family, youtubei group)
        "-H:\"google.com\" -d1+s -o2 -s5 -r5 -a1 -An -H:\"youtubei.googleapis.com\" -d1 -s1+s -s4+s -s7+s -s9+s -s12+s -s15+s -s17+s -s20+s -s24+s -s27+s -s30+s -s33+s -s36+s -s38+s -n pool -Qr -Q m=450:650 -f-1 -J 2 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -d4+s -s7+s -d10+s -s14+s -d18+s -n pool -Q o -f-1 -J 3 -a1 -As -d3+s -s7+s -S -a1",
        // 15: C_sni (C family, {sni} placeholder)
        "-H:\"ya.ru\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s2+s -d5+s -s8+s -d12+s -s16+s -d20+s -s25+s -d29+s -s34+s -n {sni} -Qr -f-1 -J 3 -a1 -As -d2+s -s6+s -d10+s -S -a1",
        // 16: Z1_light (Z1 family)
        "-H:\"google.com\" -s1+s -o2 -d4+s -r4 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s3+s -d7+s -s11+s -s15+s -J 2 -a1 -As -d3+s -s7+s -S -a1",
        // 17: Z1_yt_msft (Z1 family, youtubei group)
        "-H:\"ya.ru\" -o1 -d2+s -s6+s -a1 -An -H:\"youtubei.googleapis.com\" -d1 -s1+s -s4+s -s7+s -s9+s -s12+s -s15+s -s17+s -s20+s -s24+s -s27+s -s30+s -s33+s -s36+s -s38+s -n ya.ru -Q o -f-1 -J 2 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s2+s -d5+s -s8+s -d12+s -s16+s -d20+s -s25+s -d29+s -s34+s -n pool -Qr -Q m=450:650 -f-1 -J 3 -a1 -As -d1+s -s4+s -d8+s -J 2 -S -a1",
        // 18: C_light_b (C family, tlsrec chain)
        "-H:\"google.com\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1+s -s1:4:4+s -d14+s -n ya.ru -Q o -f-1 -J 2 -a1 -As -s2+s -d5+s -s9+s -S -a1",
        // 19: Z1_hf (Z1 family)
        "-H:\"ya.ru\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s5+s -d9+s -s13+s -d17+s -s21+s -d25+s -n huggingface.co -Qr -Q m=450:650 -f-1 -J 2 -a1 -As -d2+s -s6+s -d10+s -S -a1",
        // 20: V3b_yt_man (V3a family, youtubei+manifest group)
        "-H:\"google.com\" -d1+s -o2 -s5 -r5 -a1 -An -H:\"youtubei.googleapis.com manifest.googlevideo.com\" -d1 -s1+s -s4+s -s7+s -s9+s -s12+s -s15+s -s17+s -s20+s -s24+s -s27+s -s30+s -s33+s -s36+s -s38+s -n ya.ru -Q o -f-1 -J 2 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -d4+s -s7+s -d10+s -s14+s -d18+s -n microsoft.com -Qr -Q m=450:650 -f-1 -J 3 -a1 -As -s2+s -d5+s -s9+s -S -a1",
        // 21: Z1_msft_c (Z1 family)
        "-H:\"microsoft.com\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s5+s -d9+s -s13+s -d17+s -s21+s -d25+s -n ya.ru -Qr -Q m=450:650 -f-1 -J 3 -a1 -As -d1+s -s4+s -d8+s -J 2 -S -a1",
        // 22: C_sni_b (C family, {sni} placeholder)
        "-H:\"google.com\" -s1+s -o2 -d4+s -r4 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1+s -s1:4:4+s -d14+s -n {sni} -Qr -f-1 -J 3 -a1 -As -d2+s -s6+s -d10+s -S -a1",
        // 23: L1_hf (Light, 5 splits)
        "-H:\"ya.ru\" -o1 -d2+s -s6+s -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s3+s -d7+s -s11+s -s15+s -n huggingface.co -Q o -f-1 -J 2 -a1 -As -s2+s -d5+s -s9+s -S -a1",
        // 24: L2 (Light, 5 splits)
        "-H:\"ya.ru\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s3+s -d7+s -s11+s -s15+s -J 2 -a1 -As -d1+s -s4+s -d8+s -J 2 -S -a1",
        // 25: L3_ya (Light, 5 splits)
        "-H:\"google.com\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s5+s -d9+s -s13+s -d17+s -s21+s -d25+s -n ya.ru -Q o -f-1 -J 2 -a1 -As -d3+s -s7+s -S -a1",
        // 26: L4_pool (Light, 5 splits)
        "-H:\"microsoft.com\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s2+s -d5+s -s8+s -d12+s -s16+s -d20+s -s25+s -d29+s -s34+s -n pool -Q o -f-1 -J 3 -a1 -As -d2+s -s6+s -d10+s -S -a1",
        // 27: L5_hf (Light, 5 splits)
        "-H:\"google.com\" -s1+s -o2 -d4+s -r4 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s3+s -d7+s -s11+s -s15+s -n huggingface.co -Q o -f-1 -J 2 -a1 -As -d2+s -s6+s -d10+s -S -a1",
        // 28: L6_yt_msft (Light, youtubei group)
        "-H:\"ya.ru\" -d1+s -o2 -s5 -r5 -a1 -An -H:\"youtubei.googleapis.com\" -d1 -s1+s -s4+s -s7+s -s9+s -s12+s -s15+s -s17+s -s20+s -s24+s -s27+s -s30+s -s33+s -s36+s -s38+s -n microsoft.com -Q o -f-1 -J 2 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s1+s -s4+s -s7+s -s9+s -s12+s -s15+s -s17+s -s20+s -s24+s -s27+s -s30+s -s33+s -s36+s -s38+s -n ya.ru -Qr -Q m=450:650 -f-1 -J 2 -a1 -As -d3+s -s7+s -S -a1",
        // 29: L7_pool (Light, 5 splits)
        "-H:\"ya.ru\" -o1 -d2+s -s6+s -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -d4+s -s7+s -d10+s -s14+s -d18+s -n pool -Qr -Q m=450:650 -f-1 -J 2 -a1 -As -d2+s -s6+s -d10+s -J 3 -S -a1",
        // 30: L8 (Light, 5 splits)
        "-H:\"google.com\" -d1+s -o2 -s5 -r5 -a1 -An -o1 -r-5+se -a1 -At,r,s -d1 -s5+s -d9+s -s13+s -d17+s -s21+s -d25+s -J 2 -a1 -As -s2+s -d5+s -s9+s -S -a1",
    )

    /** Короткие имена для логов/уведомлений (индексы PACK). */
    val NAMES: List<String> = listOf(
        "V3a_ref",
        "V4b_ref",
        "C_ref",
        "Z1_ref",
        "V3a_yt_a",
        "C_ya",
        "C_msft",
        "V4b_yt_a",
        "Z1_msft",
        "C_light_a",
        "Z1_hf_yt",
        "V3a_yt_b",
        "Z1_msft_b",
        "V4b_yt_b",
        "C_sni",
        "Z1_light",
        "Z1_yt_msft",
        "C_light_b",
        "Z1_hf",
        "V3b_yt_man",
        "Z1_msft_c",
        "C_sni_b",
        "L1_hf",
        "L2",
        "L3_ya",
        "L4_pool",
        "L5_hf",
        "L6_yt_msft",
        "L7_pool",
        "L8",
    )

    /**
     * P6: ядро ротации V3a -> C -> Z1 -> V4b (паспорт §10 п.7, §4.6).
     * Индексы в PACK; используется AutopilotManager.beforeEngineStart
     * при byedpi_rotation_enabled (по умолчанию выключена — включать
     * после стабилизации автоподбора).
     */
    val ROTATION_CORE: List<Int> = listOf(0, 2, 3, 1)

    /**
     * Стелс-порядок при равных баллах: меньше индекс пака => короче
     * полотно / меньше фейков => менее аномальный отпечаток
     * рукопожатия (§4.6). Реализуется через строгий ">" в pickBest.
     */
    const val THRESHOLD = 24 // из 27; ниже — автоподбор «не уверен»
}
