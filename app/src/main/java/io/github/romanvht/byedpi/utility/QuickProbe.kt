package io.github.romanvht.byedpi.utility

import android.content.Context
import android.util.Log
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.SiteResult
import io.github.romanvht.byedpi.services.NativeEngine
import io.github.romanvht.byedpi.services.appStatus
import kotlinx.coroutines.delay

/**
 * D3: быстрая проба старта видео/шортса — 6 доменов, достаточных для
 * старта ролика, 1 запрос на домен (~5-15 с вместо полного чекера).
 *
 * Цель — дёшево коррелировать «лагает» с состоянием youtubei/manifest
 * в момент лага:
 * - 6/6 — старт должен проходить без ожиданий;
 * - youtubei.googleapis.com 0/1 — ожидаемы подвисания именно в начале
 *   роликов (плеер не получает ответ API); совпадает с накопленной
 *   статистикой youtubei 6/53;
 * - rr1---sn-xguxaxjvh-gufl (gufl) 0/1 — хронический флейк, шум (§2.4),
 *   не трактовать как деградацию.
 *
 * Если движок запущен (VPN/Proxy) — проба идёт через него (безопасно
 * коррелировать с моментом лага); иначе на время пробы поднимается
 * временный Proxy-движок с текущей стратегией.
 */
object QuickProbe {
    private const val TAG = "QuickProbe"

    val SITES: List<String> = listOf(
        "youtubei.googleapis.com",
        "jnn-pa.googleapis.com",
        "manifest.googlevideo.com",
        "signaler-pa.youtube.com",
        "youtube.com",
        "rr1---sn-xguxaxjvh-gufl.googlevideo.com",
    )

    suspend fun run(context: Context): List<SiteResult> {
        val app = context.applicationContext
        val (ip, port) = app.getPreferences().getProxyIpAndPort()
        var engine: NativeEngine? = null
        try {
            val engineAlive = appStatus.first == AppStatus.Running ||
                NativeEngine.running(app) != null
            if (!engineAlive) {
                engine = NativeEngine(app, Mode.Proxy, foreground = false)
                engine.start(NativeEngine.configuration(app))
                delay(600)
            }
            val results = SiteCheckUtils(ip, port.toInt()).checkSitesAsync(
                sites = SITES,
                requestsCount = 1,
                requestTimeout = 5,
                concurrentRequests = SITES.size,
                fullLog = false,
            )
            Log.i(TAG, "Quick probe: ${results.sumOf { it.second }}/${SITES.size}")
            return results.map { SiteResult(it.first, it.second, 1) }
        } finally {
            engine?.let { current ->
                runCatching { current.stop() }
                    .onFailure { Log.e(TAG, "Failed to stop probe engine", it) }
            }
        }
    }
}
