package lilmuff1.bsml.state

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import lilmuff1.bsml.config.DEFAULT_CAPTURE_PACKAGES
import lilmuff1.bsml.config.DEFAULT_CAPTURE_TARGETS
import lilmuff1.bsml.config.DNS_SERVER_PRESETS
import lilmuff1.bsml.config.GAME_PORT
import lilmuff1.bsml.config.NULLS_PROXY_DNS
import lilmuff1.bsml.service.DnsOverHttpsResolver
import lilmuff1.bsml.service.UdpDnsResolver

data class VpnCaptureSettings(
    val filterByIp: Boolean,
    val ipFilterText: String,
    val packageText: String,
    val port: Int,
    val autoLaunchPackage: String?
)

data class CleanupReasonSpec(
    val code: Int,
    val name: String
)

object VpnLogRepository {
    private const val TAG = "BSMLLocalVpn"
    private const val MAX_LOGS = 200
    private const val ORIGINAL_GAME_PACKAGE = "com.supercell.brawlstars"
    private const val PREFS_NAME = "bsml_vpn_settings"
    private const val KEY_AUTO_VPN_DISABLE = "auto_vpn_disable"
    private const val KEY_AUTO_LAUNCH_GAME = "auto_launch_game"
    private const val KEY_IP_FILTER_ENABLED = "ip_filter_enabled"
    private const val KEY_IP_FILTER_TEXT = "ip_filter_text"
    private const val KEY_PACKAGE_TEXT = "package_text"
    private const val KEY_AUTO_LAUNCH_PACKAGE = "auto_launch_package"
    private const val KEY_PORT_TEXT = "port_text"
    private const val KEY_INSTALL_RESULT_NOTIFICATIONS_ENABLED = "install_result_notifications_enabled"
    private const val KEY_LAST_CLIENT_VERSION = "last_client_version"
    private const val KEY_SHOW_REINSTALL_WARNING_AFTER_DELETE = "show_reinstall_warning_after_delete"
    private const val KEY_AUTO_PREPARE_FILES_ENABLED = "auto_prepare_files_enabled"
    private const val KEY_AUTO_PREPARE_FILES_DELAY_SECONDS = "auto_prepare_files_delay_seconds"
    private const val KEY_EXPORT_LOG_LINES = "export_log_lines"
    private const val KEY_THOROUGH_MOD_DELETE = "thorough_mod_delete"
    private const val KEY_SERVER_REGION = "server_region"
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val KEY_CUSTOM_DNS_ENABLED = "custom_dns_enabled"
    private const val KEY_CUSTOM_DOH_URL = "custom_doh_url"
    private const val KEY_DNS_PRESET_INDEX = "dns_preset_index"
    private val cleanupWarmupReason = CleanupReasonSpec(7, "CLIENT_CONTENT_UPDATE")

    private val logLock = Any()
    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
    @Volatile
    private var preferencesLoaded = false

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _status = MutableStateFlow("Остановлено")
    val status = _status.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _isAssetProxyRunning = MutableStateFlow(false)
    val isAssetProxyRunning = _isAssetProxyRunning.asStateFlow()

    private val _isAutoVpnDisableEnabled = MutableStateFlow(false)
    val isAutoVpnDisableEnabled = _isAutoVpnDisableEnabled.asStateFlow()

    private val _isAutoLaunchGameEnabled = MutableStateFlow(false)
    val isAutoLaunchGameEnabled = _isAutoLaunchGameEnabled.asStateFlow()

    private val _isIpFilterEnabled = MutableStateFlow(true)
    val isIpFilterEnabled = _isIpFilterEnabled.asStateFlow()

    private val _ipFilterText = MutableStateFlow(DEFAULT_CAPTURE_TARGETS)
    val ipFilterText = _ipFilterText.asStateFlow()

    private val _packageText = MutableStateFlow(DEFAULT_CAPTURE_PACKAGES)
    val packageText = _packageText.asStateFlow()

    private val _autoLaunchPackage = MutableStateFlow<String?>(null)
    val autoLaunchPackage = _autoLaunchPackage.asStateFlow()

