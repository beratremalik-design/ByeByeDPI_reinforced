package io.github.romanvht.byedpi.utility

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import com.google.gson.Gson
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.DefaultStrategyPack
import io.github.romanvht.byedpi.data.StrategyCodes
import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.data.RESTART_ACTION
import io.github.romanvht.byedpi.services.NativeEngine
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService

/**
 * D2: автоподбор стратегии при запуске приложения.
 *
 * Поверх существующего движка автотеста (TestService): запускает прогон
 * источника "pack" (DefaultStrategyPack, 30 стратегий), после завершения
 * выбирает лучшую по числу пройденных доменов и молча применяет её.
 *
 * Правила применения (spec patches_D1_D2_spec.md §2.5):
 * - молча заменяется ТОЛЬКО дефолтная стратегия (-o1 -a1 -r-5+se) или
 *   предыдущая строка автоподбора — пользователь ничего не теряет;
 * - поверх собственной стратегии пользователя — только уведомление
 *   с кнопкой «Применить» (AutopilotReceiver);
 * - при равных баллах остаётся меньший индекс пака (стелс-порядок §4.6);
 * - порог уверенности: 24 из 27 доменов (DefaultStrategyPack.THRESHOLD);
 *   ниже — состояние unresolved и повтор не чаще раза в сутки;
 * - один прогон на кандидата — сознательное отклонение от §6.1 (два
 *   прогона): автоподбор — селектор, а не исследовательский протокол;
 *   выбранный кандидат далее проверяется суточным реальным тестом.
 */
object AutopilotManager {
    private const val TAG = "AutopilotManager"

    const val KEY_ENABLED = "byedpi_autopilot_enabled"
    const val KEY_STATE = "byedpi_autopilot_state"
    const val KEY_APPLIED = "byedpi_autopilot_applied_args"
    private const val KEY_ROTATION_ENABLED = "byedpi_rotation_enabled"
    private const val KEY_ROTATION_INDEX = "byedpi_rotation_index"

    /** Повтор неудачного автоподбора — не чаще раза в сутки. */
    private const val RERUN_COOLDOWN_MS = 24L * 60 * 60 * 1000

    private val gson = Gson()

    data class State(
        val packVersion: Int,
        val lastRunAt: Long,
        val bestIndex: Int = -1,
        val bestScore: Int = 0,
        val bestTotal: Int = 0,
        val unresolved: Boolean = false,
    )

    fun isEnabled(prefs: android.content.SharedPreferences): Boolean =
        prefs.getBoolean(KEY_ENABLED, true)

    fun getState(prefs: android.content.SharedPreferences): State? = try {
        prefs.getString(KEY_STATE, null)?.let { gson.fromJson(it, State::class.java) }
    } catch (_: Exception) {
        null
    }

    private fun saveState(prefs: android.content.SharedPreferences, state: State) {
        prefs.edit { putString(KEY_STATE, gson.toJson(state)) }
    }

    fun stateDescription(context: Context): String {
        val state = getState(context.getPreferences())
        return when {
            state == null -> context.getString(R.string.autopilot_state_none)
            state.unresolved -> context.getString(R.string.autopilot_state_unresolved)
            else -> context.getString(
                R.string.autopilot_state_ok,
                state.bestIndex + 1,
                StrategyCodes.codeFor(state.bestIndex),
                state.bestScore,
                state.bestTotal,
            )
        }
    }

    /**
     * Условия тихого запуска автоподбора при старте приложения:
     * включён, командный режим, движок и тест остановлены, пак
     * обновился либо прошлый прогон не нашёл кандидата (с cooldown).
     */
    fun maybeRunStartup(context: Context) {
        val prefs = context.getPreferences()
        if (!prefs.getBoolean(KEY_ENABLED, true)) return
        if (!prefs.getCmdEnable()) return
        if (TestService.isRunning) return
        // Синхронная проверка живого движка (статус мог не успеть восстановиться
        // после рестарта процесса): TestService остановит VPN через waitStop().
        if (NativeEngine.running(context) != null) return

        val state = getState(prefs)
        val stale = state == null || state.packVersion != DefaultStrategyPack.VERSION
        val cooldownPassed = state == null ||
            System.currentTimeMillis() - state.lastRunAt >= RERUN_COOLDOWN_MS
        if (!stale && !(state!!.unresolved && cooldownPassed)) return

        Log.i(TAG, "Starting autopilot (stale=$stale, state=$state)")
        Toast.makeText(context, R.string.autopilot_started, Toast.LENGTH_LONG).show()
        TestService.start(context, TestService.SOURCE_PACK)
    }

