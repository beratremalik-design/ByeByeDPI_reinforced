package io.github.romanvht.byedpi.utility

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import io.github.romanvht.byedpi.BuildConfig
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.core.ByeDpiProxyUIPreferences
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.DefaultStrategyPack
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.PrivateDnsState
import io.github.romanvht.byedpi.data.StrategyCodes
import io.github.romanvht.byedpi.services.ByeDpiProxyService
import io.github.romanvht.byedpi.services.ByeDpiVpnService
import io.github.romanvht.byedpi.services.NativeEngine
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.services.appStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * D5: движок диагностики «в один клик».
 *
 * Собирает в один текстовый отчёт (язык отчёта — русский, решение №4;
 * строки отчёта лежат только в values/ и не переводятся):
 *  - заголовок окружения (сеть, DNS, версия, стратегия-код, QUIC-блок);
 *  - контрастный мини-тест «напрямую» (3 домена, мимо обхода);
 *  - чекер активных доменов через обход с задержками;
 *  - проба старта видео (6 доменов, QuickProbe.SITES);
 *  - тайминг DNS (системный резолв).
 *
 * Приватность: никаких IMEI/гео/SSID; стратегия пака — только код
 * A1..C10 (U2); «состояние плеера YouTube» заменяется прокси-метриками
 * старта видео + пользовательским примечанием «снято в момент лага».
 *
 * Сеть движок не покидает: все проверки — обычные HTTPS-запросы, как у
 * любого браузера; доставка отчёта v1 — буфер обмена/ACTION_SEND.
 */
object DiagnosticsEngine {
    private const val TAG = "DiagnosticsEngine"

    /** Домены контрастного теста «напрямую». */
    private val DIRECT_SITES = listOf(
        "youtube.com",
        "googleapis.com",
        "ya.ru",
    )

    /** Кэш-узлы googlevideo = хронические флейки (§2.4 паспорта). */
    private const val FLAKY_MARKER = "googlevideo.com"

    data class Result(
        val report: String,
        val summary: String,
    )

    suspend fun run(context: Context, onProgress: (String) -> Unit): Result =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val prefs = app.getPreferences()
            val sb = StringBuilder()

            // --- 0. Движок: текущий или временный Proxy ---
            onProgress.invoke(app.getString(R.string.diag_progress_engine))
            var tempEngine: NativeEngine? = null
            val (status, mode) = appStatus
            val engineAlive = status == AppStatus.Running || NativeEngine.running(app) != null
            val engineAliveNotTest = engineAlive && !TestService.isRunning
            try {
                if (!engineAliveNotTest) {
                    tempEngine = NativeEngine(app, Mode.Proxy, foreground = false)
                    tempEngine.start(NativeEngine.configuration(app))
                    delay(600)
                }
                val (ip, port) = prefs.getProxyIpAndPort()

                // --- 1. Заголовок окружения ---
                onProgress.invoke(app.getString(R.string.diag_progress_env))
                val cmdEnabled = prefs.getCmdEnable()
                val rawStrategy = if (cmdEnabled) prefs.getCmdArgs()
                else ByeDpiProxyUIPreferences(prefs).uiargs.drop(1).joinToString(" ")
                val strategyDisplay = StrategyCodes.displayFor(
                    rawStrategy,
                    prefs.getStringNotNull("byedpi_proxytest_sni", "ya.ru")
                )
                val strategySource = when {
                    prefs.getStringNotNull(AutopilotManager.KEY_APPLIED, "") == rawStrategy ->
                        app.getString(R.string.diag_source_autopilot)
                    StrategyCodes.displayFor(
                        rawStrategy,
                        prefs.getStringNotNull("byedpi_proxytest_sni", "ya.ru")
                    ) != rawStrategy ->
                        app.getString(R.string.diag_source_pack)
                    cmdEnabled -> app.getString(R.string.diag_source_user)
                    else -> app.getString(R.string.diag_source_ui)
                }

                val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.ENGLISH)
                val network = describeNetwork(app)
                val quicBlock = prefs.getBoolean("byedpi_block_quic", false)
                val applistType = prefs.getStringNotNull("applist_type", "disable")
                val selectedApps = prefs.getSelectedApps()
                val selfInWhitelist = applistType == "whitelist" && selectedApps.contains(app.packageName)
                val privateDns = PrivateDnsUtils.getState(app)
                val vpnLine = when {
                    tempEngine != null -> app.getString(R.string.diag_engine_temp)
                    mode == Mode.VPN -> app.getString(R.string.diag_engine_vpn)
                    else -> app.getString(R.string.diag_engine_proxy)
                } + uptimeSuffix(app)