    private val _portText = MutableStateFlow(GAME_PORT.toString())
    val portText = _portText.asStateFlow()

    private val _isInstallResultNotificationsEnabled = MutableStateFlow(false)
    val isInstallResultNotificationsEnabled = _isInstallResultNotificationsEnabled.asStateFlow()

    private val _lastClientVersion = MutableStateFlow<String?>(null)
    val lastClientVersion = _lastClientVersion.asStateFlow()

    private val _showReinstallWarningAfterDelete = MutableStateFlow(true)
    val showReinstallWarningAfterDelete = _showReinstallWarningAfterDelete.asStateFlow()

    private val _isAutoPrepareFilesEnabled = MutableStateFlow(true)
    val isAutoPrepareFilesEnabled = _isAutoPrepareFilesEnabled.asStateFlow()

    private val _autoPrepareFilesDelaySeconds = MutableStateFlow("3")
    val autoPrepareFilesDelaySeconds = _autoPrepareFilesDelaySeconds.asStateFlow()

    private val _exportLogLines = MutableStateFlow("50")
    val exportLogLines = _exportLogLines.asStateFlow()

    private val _isThoroughModDeleteEnabled = MutableStateFlow(false)
    val isThoroughModDeleteEnabled = _isThoroughModDeleteEnabled.asStateFlow()

    private val _serverRegion = MutableStateFlow("auto")
    val serverRegion = _serverRegion.asStateFlow()

    private val _deleteCleanupPending = MutableStateFlow(false)
    val deleteCleanupPending = _deleteCleanupPending.asStateFlow()

    private val _appLanguage = MutableStateFlow("system")
    val appLanguage = _appLanguage.asStateFlow()

    private val _isCustomDnsEnabled = MutableStateFlow(false)
    val isCustomDnsEnabled = _isCustomDnsEnabled.asStateFlow()

    private val _customDohUrl = MutableStateFlow("")
    val customDohUrl = _customDohUrl.asStateFlow()

    private val _dnsPresetIndex = MutableStateFlow(0)
    val dnsPresetIndex = _dnsPresetIndex.asStateFlow()

    @Volatile
    private var dnsResolver: DnsOverHttpsResolver? = null

    @Volatile
    private var udpDnsResolver: UdpDnsResolver? = null

    private val _vpnStartBlockedSignal = MutableStateFlow(0)
    val vpnStartBlockedSignal = _vpnStartBlockedSignal.asStateFlow()