    /**
     * Выбор лучшей стратегии из результатов прогона пака.
     * Каждый домен = 1 запрос, балл = successCount; при равных баллах
     * побеждает меньший индекс (стелс-порядок пака, §4.6).
     * @return индекс в DefaultStrategyPack.PACK или null, если порог
     * не достигнут (24/27 пропорционально общему числу доменов).
     */
    fun pickBest(results: List<StrategyResult>): Int? {
        var best: Int? = null
        var bestScore = -1
        var total = 0
        results.forEachIndexed { index, r ->
            if (!r.isCompleted || r.totalRequests <= 0) return@forEachIndexed
            total = r.totalRequests
            if (r.successCount > bestScore) {
                bestScore = r.successCount
                best = index
            }
        }
        if (best == null || total <= 0) return null
        val ratio = DefaultStrategyPack.THRESHOLD.toDouble() / 27.0
        return if (bestScore.toDouble() / total >= ratio) best else null
    }

    /** Завершение прогона пака: сохранить состояние и применить лучшую. */
    fun onPackTestCompleted(context: Context, results: List<StrategyResult>) {
        val prefs = context.getPreferences()
        val best = pickBest(results)
        if (best == null) {
            Log.w(TAG, "Autopilot: no strategy reached the threshold, keeping current")
            saveState(prefs, State(
                packVersion = DefaultStrategyPack.VERSION,
                lastRunAt = System.currentTimeMillis(),
                unresolved = true,
            ))
            return
        }
        saveState(prefs, State(
            packVersion = DefaultStrategyPack.VERSION,
            lastRunAt = System.currentTimeMillis(),
            bestIndex = best,
            bestScore = results[best].successCount,
            bestTotal = results[best].totalRequests,
        ))
        apply(context, best)
    }

    /**
     * Применение стратегии пака: молча — только если сейчас стоит дефолт
     * или прошлая строка автоподбора; иначе — уведомление с кнопкой.
     */
    fun apply(context: Context, index: Int) {
        val prefs = context.getPreferences()
        val cmd = DefaultStrategyPack.PACK.getOrNull(index) ?: return
        val current = prefs.getCmdArgs()
        val previousApplied = prefs.getStringNotNull(KEY_APPLIED, "")
        val canSilentlyApply = current == DEFAULT_CMD_ARGS || current == previousApplied

        if (prefs.getCmdEnable() && canSilentlyApply) {
            prefs.edit {
                putString("byedpi_cmd_args", cmd)
                putString(KEY_APPLIED, cmd)
            }
            HistoryUtils(context).addCommand(cmd)
            Log.i(TAG, "Autopilot applied strategy ${StrategyCodes.codeFor(index)}")
        } else {
            Log.i(TAG, "Autopilot proposes strategy ${StrategyCodes.codeFor(index)}: current args belong to user")
            notifyProposal(context, cmd)
        }
    }

    private fun notifyProposal(context: Context, cmd: String) {
        try {
            // Top-level функция из utility/NotificationUtils.kt (тот же пакет).
            // В тексте уведомления — только код (A1..C10), U2.
            io.github.romanvht.byedpi.utility.showAutopilotProposal(
                context,
                StrategyCodes.displayFor(cmd),
                cmd
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show autopilot proposal", e)
        }
    }

    /**
     * Применение из уведомления (AutopilotReceiver): пользователь явно
     * согласился — перезаписываем и перезапускаем движок, если он был
     * запущен. Строка помечается как автоподбор-строка (KEY_APPLIED).
     */
    suspend fun applyFromProposal(context: Context, cmd: String) {
        val prefs = context.getPreferences()
        prefs.edit {
            putString("byedpi_cmd_args", cmd)
            putString(KEY_APPLIED, cmd)
        }
        HistoryUtils(context).addCommand(cmd)
        val mode = prefs.mode()
        ServiceManager.handleAction(context, RESTART_ACTION, mode)
    }

    /**
     * P6 (заготовка): ротация ядра V3a -> C -> Z1 -> V4b на каждом старте
     * движка (§4.6 — разные отпечатки рукопожатия в разные дни).
     * Выключена по умолчанию; включать после стабилизации автоподбора.
     * Меняется только автоподбор-строка или дефолт — своя стратегия
     * пользователя ротацией не затрагивается.
     */
    fun beforeEngineStart(context: Context) {
        val prefs = context.getPreferences()
        if (!prefs.getBoolean(KEY_ROTATION_ENABLED, false)) return
        if (!prefs.getCmdEnable()) return
        val current = prefs.getCmdArgs()
        val previousApplied = prefs.getStringNotNull(KEY_APPLIED, "")
        val ownsCurrent = current == DEFAULT_CMD_ARGS || current == previousApplied
        if (!ownsCurrent) return

        val index = prefs.getInt(KEY_ROTATION_INDEX, 0)
        val packIndex = DefaultStrategyPack.ROTATION_CORE[index % DefaultStrategyPack.ROTATION_CORE.size]
        val cmd = DefaultStrategyPack.PACK[packIndex]
        prefs.edit {
            putString("byedpi_cmd_args", cmd)
            putString(KEY_APPLIED, cmd)
            putInt(KEY_ROTATION_INDEX, index + 1)
        }
        Log.i(TAG, "Rotation: applied strategy #$packIndex (step $index)")
    }
}
