package io.github.romanvht.byedpi.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.preference.*
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.utility.AutopilotManager
import io.github.romanvht.byedpi.utility.*

class ProxyTestSettingsFragment : PreferenceFragmentCompat() {

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updatePreferences()
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.proxy_test_settings, rootKey)

        setupNumberSummary("byedpi_proxytest_delay", R.string.proxytest_delay_desc)
        setupNumberSummary("byedpi_proxytest_requests", R.string.proxytest_requests_desc)
        setupNumberSummary("byedpi_proxytest_limit", R.string.proxytest_limit_desc)
        setupNumberSummary("byedpi_proxytest_timeout", R.string.proxytest_timeout_desc)

        setEditTestPreferenceListenerInt("byedpi_proxytest_delay", 0, 10)
        setEditTestPreferenceListenerInt("byedpi_proxytest_requests", 1, 20)
        setEditTestPreferenceListenerInt("byedpi_proxytest_timeout", 1, 15)
        setEditTestPreferenceListenerInt("byedpi_proxytest_limit", 1, 50)

        setEditTestPreferenceListenerDomain("byedpi_proxytest_sni")

        setupAutopilot()

        updatePreferences()
    }

    override fun onResume() {
        super.onResume()
        updatePreferences()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun updatePreferences() {
        val switchUserCommands = findPreferenceNotNull<SwitchPreference>("byedpi_proxytest_usercommands")
        val textUserCommands = findPreferenceNotNull<EditTextPreference>("byedpi_proxytest_commands")
        val manageDomainLists = findPreferenceNotNull<Preference>("manage_domain_lists")
        val runAutopilot = findPreferenceNotNull<Preference>("run_autopilot")
        val activeLists = DomainListUtils.getLists(requireContext()).filter { it.isActive }

        manageDomainLists.summary = if (activeLists.isEmpty()) {
            getString(R.string.domain_lists_summary)
        } else {
            activeLists.joinToString(", ") { it.name }
        }

        textUserCommands.isEnabled = switchUserCommands.isChecked
        runAutopilot.summary = AutopilotManager.stateDescription(requireContext())
    }

    /** D2: ручной запуск автоподбора по паку 30 стратегий. */
    private fun setupAutopilot() {
        val runAutopilot = findPreferenceNotNull<Preference>("run_autopilot")
        runAutopilot.setOnPreferenceClickListener {
            if (TestService.isRunning) {
                Toast.makeText(requireContext(), R.string.test_unavailable, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.autopilot_started, Toast.LENGTH_LONG).show()
                TestService.start(requireContext(), TestService.SOURCE_PACK)
            }
            true
        }
    }

    private fun setupNumberSummary(key: String, descriptionResId: Int) {
        val pref = findPreference<EditTextPreference>(key) ?: return

        pref.summaryProvider = Preference.SummaryProvider<EditTextPreference> { p ->
            val value = p.text ?: "—"
            val description = getString(descriptionResId)
            "$value - $description"
        }
    }
}