    fun initialize(context: Context) {
        if (preferencesLoaded) return
        synchronized(this) {
            if (preferencesLoaded) return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _isAutoVpnDisableEnabled.value = prefs.getBoolean(KEY_AUTO_VPN_DISABLE, false)
            _isAutoLaunchGameEnabled.value = prefs.getBoolean(KEY_AUTO_LAUNCH_GAME, false)
            _isIpFilterEnabled.value = prefs.getBoolean(KEY_IP_FILTER_ENABLED, true)
            _ipFilterText.value = prefs.getString(KEY_IP_FILTER_TEXT, DEFAULT_CAPTURE_TARGETS) ?: DEFAULT_CAPTURE_TARGETS
            _packageText.value = prefs.getString(KEY_PACKAGE_TEXT, DEFAULT_CAPTURE_PACKAGES) ?: DEFAULT_CAPTURE_PACKAGES
            val savedAutoLaunchPackage = prefs.getString(KEY_AUTO_LAUNCH_PACKAGE, null)?.trim()?.ifEmpty { null }
            val defaultAutoLaunchPackage = if (!prefs.contains(KEY_AUTO_LAUNCH_PACKAGE) && isPackageLaunchable(context, ORIGINAL_GAME_PACKAGE)) {
                ORIGINAL_GAME_PACKAGE
            } else {
                savedAutoLaunchPackage
            }
            _autoLaunchPackage.value = defaultAutoLaunchPackage
            if (defaultAutoLaunchPackage != savedAutoLaunchPackage) {
                prefs.edit().putString(KEY_AUTO_LAUNCH_PACKAGE, defaultAutoLaunchPackage).apply()
            }
            _portText.value = prefs.getString(KEY_PORT_TEXT, GAME_PORT.toString())
                ?.filter { it.isDigit() }
                ?.take(5)
                ?.ifEmpty { GAME_PORT.toString() }
                ?: GAME_PORT.toString()
            _isInstallResultNotificationsEnabled.value = prefs.getBoolean(KEY_INSTALL_RESULT_NOTIFICATIONS_ENABLED, false)
            _lastClientVersion.value = prefs.getString(KEY_LAST_CLIENT_VERSION, null)?.trim()?.ifEmpty { null }
            _showReinstallWarningAfterDelete.value = prefs.getBoolean(KEY_SHOW_REINSTALL_WARNING_AFTER_DELETE, true)
            _isAutoPrepareFilesEnabled.value = prefs.getBoolean(KEY_AUTO_PREPARE_FILES_ENABLED, true)
            _autoPrepareFilesDelaySeconds.value = prefs.getInt(KEY_AUTO_PREPARE_FILES_DELAY_SECONDS, 3).toString()
            _exportLogLines.value = prefs.getInt(KEY_EXPORT_LOG_LINES, 50).toString()
            _isThoroughModDeleteEnabled.value = prefs.getBoolean(KEY_THOROUGH_MOD_DELETE, false)
            _serverRegion.value = prefs.getString(KEY_SERVER_REGION, "auto") ?: "auto"
            _appLanguage.value = prefs.getString(KEY_APP_LANGUAGE, "system") ?: "system"
            _isCustomDnsEnabled.value = prefs.getBoolean(KEY_CUSTOM_DNS_ENABLED, false)
            _customDohUrl.value = prefs.getString(KEY_CUSTOM_DOH_URL, "") ?: ""
            _dnsPresetIndex.value = prefs.getInt(KEY_DNS_PRESET_INDEX, 0)
            preferencesLoaded = true
        }
    }

