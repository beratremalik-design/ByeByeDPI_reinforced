package io.github.romanvht.byedpi.activities

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.*
import io.github.romanvht.byedpi.databinding.ActivityMainBinding
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var historyUtils: HistoryUtils

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName
        private const val BATTERY_OPTIMIZATION_REQUESTED = "battery_optimization_requested"

        private fun collectLogs(): String? {
            return try {
                val process = Runtime.getRuntime().exec("logcat *:D -d")
                process.inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to collect logs", exception)
                null
            }
        }
    }

    private val vpnRegister =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                ServiceManager.start(this, Mode.VPN)
            } else {
                Toast.makeText(this, R.string.vpn_permission_denied, Toast.LENGTH_SHORT).show()
                updateStatus()
            }
        }

    private val logsRegister = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ::handleLogsFileResult
    )

    private fun handleLogsFileResult(result: ActivityResult) {
        if (result.resultCode != RESULT_OK) return
        val data = result.data ?: return
        val uri = data.data
        val path = data.getStringExtra(FileActivity.EXTRA_PATH)
        val file = if (path == null) null else File(path)
        if (uri == null && file == null) return

        lifecycleScope.launch(Dispatchers.IO) {
            val logs = collectLogs()
            if (logs == null) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, R.string.logs_failed, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                val outputStream = when {
                    uri != null -> contentResolver.openOutputStream(uri)
                    file != null -> file.outputStream()
                    else -> null
                }
                if (outputStream == null) {
                    Log.e(TAG, "Failed to open output stream")
                } else {
                    outputStream.use { stream ->
                        stream.write(logs.toByteArray())
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to save logs", exception)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received intent: ${intent?.action}")

            if (intent == null) {
                Log.w(TAG, "Received null intent")
                return
            }

            val senderOrd = intent.getIntExtra(SENDER, -1)
            val sender = Sender.entries.getOrNull(senderOrd)
            if (sender == null) {
                Log.w(TAG, "Received intent with unknown sender: $senderOrd")
                return
            }

            when (val action = intent.action) {
                STARTED_BROADCAST,
                STOPPED_BROADCAST -> updateStatus()

                FAILED_BROADCAST -> {
                    Toast.makeText(
                        context,
                        getString(R.string.failed_to_start, sender.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                    updateStatus()
                }

                else -> Log.w(TAG, "Unknown action: $action")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceManager.refresh(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()

        historyUtils = HistoryUtils(this)

        val intentFilter = IntentFilter().apply {
            addAction(STARTED_BROADCAST)
            addAction(STOPPED_BROADCAST)
            addAction(FAILED_BROADCAST)
        }

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, intentFilter)
        }

        binding.statusButtonCard.setOnClickListener {
            binding.statusButtonCard.isClickable = false

            val (status, _) = appStatus
            when (status) {
                AppStatus.Halted -> start()
                AppStatus.Running -> stop()
            }

            binding.statusButtonCard.postDelayed({
                binding.statusButtonCard.isClickable = true
            }, 1000)
        }

        binding.statusButtonCard.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.statusButtonCard.strokeWidth = 10
                binding.statusButtonCard.strokeColor = android.graphics.Color.argb(100, 0, 0, 0)
            } else {
                binding.statusButtonCard.strokeWidth = 0
            }
        }

        binding.settingsButton.setOnClickListener {
            if (TestService.isRunning) {
                Toast.makeText(this, R.string.test_unavailable, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (appStatus.first == AppStatus.Running) {
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        binding.editorButton.setOnClickListener {
            val useCmdSettings = getPreferences().getCmdEnable()

            if (!useCmdSettings && appStatus.first == AppStatus.Running) {
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, SettingsActivity::class.java)
            intent.putExtra("open_fragment", if (useCmdSettings) "cmd" else "ui")
            startActivity(intent)
        }

        binding.testProxyButton.setOnClickListener {
            startActivity(Intent(this, TestActivity::class.java))
        }

        binding.domainListsButton.setOnClickListener {
            val intent = Intent(this, TestSettingsActivity::class.java)
            intent.putExtra("open_fragment", "domain_lists")
            startActivity(intent)
        }

        binding.strategyButton.setOnClickListener {
            showStrategyPicker()
        }

        if (!PermissionUtils.hasNotificationPermission(this)) {
            PermissionUtils.requestNotificationPermission(this, 1)
        } else {
            requestBatteryOptimization()
        }

        migrateDefaults()
        migrateFakeSni()

        // D2: тихий автоподбор стратегии из встроенного пака при запуске.
        // Небольшая задержка — дать ServiceManager.refresh восстановить статус.
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1200)
            try {
                AutopilotManager.maybeRunStartup(this@MainActivity)
            } catch (e: Exception) {
                Log.e(TAG, "Autopilot startup failed", e)
            }
        }

        if (!TestService.isRunning && getPreferences().getBoolean("auto_connect", false) &&
            appStatus.first != AppStatus.Running) {
            this.start()
        }

        ShortcutUtils.update(this)
    }

    override fun onResume() {
        super.onResume()
        ServiceManager.refresh(this)
        updateStatus()
        updateStrategyButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) {
            requestBatteryOptimization()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save_logs -> {
                val intent = Intent(this, FileActivity::class.java)
                intent.putExtra(FileActivity.EXTRA_MODE, FileActivity.MODE_CREATE)
                intent.putExtra(FileActivity.EXTRA_TYPE, FileActivity.TYPE_LOGS)
                logsRegister.launch(intent)
                true
            }

            R.id.action_close_app -> {
                TestService.stop()
                lifecycleScope.launch {
                    try {
                        TestService.awaitStopped()
                        ServiceManager.waitStop()
                    } catch (exception: Exception) {
                        Log.e(TAG, "Failed to stop service before closing", exception)
                        return@launch
                    }
                    finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(0)
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun start() {
        if (TestService.isRunning) {
            Toast.makeText(this, R.string.test_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        when (getPreferences().mode()) {
            Mode.VPN -> {
                val intentPrepare = VpnService.prepare(this)
                if (intentPrepare != null) {
                    vpnRegister.launch(intentPrepare)
                } else {
                    ServiceManager.start(this, Mode.VPN)
                }
            }

            Mode.Proxy -> ServiceManager.start(this, Mode.Proxy)
        }
    }

    private fun stop() {
        ServiceManager.stop()
    }

    private fun updateStatus() {
        val (status, mode) = appStatus

        Log.i(TAG, "Updating status: $status, $mode")

        val preferences = getPreferences()
        val (ip, port) = preferences.getProxyIpAndPort()

        binding.proxyAddress.text = getString(R.string.proxy_address, ip, port)

        when (status) {
            AppStatus.Halted -> {
                val typedValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.colorPrimary, typedValue,true)
                binding.statusButtonCard.setCardBackgroundColor(typedValue.data)
                binding.statusButtonIcon.clearColorFilter()

                when (preferences.mode()) {
                    Mode.VPN -> {
                        binding.statusText.setText(R.string.vpn_disconnected)
                    }

                    Mode.Proxy -> {
                        binding.statusText.setText(R.string.proxy_down)
                    }
                }
            }

            AppStatus.Running -> {
                binding.statusButtonCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.green_active))
                binding.statusButtonIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.white))

                when (mode) {
                    Mode.VPN -> {
                        binding.statusText.setText(R.string.vpn_connected)
                    }

                    Mode.Proxy -> {
                        binding.statusText.setText(R.string.proxy_up)
                    }
                }
            }
        }
    }

    private fun updateStrategyButton() {
        val useCmdSettings = getPreferences().getCmdEnable()

        if (!useCmdSettings) {
            binding.cmdButtonsRow.visibility = View.GONE
            binding.strategyButton.visibility = View.GONE
            return
        }

        binding.cmdButtonsRow.visibility = View.VISIBLE

        val pinned = historyUtils.getPinnedHistory()
        val currentCmdArgs = getPreferences().getCmdArgs()

        val matched = pinned.find { it.text == currentCmdArgs }
        val name = matched?.name?.takeIf { it.isNotBlank() }

        if (name != null) {
            binding.strategyButtonName.text = name
            binding.strategyButtonName.visibility = View.VISIBLE
            binding.strategyButtonText.maxLines = 1
        } else {
            binding.strategyButtonName.visibility = View.GONE
            binding.strategyButtonText.maxLines = 2
        }

        // U2: стратегия пака — только код (A1..C10); своя строка
        // пользователя показывается как есть (осознанный ввод).
        binding.strategyButtonText.text = StrategyCodes
            .displayFor(currentCmdArgs, testSni())
            .ifBlank { getString(R.string.main_strategy_picker) }
        binding.strategyButton.visibility = View.VISIBLE
    }

    /** SNI автотеста — нужен для сопоставления строк пака с {sni}. */
    private fun testSni(): String =
        getPreferences().getStringNotNull("byedpi_proxytest_sni", "ya.ru")

    private fun showStrategyPicker() {
        val pinned = historyUtils.getPinnedHistory()

        if (pinned.isEmpty()) {
            Toast.makeText(this, R.string.main_strategy_no_pinned, Toast.LENGTH_SHORT).show()
            return
        }

        val adapter = object : ArrayAdapter<Command>(
            this,
            R.layout.item_main_strategy,
            pinned
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(
                    R.layout.item_main_strategy,
                    parent,
                    false
                )

                val command = getItem(position)!!

                val nameView = view.findViewById<TextView>(R.id.strategyName)
                val textView = view.findViewById<TextView>(R.id.strategyText)
                val dividerView = view.findViewById<View>(R.id.strategyDivider)

                val name = command.name?.takeIf { it.isNotBlank() }

                if (name != null) {
                    nameView.visibility = View.VISIBLE
                    nameView.text = name
                    textView.maxLines = 2
                } else {
                    nameView.visibility = View.GONE
                    textView.maxLines = 3
                }

                // U2: стратегии пака — только коды A1..C10, не сырая строка
                textView.text = StrategyCodes.displayFor(command.text, testSni())
                dividerView.visibility = if (position == count - 1) View.GONE else View.VISIBLE

                return view
            }
        }

        val listView = ListView(this).apply {
            divider = null
            setPadding(0, (10 * resources.displayMetrics.density).toInt(), 0, 0)
            this.adapter = adapter
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.main_strategy_picker))
            .setView(listView)
            .setNegativeButton(getString(android.R.string.cancel), null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            applyStrategy(pinned[position].text)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applyStrategy(commandText: String) {
        getPreferences().edit { putString("byedpi_cmd_args", commandText) }

        updateStrategyButton()

        if (appStatus.first == AppStatus.Running) {
            val mode = getPreferences().mode()
            if (mode == Mode.VPN && VpnService.prepare(this) != null) {
                return
            }

            ServiceManager.restart(this, mode)
            Toast.makeText(this, R.string.service_restart, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestBatteryOptimization() {
        val preferences = getPreferences()
        val alreadyRequested = preferences.getBoolean(BATTERY_OPTIMIZATION_REQUESTED, false)

        if (!alreadyRequested && !PermissionUtils.isBatteryOptimizationDisabled(this)) {
            PermissionUtils.requestBatteryOptimization(this)
            preferences.edit { putBoolean(BATTERY_OPTIMIZATION_REQUESTED, true) }
        }
    }

    /**
     * U3: дефолты свежей установки (принцип D1/D4 — существующие
     * установки не трогаем). Признак существующей установки — флаг
     * миграции SNI (D4): он пишется при первом запуске уже работавшей
     * версии. Нет флага — свежая установка: применяем белый список
     * (YouTube + YouTube Music) и автоподключение при открытии.
     * Явно заданные пользователем значения не перезаписываются.
     */
    private fun migrateDefaults() {
        val preferences = getPreferences()
        if (preferences.getBoolean("defaults_v3_applied", false)) return

        val isFreshInstall = !preferences.getBoolean("byedpi_sni_migrated_v1", false)
        if (isFreshInstall) {
            if (!preferences.contains("applist_type") && !preferences.contains("selected_apps")) {
                preferences.edit {
                    putString("applist_type", "whitelist")
                    putStringSet(
                        "selected_apps",
                        setOf(
                            "com.google.android.youtube",
                            "com.google.android.apps.youtube.music"
                        )
                    )
                }
            }
            if (!preferences.contains("auto_connect")) {
                preferences.edit { putBoolean("auto_connect", true) }
            }
        }
        preferences.edit { putBoolean("defaults_v3_applied", true) }
    }

    /**
     * D4: замена дефолтного SNI автотеста/фейков с google.com на ya.ru
     * (см. patches_D1_D2_spec.md §4: ya.ru дал 3 прохода из 6 на youtubei,
     * google.com — 0 из 2; цена замены нулевая, SNI белый для ядра).
     * Явно изменённое пользователем значение (не null и не google.com) не трогаем.
     */
    private fun migrateFakeSni() {
        val preferences = getPreferences()
        if (preferences.getBoolean("byedpi_sni_migrated_v1", false)) return

        val current = preferences.getString("byedpi_proxytest_sni", null)
        if (current == null || current == "google.com") {
            preferences.edit { putString("byedpi_proxytest_sni", "ya.ru") }
        }
        val uiSni = preferences.getString("byedpi_fake_sni", null)
        if (uiSni == null || uiSni == "www.iana.org") {
            preferences.edit { putString("byedpi_fake_sni", "ya.ru") }
        }
        preferences.edit { putBoolean("byedpi_sni_migrated_v1", true) }
    }
}
