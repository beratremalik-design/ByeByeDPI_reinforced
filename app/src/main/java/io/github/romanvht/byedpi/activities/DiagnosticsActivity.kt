package io.github.romanvht.byedpi.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import io.github.romanvht.byedpi.BuildConfig
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.utility.ClipboardUtils
import io.github.romanvht.byedpi.utility.DiagnosticsEngine
import io.github.romanvht.byedpi.utility.getStringNotNull
import io.github.romanvht.byedpi.utility.getPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * U4/D5: экран «Диагностика» — живёт в настройках (кнопка не на главном
 * окне). Формирует отчёт локально; сетевых отправок нет — доставка v1 =
 * [Скопировать] / [Поделиться] (ACTION_SEND), нулевой сетевой след.
 *
 * Ручные поля «провайдер» и «примечание» сохраняются в настройки и
 * подставляются в отчёт.
 *
 * «Полный прогон пака ×2» (автоматизация исследовательского протокола) —
 * только в debug-сборке (BuildConfig.DEBUG).
 */
class DiagnosticsActivity : BaseActivity() {

    private lateinit var providerInput: TextInputEditText
    private lateinit var noteInput: TextInputEditText
    private lateinit var startButton: MaterialButton
    private lateinit var fullPackButton: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var statusView: MaterialTextView
    private lateinit var resultView: MaterialTextView
    private lateinit var buttonsRow: View

    private var runningJob: Job? = null
    private var lastReport: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        setupToolbar()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        providerInput = findViewById(R.id.diagProvider)
        noteInput = findViewById(R.id.diagNote)
        startButton = findViewById(R.id.diagStart)
        fullPackButton = findViewById(R.id.diagFullPack)
        progress = findViewById(R.id.diagProgress)
        statusView = findViewById(R.id.diagStatus)
        resultView = findViewById(R.id.diagResult)
        buttonsRow = findViewById(R.id.diagButtonsRow)

        val prefs = getPreferences()
        providerInput.setText(prefs.getStringNotNull("diagnostic_provider", ""))
        noteInput.setText(prefs.getStringNotNull("diagnostic_note", ""))

        if (BuildConfig.DEBUG) fullPackButton.visibility = View.VISIBLE

        startButton.setOnClickListener {
            if (TestService.isRunning) {
                Toast.makeText(this, R.string.test_unavailable, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveOptionalFields()
            runDiagnostics()
        }

        fullPackButton.setOnClickListener {
            if (TestService.isRunning) {
                Toast.makeText(this, R.string.test_unavailable, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveOptionalFields()
            runFullPack()
        }

        findViewById<MaterialButton>(R.id.diagCopy).setOnClickListener {
            lastReport?.let { report ->
                ClipboardUtils.copy(this, report, getString(R.string.diagnostics))
                Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<MaterialButton>(R.id.diagShare).setOnClickListener {
            lastReport?.let { report ->
                // v1: системный шеринг (Telegram/почта/любой канал) —
                // приложение само ничего не отправляет и никуда не ходит.
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, report)
                startActivity(Intent.createChooser(send, getString(R.string.diag_share)))
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        runningJob?.cancel()
        super.onDestroy()
    }

    private fun saveOptionalFields() {
        val prefs = getPreferences()
        prefs.edit {
            putString("diagnostic_provider", providerInput.text?.toString()?.trim() ?: "")
            putString("diagnostic_note", noteInput.text?.toString()?.trim() ?: "")
        }
    }

    private fun runDiagnostics() {
        if (runningJob?.isActive == true) return
        setBusy(true, getString(R.string.diag_running))
        runningJob = lifecycleScope.launch {
            try {
                val result = DiagnosticsEngine.run(this@DiagnosticsActivity) { stage ->
                    runOnUiThread { statusView.text = stage }
                }
                showResult(result.report)
            } catch (e: Exception) {
                setBusy(false, getString(R.string.diag_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun runFullPack() {
        if (runningJob?.isActive == true) return
        setBusy(true, getString(R.string.diag_fullpack_running))
        runningJob = lifecycleScope.launch {
            try {
                val path = DiagnosticsEngine.runFullPack(this@DiagnosticsActivity) { stage ->
                    runOnUiThread { statusView.text = stage }
                }
                setBusy(false, getString(R.string.diag_fullpack_done, path))
                Toast.makeText(this@DiagnosticsActivity, R.string.diag_fullpack_saved, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                setBusy(false, getString(R.string.diag_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun showResult(report: String) {
        lastReport = report
        setBusy(false, getString(R.string.diag_done))
        resultView.text = report
        resultView.visibility = View.VISIBLE
        buttonsRow.visibility = View.VISIBLE
    }

    private fun setBusy(busy: Boolean, statusText: String?) {
        startButton.isEnabled = !busy
        fullPackButton.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        statusView.text = statusText
        if (busy) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