                // Язык отчёта — русский (решение №4): заголовок и все строки
                // содержимого — из values/ без переводов в values-en.
                sb.appendLine("== ByeByeDPI Reinforced :: Диагностика ==")
                sb.appendLine(app.getString(R.string.diag_warning))
                sb.appendLine("${app.getString(R.string.diag_time)}: ${timeFmt.format(Date())}")
                sb.appendLine("${app.getString(R.string.diag_app)}: ${BuildConfig.VERSION_NAME}, ${app.getString(R.string.diag_pack_version)}: ${DefaultStrategyPack.VERSION}")
                sb.appendLine("${app.getString(R.string.diag_device)}: ${deviceName()}")
                sb.appendLine("${app.getString(R.string.diag_android)}: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                sb.appendLine("${app.getString(R.string.diag_network)}: $network")
                val provider = prefs.getStringNotNull("diagnostic_provider", "").trim()
                if (provider.isNotEmpty()) {
                    sb.appendLine("${app.getString(R.string.diag_provider)}: $provider (${app.getString(R.string.diag_provider_manual)})")
                }
                if (mode == Mode.VPN && privateDns !is PrivateDnsState.Configured) {
                    val dns = prefs.getStringNotNull("dns_ip", "1.1.1.1").ifBlank { "системный" }
                    sb.appendLine("DNS: $dns")
                }
                sb.appendLine("${app.getString(R.string.diag_private_dns)}: ${describePrivateDns(app, privateDns)}")
                sb.appendLine("$vpnLine; ${app.getString(R.string.diag_quic)}: ${onOff(app, quicBlock)}")
                sb.appendLine("${app.getString(R.string.diag_strategy)}: $strategyDisplay ($strategySource)")
                sb.appendLine("${app.getString(R.string.diag_autopilot)}: ${autopilotStateRu(app)}")
                val note = prefs.getStringNotNull("diagnostic_note", "").trim()
                if (note.isNotEmpty()) {
                    sb.appendLine("${app.getString(R.string.diag_note)}: $note")
                }

                // --- 2. Контрастный тест «напрямую» ---
                onProgress.invoke(app.getString(R.string.diag_progress_direct))
                sb.appendLine()
                sb.appendLine("— ${app.getString(R.string.diag_direct_title)} —")
                if (selfInWhitelist) {
                    sb.appendLine(app.getString(R.string.diag_direct_via_tunnel))
                }
                val direct = directTest()
                sb.appendLine(direct.joinToString(" | ") { "${it.site}: ${fmtDirect(app, it)}" })
                sb.appendLine("→ ${directVerdict(app, direct)}")

                // --- 3. Чекер активных доменов ---
                onProgress.invoke(app.getString(R.string.diag_progress_checker))
                DomainListUtils.syncLists(app)
                val sites = DomainListUtils.getActiveDomains(app).toList()
                val timeout = prefs.getLongStringNotNull("byedpi_proxytest_timeout", 5).coerceAtLeast(1)
                val checkerStarted = System.currentTimeMillis()
                val checker = SiteCheckUtils(ip, port.toInt())
                    .checkSitesDetailedAsync(
                        sites = sites,
                        requestsCount = 1,
                        requestTimeout = timeout,
                        concurrentRequests = 12,
                    )
                val checkerSeconds = (System.currentTimeMillis() - checkerStarted) / 1000.0
                val score = checker.sumOf { it.successCount }
                sb.appendLine()
                sb.appendLine("— ${app.getString(R.string.diag_checker_title, sites.size)} —")
                sb.appendLine("${app.getString(R.string.diag_score)}: $score/${sites.size}; ${app.getString(R.string.diag_median)}: ${median(checker.map { it.latencyMs })} ${app.getString(R.string.diag_ms)}; ${app.getString(R.string.diag_total_time)}: ${"%.1f".format(checkerSeconds)} ${app.getString(R.string.diag_seconds_short)}")
                val failures = checker.filter { it.successCount < it.totalCount }
                if (failures.isEmpty()) {
                    sb.appendLine(app.getString(R.string.diag_all_ok))
                } else {
                    sb.appendLine("${app.getString(R.string.diag_failures)}: " + failures.joinToString("; ") {
                        "${it.site} (${fmtLatency(app, it.latencyMs)})" +
                            if (it.site.contains(FLAKY_MARKER)) " [${app.getString(R.string.diag_flaky)}]" else ""
                    })
                    // таблица по всем доменам — для разбора с разработчиком
                    checker.forEach {
                        sb.appendLine("  ${it.site}: ${it.successCount}/${it.totalCount}, ${it.latencyMs} ${app.getString(R.string.diag_ms)}")
                    }
                }

                // --- 4. Проба старта видео (6 доменов) ---
                onProgress.invoke(app.getString(R.string.diag_progress_probe))
                val probe = SiteCheckUtils(ip, port.toInt())
                    .checkSitesDetailedAsync(
                        sites = QuickProbe.SITES,
                        requestsCount = 1,
                        requestTimeout = timeout,
                        concurrentRequests = QuickProbe.SITES.size,
                    )
                sb.appendLine()
                sb.appendLine("— ${app.getString(R.string.diag_probe_title)} —")
                sb.appendLine(probe.joinToString("; ") { "${it.site}: ${fmtCheck(app, it)}" })
                val youtubei = probe.firstOrNull { it.site == "youtubei.googleapis.com" }
                if (youtubei != null && youtubei.successCount == 0) {
                    sb.appendLine("→ ${app.getString(R.string.diag_probe_youtubei_hint)}")
                }

                // --- 5. DNS-тайминг ---
                onProgress.invoke(app.getString(R.string.diag_progress_dns))
                val dnsMedian = dnsTiming(DIRECT_SITES)
                sb.appendLine()
                sb.appendLine("— ${app.getString(R.string.diag_dns_title)} —")
                sb.appendLine("${app.getString(R.string.diag_dns_median)}: $dnsMedian ${app.getString(R.string.diag_ms)} (${app.getString(R.string.diag_dns_system)})")

                sb.appendLine()
                sb.appendLine(app.getString(R.string.diag_footer))

                val report = sb.toString()
                val summary = app.getString(
                    R.string.diag_summary_short,
                    score, sites.size,
                ) + if (youtubei != null) {
                    ", youtubei ${youtubei.successCount}/${youtubei.totalCount}"
                } else ""
                Result(report, summary)
            } finally {
                tempEngine?.let { engine ->
                    runCatching { engine.stop() }
                        .onFailure { Log.e(TAG, "Failed to stop temp engine", it) }
                }
            }
        }

    /**
     * U4/§10.7: «Полная диагностика пака» — ТОЛЬКО debug-сборка.
     * Автоматизирует исследовательский протокол §6 (каждая стратегия ×2,
     * пауза, рестарт движка). Пишет протокол в файл приложения; отчёт
     * пользовательской кнопкой не является и в общий UI не попадает.
     *
     * @return путь к файлу протокола
     */
    suspend fun runFullPack(context: Context, onProgress: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val prefs = app.getPreferences()
            val (ip, port) = prefs.getProxyIpAndPort()
            val sni = prefs.getStringNotNull("byedpi_proxytest_sni", "ya.ru")
            val timeout = prefs.getLongStringNotNull("byedpi_proxytest_timeout", 5).coerceAtLeast(1)
            DomainListUtils.syncLists(app)
            val sites = DomainListUtils.getActiveDomains(app).toList()

            val dir = File(app.filesDir, "diagnostics").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
            val file = File(dir, "pack_report_$stamp.txt")

            file.appendText("== ByeByeDPI Reinforced :: ${app.getString(R.string.diag_fullpack_title)} ==\n")
            file.appendText("${app.getString(R.string.diag_app)}: ${BuildConfig.VERSION_NAME}, ${app.getString(R.string.diag_pack_version)}: ${DefaultStrategyPack.VERSION}\n")
            file.appendText("${app.getString(R.string.diag_network)}: ${describeNetwork(app)}\n")
            file.appendText("${app.getString(R.string.diag_rules)}\n\n")

            val runs = 2
            for (pass in 1..runs) {
                file.appendText("— ${app.getString(R.string.diag_fullpack_pass, pass, runs)} —\n")
                DefaultStrategyPack.PACK.forEachIndexed { index, cmd ->
                    onProgress.invoke(
                        app.getString(R.string.diag_fullpack_progress, pass, runs, StrategyCodes.codeFor(index))
                    )
                    val line = StringBuilder(StrategyCodes.codeFor(index) + ": ")
                    var engine: NativeEngine? = null
                    try {
                        val command = cmd.replace("{sni}", "\"$sni\"")
                        val configuration = testConfiguration(app, command, ip, port.toInt())
                        engine = NativeEngine(app, Mode.Proxy, foreground = false)
                        engine.start(configuration)
                        delay(600)
                        val started = System.currentTimeMillis()
                        val results = SiteCheckUtils(ip, port.toInt())
                            .checkSitesDetailedAsync(
                                sites = sites,
                                requestsCount = 1,
                                requestTimeout = timeout,
                                concurrentRequests = 12,
                            )
                        val score = results.sumOf { it.successCount }
                        val seconds = (System.currentTimeMillis() - started) / 1000.0
                        val medianMs = median(results.map { it.latencyMs })
                        line.append("$score/${sites.size}, ${app.getString(R.string.diag_median)} $medianMs ${app.getString(R.string.diag_ms)}, ${"%.1f".format(seconds)} ${app.getString(R.string.diag_seconds_short)}")
                        val failed = results.filter { it.successCount < it.totalCount }
                        if (failed.isNotEmpty()) {
                            line.append(" | ${app.getString(R.string.diag_failures)}: " +
                                failed.joinToString("; ") { it.site })
                        }
                    } catch (e: Exception) {
                        line.append("ERROR: ${e.message}")
                        Log.e(TAG, "Full pack run failed at index $index", e)
                    } finally {
                        engine?.let { current ->
                            runCatching { current.stop() }
                                .onFailure { Log.e(TAG, "Failed to stop engine", it) }
                        }
                    }
                    // appendText открывает/закрывает файл — буфер сбрасывается сам
                    file.appendText(line.toString() + "\n")
                    delay(1500) // пауза между кандидатами (§6)
                }
                file.appendText("\n")
            }
            file.absolutePath
        }

    // ---------- вспомогательные ----------

    /** Прямой результат контрастного теста (мимо SOCKS). */
    private data class DirectCheck(val site: String, val ok: Boolean, val latencyMs: Long)

    /**
     * Строка о состоянии автоподбора на русском (язык отчёта фиксирован,
     * решение №4) — независимо от локали устройства.
     */
    private fun autopilotStateRu(context: Context): String {
        val state = AutopilotManager.getState(context.getPreferences())
        return when {
            state == null -> "не запускался"
            state.unresolved -> "кандидат не найден (порог не достигнут)"
            else -> "стратегия ${StrategyCodes.codeFor(state.bestIndex)}, балл ${state.bestScore}/${state.bestTotal}"
        }
    }

    private suspend fun directTest(): List<DirectCheck> = coroutineScope {
        DIRECT_SITES.map { site ->
            async(Dispatchers.IO) {
                val started = System.currentTimeMillis()
                var connection: HttpURLConnection? = null
                val ok = try {
                    val url = URL("https://$site")
                    connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.setRequestProperty("Connection", "close")
                    connection.responseCode in 200..399
                } catch (_: Exception) {
                    false
                } finally {
                    connection?.disconnect()
                }
                DirectCheck(site, ok, System.currentTimeMillis() - started)
            }
        }.awaitAll()
    }

    private suspend fun dnsTiming(sites: List<String>): Long = withContext(Dispatchers.IO) {
        val times = mutableListOf<Long>()
        sites.forEach { site ->
            val started = System.currentTimeMillis()
            try {
                InetAddress.getAllByName(site)
            } catch (_: Exception) {
                // сбой резолва тоже фиксируется по времени
            }
            times.add(System.currentTimeMillis() - started)
        }
        median(times)
    }

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun describeNetwork(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        return when {
            caps == null -> context.getString(R.string.diag_network_unknown)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                context.getString(R.string.diag_network_wifi)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val operator = tm?.networkOperatorName?.takeIf { it.isNotBlank() }
                if (operator != null) {
                    context.getString(R.string.diag_network_cellular_op, operator)
                } else {
                    context.getString(R.string.diag_network_cellular)
                }
            }
            else -> context.getString(R.string.diag_network_other)
        }
    }

    private fun describePrivateDns(context: Context, state: PrivateDnsState): String = when (state) {
        is PrivateDnsState.Configured -> state.hostname
        PrivateDnsState.Inactive -> context.getString(R.string.diag_private_dns_off)
        PrivateDnsState.Unknown -> context.getString(R.string.diag_private_dns_unknown)
        PrivateDnsState.Unsupported -> context.getString(R.string.diag_private_dns_unsupported)
    }

    private fun uptimeSuffix(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return ""
        val services = try {
            am.getRunningServices(Int.MAX_VALUE)
        } catch (_: Exception) {
            null
        } ?: return ""
        val target = setOf(ByeDpiVpnService::class.java.name, ByeDpiProxyService::class.java.name)
        val service = services.firstOrNull { it.service.className in target && it.activeSince > 0 }
            ?: return ""
        val uptimeSec = (System.currentTimeMillis() - service.activeSince) / 1000
        if (uptimeSec <= 0) return ""
        return String.format(Locale.ENGLISH, ", %s %02d:%02d",
            context.getString(R.string.diag_uptime), uptimeSec / 60, uptimeSec % 60)
    }

    private fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .joinToString(" ")

    /** Строка состояния для результатов через SOCKS (SiteCheckResult). */
    private fun fmtCheck(context: Context, r: SiteCheckResult): String =
        if (r.successCount == r.totalCount) "OK (${fmtLatency(context, r.latencyMs)})"
        else "FAIL (${fmtLatency(context, r.latencyMs)})"

    /** Строка состояния для контрастного теста (DirectCheck) — D5. */
    private fun fmtDirect(context: Context, r: DirectCheck): String =
        if (r.ok) "OK (${fmtLatency(context, r.latencyMs)})"
        else "FAIL (${fmtLatency(context, r.latencyMs)})"

    private fun fmtLatency(context: Context, ms: Long): String =
        "$ms ${context.getString(R.string.diag_ms)}"

    private fun onOff(context: Context, value: Boolean): String =
        context.getString(if (value) R.string.diag_on else R.string.diag_off)

    private fun directVerdict(context: Context, results: List<DirectCheck>): String {
        val blocked = results.filter { !it.ok }
        return when {
            blocked.isEmpty() -> context.getString(R.string.diag_verdict_no_block)
            blocked.size == results.size -> context.getString(R.string.diag_verdict_all_blocked)
            results.first { it.site == "youtube.com" }.let { !it.ok } ->
                context.getString(R.string.diag_verdict_selective)
            else -> context.getString(R.string.diag_verdict_partial)
        }
    }
}
