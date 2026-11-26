#!/usr/bin/env python3
"""U2b: generate DefaultStrategyPack.kt from the developer's reference list.

The reference file (30 strategies, leaders first) is NOT stored in this
repository - it lives only on the developer's machine. The generated
Kotlin stores the pack XOR-masked on top of Base64, so plain `strings`
or jadx text search over the APK finds no pack fragments. UI and reports
refer to pack entries by codes A1..C10 only (see StrategyCodes).

Usage:
  python3 scripts/gen_pack.py <reference_pack.txt> [--verify]

  reference_pack.txt  30 strategies, one per line, leaders first
  --verify            decode the generated blob and compare with the
                      reference (must be 30/30)

Note: the previous version of this script emitted Kotlin string literals
WITHOUT separating commas -> "Expecting ','" compile errors. Fixed here:
chunks are comma-separated; also added XOR keys echo check.
"""
import base64
import os
import re
import sys

# XOR-mask key constants (must stay in sync with the Kotlin decoder)
K1 = 0x1F3A5C7D
K2 = 0x2B6E9F41
K3 = 0x4C8D2E6A
K4 = 0x5A17E3B2


def key_byte(i: int) -> int:
    a = ((K1 & 0xFFFF) * (i + 17)) & 0xFFFF
    b = ((K2 & 0xFFFF) * i) & 0xFFFF
    c = (K3 >> ((i & 3) * 4)) & 0xFFFF
    d = (K4 >> (i & 7)) & 0xFF
    return (a ^ b ^ c ^ d) & 0xFF


def xor_mask(data: bytes) -> bytes:
    return bytes((b ^ key_byte(i)) & 0xFF for i, b in enumerate(data))


def chunk(s: str, n: int = 96) -> str:
    """Разбивка b64-блоба на строки-литералы, разделённые ЗАПЯТЫМИ."""
    parts = [f'"{s[i:i + n]}"' for i in range(0, len(s), n)]
    return ",\n        ".join(parts)


DST = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app/src/main/java/io/github/romanvht/byedpi/data/DefaultStrategyPack.kt")

TEMPLATE = '''package io.github.romanvht.byedpi.data

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
        {CHUNKS}
    ).joinToString("")

    // Константы XOR-маски (парные к scripts/gen_pack.py)
    private const val K1 = {K1_HEX}
    private const val K2 = {K2_HEX}
    private const val K3 = {K3_HEX}
    private const val K4 = {K4_HEX}

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
        val lines = String(out, Charsets.UTF_8).split('\\n')
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
'''


def build(reference_path: str) -> str:
    with open(reference_path, encoding="utf-8") as f:
        lines = [l.rstrip("\n") for l in f]
    lines = [l for l in lines if l.strip()]
    assert len(lines) == 30, f"expected 30 strategies, got {len(lines)}"
    data = ("\n".join(lines)).encode("utf-8")
    b64 = base64.b64encode(xor_mask(data)).decode("ascii")
    return (TEMPLATE
            .replace("{CHUNKS}", chunk(b64))
            .replace("{K1_HEX}", f"0x{K1:08X}")
            .replace("{K2_HEX}", f"0x{K2:08X}")
            .replace("{K3_HEX}", f"0x{K3:08X}")
            .replace("{K4_HEX}", f"0x{K4:08X}"))


def verify(kotlin_path: str, reference_path: str) -> bool:
    """Декодировать блоб из сгенерированного файла и сверить с эталоном."""
    src = open(kotlin_path, encoding="utf-8").read()
    body = src.split("private val B64 = listOf(", 1)[1].split(").joinToString", 1)[0]
    chunks = re.findall(r'"([A-Za-z0-9+/=]+)"', body)
    assert len(chunks) >= 2, "no chunks parsed - template broken"
    b64 = "".join(chunks)
    masked = base64.b64decode(b64)
    data = xor_mask(masked)
    decoded = data.decode("utf-8").split("\n")
    ref = [l.rstrip("\n") for l in open(reference_path, encoding="utf-8") if l.strip()]
    ok = decoded == ref
    print(f"verify: {sum(1 for a, b in zip(decoded, ref) if a == b)}/{len(ref)} строк совпало; "
          f"{'OK' if ok else 'FAIL'}")
    return ok


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("usage: gen_pack.py <reference_pack.txt> [--verify]")
        sys.exit(2)
    ref = sys.argv[1]
    content = build(ref)
    open(DST, "w", encoding="utf-8").write(content)
    print(f"записан {DST} ({len(content)} байт)")
    if "--verify" in sys.argv:
        sys.exit(0 if verify(DST, ref) else 1)