    private fun updatePreference(context: Context, block: android.content.SharedPreferences.Editor.() -> Unit) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply(block)
            .commit()
    }

    fun setStatus(status: String) {
        _status.value = status
    }

    fun setRunning(isRunning: Boolean) {
        _isRunning.value = isRunning
    }

    fun isVpnRunningNow(): Boolean = _isRunning.value

    fun notifyVpnStartBlocked(reason: String) {
        setStatus("VPN start blocked")
        log("VPN start blocked: $reason")
        _vpnStartBlockedSignal.value = _vpnStartBlockedSignal.value + 1
    }

    fun setAssetProxyRunning(isRunning: Boolean) {
        _isAssetProxyRunning.value = isRunning
    }

    fun setAutoVpnDisableEnabled(isEnabled: Boolean) {
        _isAutoVpnDisableEnabled.value = isEnabled
    }

    fun setAutoVpnDisableEnabled(context: Context, isEnabled: Boolean) {
        setAutoVpnDisableEnabled(isEnabled)
        updatePreference(context) { putBoolean(KEY_AUTO_VPN_DISABLE, isEnabled) }
    }

    fun isAutoVpnDisableEnabledNow(): Boolean = _isAutoVpnDisableEnabled.value

    fun setAutoLaunchGameEnabled(isEnabled: Boolean) {
        _isAutoLaunchGameEnabled.value = isEnabled
    }

    fun setAutoLaunchGameEnabled(context: Context, isEnabled: Boolean) {
        setAutoLaunchGameEnabled(isEnabled)
        updatePreference(context) { putBoolean(KEY_AUTO_LAUNCH_GAME, isEnabled) }
    }

    fun isAutoLaunchGameEnabledNow(): Boolean = _isAutoLaunchGameEnabled.value

    fun setIpFilterEnabled(isEnabled: Boolean) {
        _isIpFilterEnabled.value = isEnabled
    }

    fun setIpFilterEnabled(context: Context, isEnabled: Boolean) {
        setIpFilterEnabled(isEnabled)
        updatePreference(context) { putBoolean(KEY_IP_FILTER_ENABLED, isEnabled) }
    }

    fun setIpFilterText(text: String) {
        _ipFilterText.value = text
    }

    fun setIpFilterText(context: Context, text: String) {
        setIpFilterText(text)
        updatePreference(context) { putString(KEY_IP_FILTER_TEXT, text) }
    }

    fun setPackageText(text: String) {
        _packageText.value = text
    }

    fun setPackageText(context: Context, text: String) {
        setPackageText(text)
        updatePreference(context) { putString(KEY_PACKAGE_TEXT, text) }
    }

    fun setAutoLaunchPackage(packageName: String?) {
        _autoLaunchPackage.value = packageName?.trim()?.ifEmpty { null }
    }

    fun setAutoLaunchPackage(context: Context, packageName: String?) {
        val normalized = packageName?.trim()?.ifEmpty { null }
        setAutoLaunchPackage(normalized)
        updatePreference(context) { putString(KEY_AUTO_LAUNCH_PACKAGE, normalized) }
    }

    fun setPortText(text: String) {
        _portText.value = text.filter { it.isDigit() }.take(5)
    }

    fun setPortText(context: Context, text: String) {
        val normalized = text.filter { it.isDigit() }.take(5)
        setPortText(normalized)
        updatePreference(context) { putString(KEY_PORT_TEXT, normalized) }
    }

    fun setInstallResultNotificationsEnabled(isEnabled: Boolean) {
        _isInstallResultNotificationsEnabled.value = isEnabled
    }

    fun setInstallResultNotificationsEnabled(context: Context, isEnabled: Boolean) {
        setInstallResultNotificationsEnabled(isEnabled)
        updatePreference(context) { putBoolean(KEY_INSTALL_RESULT_NOTIFICATIONS_ENABLED, isEnabled) }
    }

    fun isInstallResultNotificationsEnabledNow(): Boolean = _isInstallResultNotificationsEnabled.value

    fun setLastClientVersion(version: String?) {
        _lastClientVersion.value = version?.trim()?.ifEmpty { null }
    }

    fun setLastClientVersion(context: Context, version: String?) {
        val normalized = version?.trim()?.ifEmpty { null }
        setLastClientVersion(normalized)
        updatePreference(context) { putString(KEY_LAST_CLIENT_VERSION, normalized) }
    }

    fun lastClientVersionNow(): String? = _lastClientVersion.value

    fun setShowReinstallWarningAfterDelete(isEnabled: Boolean) {
        _showReinstallWarningAfterDelete.value = isEnabled
    }

    fun setShowReinstallWarningAfterDelete(context: Context, isEnabled: Boolean) {
        setShowReinstallWarningAfterDelete(isEnabled)
        updatePreference(context) { putBoolean(KEY_SHOW_REINSTALL_WARNING_AFTER_DELETE, isEnabled) }
    }

    fun shouldShowReinstallWarningAfterDeleteNow(): Boolean = _showReinstallWarningAfterDelete.value

    fun setAutoPrepareFilesEnabled(context: Context, isEnabled: Boolean) {
        _isAutoPrepareFilesEnabled.value = isEnabled
        updatePreference(context) { putBoolean(KEY_AUTO_PREPARE_FILES_ENABLED, isEnabled) }
    }

    fun isAutoPrepareFilesEnabledNow(): Boolean = _isAutoPrepareFilesEnabled.value

    fun setAutoPrepareFilesDelaySeconds(context: Context, text: String) {
        val normalized = text.filter { it.isDigit() }
            .take(3)
            .toIntOrNull()
            ?.coerceIn(1, 120)
            ?: 3
        _autoPrepareFilesDelaySeconds.value = normalized.toString()
        updatePreference(context) { putInt(KEY_AUTO_PREPARE_FILES_DELAY_SECONDS, normalized) }
    }

    fun autoPrepareFilesDelayMillisNow(): Long {
        val seconds = _autoPrepareFilesDelaySeconds.value.toIntOrNull()?.coerceIn(1, 120) ?: 3
        return seconds * 1_000L
    }

    fun setExportLogLines(context: Context, text: String) {
        val normalized = text.filter { it.isDigit() }
            .take(4)
            .toIntOrNull()
            ?.coerceIn(1, MAX_LOGS)
            ?: 50
        _exportLogLines.value = normalized.toString()
        updatePreference(context) { putInt(KEY_EXPORT_LOG_LINES, normalized) }
    }

    fun exportLogLinesNow(): Int {
        return _exportLogLines.value.toIntOrNull()?.coerceIn(1, MAX_LOGS) ?: 50
    }

    fun setThoroughModDeleteEnabled(context: Context, isEnabled: Boolean) {
        _isThoroughModDeleteEnabled.value = isEnabled
        updatePreference(context) { putBoolean(KEY_THOROUGH_MOD_DELETE, isEnabled) }
    }

    fun isThoroughModDeleteEnabledNow(): Boolean = _isThoroughModDeleteEnabled.value

    fun setServerRegion(context: Context, region: String) {
        _serverRegion.value = region
        updatePreference(context) { putString(KEY_SERVER_REGION, region) }
    }

    fun serverRegionNow(): String = _serverRegion.value

    fun setAppLanguage(context: Context, language: String) {
        _appLanguage.value = language
        updatePreference(context) { putString(KEY_APP_LANGUAGE, language) }
    }

    fun appLanguageNow(): String = _appLanguage.value

    fun setCustomDnsEnabled(context: Context, isEnabled: Boolean) {
        _isCustomDnsEnabled.value = isEnabled
        updatePreference(context) { putBoolean(KEY_CUSTOM_DNS_ENABLED, isEnabled) }
    }

    fun isCustomDnsEnabledNow(): Boolean = _isCustomDnsEnabled.value

    fun setCustomDohUrl(context: Context, url: String) {
        val normalized = url.trim()
        _customDohUrl.value = normalized
        updatePreference(context) { putString(KEY_CUSTOM_DOH_URL, normalized) }
    }

    fun customDohUrlNow(): String = _customDohUrl.value

    fun setDnsPresetIndex(context: Context, index: Int) {
        _dnsPresetIndex.value = index
        updatePreference(context) { putInt(KEY_DNS_PRESET_INDEX, index) }
    }

    fun dnsPresetIndexNow(): Int = _dnsPresetIndex.value

    fun effectiveDohUrl(): String {
        val custom = _customDohUrl.value.trim()
        if (custom.isNotEmpty()) return custom
        val index = _dnsPresetIndex.value.coerceIn(0, DNS_SERVER_PRESETS.size - 1)
        return DNS_SERVER_PRESETS[index].dohUrl
    }

    fun effectiveBootstrapIp(): String {
        val index = _dnsPresetIndex.value.coerceIn(0, DNS_SERVER_PRESETS.size - 1)
        return DNS_SERVER_PRESETS[index].bootstrapIp
    }

    fun resolveWithCustomDns(hostname: String): List<java.net.Inet4Address> {
        if (!_isCustomDnsEnabled.value) return emptyList()
        val dohUrl = effectiveDohUrl()
        if (dohUrl.isEmpty()) return emptyList()
        val bootstrapIp = effectiveBootstrapIp().takeIf { it.isNotEmpty() }
        val currentDohUrl = dnsResolver?.dohUrl ?: ""
        val resolver = if (dnsResolver == null || currentDohUrl != dohUrl) {
            DnsOverHttpsResolver(dohUrl, bootstrapIp).also { dnsResolver = it }
        } else {
            dnsResolver!!
        }
        return resolver.resolve(hostname)
    }

    fun getDnsServerIp(): String? {
        if (!_isCustomDnsEnabled.value) return null
        val custom = _customDohUrl.value.trim()
        if (custom.isNotEmpty()) {
            val bootstrapIp = effectiveBootstrapIp()
            return bootstrapIp.takeIf { it.isNotEmpty() }
        }
        val index = _dnsPresetIndex.value.coerceIn(0, DNS_SERVER_PRESETS.size - 1)
        return DNS_SERVER_PRESETS[index].bootstrapIp.takeIf { it.isNotEmpty() }
    }

    fun resolveViaDnsServer(target: String, dnsServerIp: String, dnsPort: Int = 53): List<java.net.Inet4Address> {
        return try {
            val resolver = udpDnsResolver?.takeIf { it.dnsServerIp == dnsServerIp && it.dnsServerPort == dnsPort }
                ?: UdpDnsResolver(dnsServerIp, dnsPort).also { udpDnsResolver = it }
            resolver.resolve(target)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun resolveNullsProxyIp(): String? {
        val preset = DNS_SERVER_PRESETS.firstOrNull { it.dohUrl.contains("nullsproxy") } ?: return null
        val staticIp = preset.bootstrapIp
        return try {
            val resolver = udpDnsResolver?.takeIf { it.dnsServerIp == staticIp }
                ?: UdpDnsResolver(staticIp).also { udpDnsResolver = it }
            val addresses = resolver.resolve(NULLS_PROXY_DNS)
            addresses.firstOrNull()?.hostAddress ?: staticIp
        } catch (e: Exception) {
            staticIp
        }
    }

    fun enableNullsProxy(context: Context) {
        val preset = DNS_SERVER_PRESETS.firstOrNull { it.dohUrl.contains("nullsproxy") } ?: return
        _isCustomDnsEnabled.value = true
        _customDohUrl.value = preset.dohUrl
        _dnsPresetIndex.value = DNS_SERVER_PRESETS.indexOf(preset)
        updatePreference(context) {
            putBoolean(KEY_CUSTOM_DNS_ENABLED, true)
            putString(KEY_CUSTOM_DOH_URL, preset.dohUrl)
            putInt(KEY_DNS_PRESET_INDEX, DNS_SERVER_PRESETS.indexOf(preset))
        }
    }

    fun disableCustomDns(context: Context) {
        _isCustomDnsEnabled.value = false
        updatePreference(context) { putBoolean(KEY_CUSTOM_DNS_ENABLED, false) }
    }

    fun isNullsProxyEnabledNow(): Boolean {
        if (!_isCustomDnsEnabled.value) return false
        val custom = _customDohUrl.value.trim()
        return custom.contains("nullsproxy") || _dnsPresetIndex.value == DNS_SERVER_PRESETS.indexOfFirst { it.dohUrl.contains("nullsproxy") }
    }

    private fun isPackageLaunchable(context: Context, packageName: String): Boolean {
        return context.packageManager.getLaunchIntentForPackage(packageName) != null
    }

    fun markDeleteCleanupPending() {
        _deleteCleanupPending.value = true
    }

    fun clearDeleteCleanupPending() {
        _deleteCleanupPending.value = false
    }

    fun isDeleteCleanupPendingNow(): Boolean = _deleteCleanupPending.value

    fun captureSettingsNow(): VpnCaptureSettings {
        val port = _portText.value.toIntOrNull()
            ?.takeIf { it in 1..65535 }
            ?: GAME_PORT
        return VpnCaptureSettings(
            filterByIp = _isIpFilterEnabled.value,
            ipFilterText = _ipFilterText.value,
            packageText = _packageText.value,
            port = port,
            autoLaunchPackage = _autoLaunchPackage.value
        )
    }

    fun cleanupWarmupReason(): CleanupReasonSpec = cleanupWarmupReason

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun exportLogsText(limit: Int = MAX_LOGS): String {
        return _logs.value
            .asReversed()
            .take(limit.coerceAtLeast(0))
            .joinToString(separator = "\n")
    }

    fun log(message: String) {
        val timestamp = timeFormat.get()?.format(Date()) ?: ""
        val line = "[$timestamp] $message"
        Log.d(TAG, line)
        synchronized(logLock) {
            _logs.value = listOf(line) + _logs.value.take(MAX_LOGS - 1)
        }
    }
}
