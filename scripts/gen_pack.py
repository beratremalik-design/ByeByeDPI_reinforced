#!/usr/bin/env python3
"""Generate DefaultStrategyPack.kt from download/default_pack_30.txt verbatim.

Keeps the Kotlin pack byte-identical to the reference list (spec D2):
each line is escaped for a Kotlin string literal, comments carry the
family/score annotations from the spec.
"""
import sys

import os as _os
# Usage: python3 scripts/gen_pack.py [reference_pack.txt]
#   reference_pack.txt - 30 strategies, leaders first (see patches README)
SRC = sys.argv[1] if len(sys.argv) > 1 else "default_pack_30.txt"
DST = _os.path.join(_os.path.dirname(_os.path.dirname(_os.path.abspath(__file__))),
                    "app/src/main/java/io/github/romanvht/byedpi/data/DefaultStrategyPack.kt")

# Short names + notes (index -> (name, note)), spec v5.1/§2.1 of patches_D1_D2_spec.md
META = {
    0: ("V3a_ref", "27/27 x2"),
    1: ("V4b_ref", "26/27 x2, stealth"),
    2: ("C_ref", "26/27"),
    3: ("Z1_ref", "26/27 x2"),
    4: ("V3a_yt_a", "V3a family, youtubei group"),
    5: ("C_ya", "C family"),
    6: ("C_msft", "C family"),
    7: ("V4b_yt_a", "V4b family, youtubei group"),
    8: ("Z1_msft", "Z1 family"),
    9: ("C_light_a", "C family, jitter 2"),
    10: ("Z1_hf_yt", "Z1 family, youtubei group"),
    11: ("V3a_yt_b", "V3a family, youtubei group"),
    12: ("Z1_msft_b", "Z1 family"),
    13: ("V4b_yt_b", "V4b family, youtubei group"),
    14: ("C_sni", "C family, {sni} placeholder"),
    15: ("Z1_light", "Z1 family"),
    16: ("Z1_yt_msft", "Z1 family, youtubei group"),
    17: ("C_light_b", "C family, tlsrec chain"),
    18: ("Z1_hf", "Z1 family"),
    19: ("V3b_yt_man", "V3a family, youtubei+manifest group"),
    20: ("Z1_msft_c", "Z1 family"),
    21: ("C_sni_b", "C family, {sni} placeholder"),
    22: ("L1_hf", "Light, 5 splits"),
    23: ("L2", "Light, 5 splits"),
    24: ("L3_ya", "Light, 5 splits"),
    25: ("L4_pool", "Light, 5 splits"),
    26: ("L5_hf", "Light, 5 splits"),
    27: ("L6_yt_msft", "Light, youtubei group"),
    28: ("L7_pool", "Light, 5 splits"),
    29: ("L8", "Light, 5 splits"),
}


def k_escape(s: str) -> str:
    out = []
    for ch in s:
        if ch == '"':
            out.append('\\"')
        elif ch == "\\":
            out.append("\\\\")
        else:
            out.append(ch)
    return "".join(out)


def main() -> int:
    with open(SRC, encoding="utf-8") as f:
        lines = [ln.strip() for ln in f if ln.strip()]
    assert len(lines) == 30, f"expected 30 strategies, got {len(lines)}"

    body = []
    for i, ln in enumerate(lines):
        name, note = META[i]
        body.append(f"        // {i + 1}: {name} ({note})")
        body.append(f'        "{k_escape(ln)}",')
    pack_literal = "\n".join(body)

    content = f'''package io.github.romanvht.byedpi.data

/**
 * D2: встроенный пак из 30 стратегий по умолчанию (порядок = порядок
 * автоподбора, лидеры первыми). Синхронизируется дословно с эталоном
 * download/default_pack_30.txt (см. scripts/gen_pack.py), строки 1-4 —
 * референсы V3a/V4b/C/Z1 из паспорта v5.1 §5 и раунда 6.
 *
 * Правила паспорта §7 соблюдены: минимум 4 эшелона; в общем -An только
 * "-o1 -r-5+se -a1"; фейк-SNI только белый пул ядра / {{sni}}; позиции
 * полотна по возрастанию; тройка (полотно × усиление × добивка) не
 * повторяется в соседних строках — единственное задокументированное
 * исключение: строки 1-3 (референсы, исторически C — база V3a/V4b).
 *
 * Не показывается в UI; используется автоподбором (AutopilotManager)
 * и источником "pack" в настройках проверки.
 */
object DefaultStrategyPack {{
    /** Версия пака: при её изменении автоподбор перезапускается сам. */
    const val VERSION = 1

    /** 30 стратегий, порядок = порядок автоподбора (лидеры первыми). */
    val PACK: List<String> = listOf(
{pack_literal}
    )

    /** Короткие имена для логов/уведомлений (индексы PACK). */
    val NAMES: List<String> = listOf(
{chr(10).join('        "%s",' % name for _, (name, _) in META.items())}
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
}}
'''
    with open(DST, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"wrote {DST} ({len(lines)} strategies)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
