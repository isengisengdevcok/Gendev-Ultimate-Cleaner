package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppSettings
import com.example.model.AutomationConfig
import com.example.model.CleaningHistoryItem
import com.example.model.LastCleanSummary
import com.example.model.OperationMode
import com.example.model.StorageHistoryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.cleanerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ultimate_cleaner_prefs"
)

class CleaningRepository(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val PURPLE_ACCENT = booleanPreferencesKey("purple_accent")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val AUTO_SCROLL_LOG = booleanPreferencesKey("auto_scroll_log")
        val CLEANING_CONFIRMATION = booleanPreferencesKey("cleaning_confirmation")
        val SECURE_SHRED_ENABLED = booleanPreferencesKey("secure_shred_enabled")
        val ZERO_TRACE_HISTORY_ENABLED = booleanPreferencesKey("zero_trace_history_enabled")
        val OPERATION_MODE = stringPreferencesKey("operation_mode")
        val CUSTOM_EXCLUSIONS = stringPreferencesKey("custom_exclusions_json")
        val GRANTED_SAF_URIS = stringPreferencesKey("granted_saf_uris_json")

        val TOTAL_FREED_BYTES = longPreferencesKey("total_freed_bytes")
        val LAST_CLEAN_FREED_BYTES = longPreferencesKey("last_clean_freed_bytes")
        val LAST_CLEAN_FILES = intPreferencesKey("last_clean_files")
        val LAST_CLEAN_MODE = stringPreferencesKey("last_clean_mode")
        val LAST_CLEAN_TS = longPreferencesKey("last_clean_ts")
        val HISTORY_JSON = stringPreferencesKey("cleaning_history_json")

        val AUTO_SCHEDULED = booleanPreferencesKey("auto_scheduled")
        val AUTO_INTERVAL_HOURS = intPreferencesKey("auto_interval_hours")
        val AUTO_SCREEN_OFF = booleanPreferencesKey("auto_screen_off")
        val AUTO_BATTERY_SAVER = booleanPreferencesKey("auto_battery_saver")
        val AUTO_LOW_STORAGE_GUARD = booleanPreferencesKey("auto_low_storage_guard")
        val AUTO_LOW_STORAGE_GB = intPreferencesKey("auto_low_storage_gb")
        val AUTO_COOLDOWN_MINUTES = intPreferencesKey("auto_cooldown_minutes")
        val LAST_BOOT_TS = longPreferencesKey("last_boot_timestamp")
        val LAST_AUTO_CLEAN_TS = longPreferencesKey("last_auto_clean_timestamp")
        val LAST_AUTO_CLEAN_SUMMARY = stringPreferencesKey("last_auto_clean_summary")
        val LAST_AUTO_CLEAN_TRIGGER = stringPreferencesKey("last_auto_clean_trigger")
        val BOOT_STATUS_MSG = stringPreferencesKey("boot_status_msg")

        // AI Lab persistence
        val STORAGE_HISTORY_JSON = stringPreferencesKey("ai_storage_history_json")
        val PREV_APP_CACHE_JSON = stringPreferencesKey("ai_prev_app_cache_json")
        val PREV_APP_CACHE_TS = longPreferencesKey("ai_prev_app_cache_ts")
        val BASELINE_DIFF_JSON = stringPreferencesKey("ai_baseline_diff_json")
        val BASELINE_DIFF_TS = longPreferencesKey("ai_baseline_diff_ts")
    }

    val settingsFlow: Flow<AppSettings> = context.cleanerDataStore.data.map { prefs ->
        val modeName = prefs[Keys.OPERATION_MODE] ?: OperationMode.AUTO.name
        val mode = OperationMode.entries.firstOrNull { it.name == modeName } ?: OperationMode.AUTO
        AppSettings(
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            purpleAccent = prefs[Keys.PURPLE_ACCENT] ?: true,
            animationsEnabled = prefs[Keys.ANIMATIONS_ENABLED] ?: true,
            autoScrollLog = prefs[Keys.AUTO_SCROLL_LOG] ?: true,
            cleaningConfirmation = prefs[Keys.CLEANING_CONFIRMATION] ?: true,
            secureShredEnabled = prefs[Keys.SECURE_SHRED_ENABLED] ?: true,
            zeroTraceHistoryEnabled = prefs[Keys.ZERO_TRACE_HISTORY_ENABLED] ?: false,
            operationMode = mode,
            customExclusions = parseStringList(prefs[Keys.CUSTOM_EXCLUSIONS]),
            grantedSafUris = parseStringList(prefs[Keys.GRANTED_SAF_URIS])
        )
    }

    val totalFreedBytesFlow: Flow<Long> = context.cleanerDataStore.data.map { prefs ->
        prefs[Keys.TOTAL_FREED_BYTES] ?: 0L
    }

    val lastCleanFlow: Flow<LastCleanSummary> = context.cleanerDataStore.data.map { prefs ->
        LastCleanSummary(
            mode = prefs[Keys.LAST_CLEAN_MODE] ?: "",
            freedBytes = prefs[Keys.LAST_CLEAN_FREED_BYTES] ?: 0L,
            filesDeleted = prefs[Keys.LAST_CLEAN_FILES] ?: 0,
            timestampMillis = prefs[Keys.LAST_CLEAN_TS] ?: 0L
        )
    }

    val historyFlow: Flow<List<CleaningHistoryItem>> = context.cleanerDataStore.data.map { prefs ->
        parseHistoryList(prefs[Keys.HISTORY_JSON])
    }

    val automationFlow: Flow<AutomationConfig> = context.cleanerDataStore.data.map { prefs ->
        AutomationConfig(
            scheduledEnabled = prefs[Keys.AUTO_SCHEDULED] ?: false,
            intervalHours = prefs[Keys.AUTO_INTERVAL_HOURS] ?: 12,
            screenOffEnabled = prefs[Keys.AUTO_SCREEN_OFF] ?: false,
            batterySaverEnabled = prefs[Keys.AUTO_BATTERY_SAVER] ?: false,
            lowStorageGuardEnabled = prefs[Keys.AUTO_LOW_STORAGE_GUARD] ?: false,
            lowStorageThresholdGb = prefs[Keys.AUTO_LOW_STORAGE_GB] ?: 2,
            cooldownMinutes = prefs[Keys.AUTO_COOLDOWN_MINUTES] ?: 30,
            lastBootTimestamp = prefs[Keys.LAST_BOOT_TS] ?: 0L,
            lastAutoCleanTimestamp = prefs[Keys.LAST_AUTO_CLEAN_TS] ?: 0L,
            lastAutoCleanSummary = prefs[Keys.LAST_AUTO_CLEAN_SUMMARY] ?: "",
            lastAutoCleanTrigger = prefs[Keys.LAST_AUTO_CLEAN_TRIGGER] ?: "",
            bootStatusMessage = prefs[Keys.BOOT_STATUS_MSG] ?: "Menunggu event BOOT_COMPLETED"
        )
    }

    suspend fun getSettingsOnce(): AppSettings = settingsFlow.first()

    suspend fun getAutomationOnce(): AutomationConfig = automationFlow.first()

    suspend fun updateDarkTheme(enabled: Boolean) {
        context.cleanerDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun updatePurpleAccent(enabled: Boolean) {
        context.cleanerDataStore.edit { it[Keys.PURPLE_ACCENT] = enabled }
    }

    suspend fun updateAnimationsEnabled(enabled: Boolean) {
        context.cleanerDataStore.edit { it[Keys.ANIMATIONS_ENABLED] = enabled }
    }

    suspend fun updateAutoScrollLog(enabled: Boolean) {
        context.cleanerDataStore.edit { it[Keys.AUTO_SCROLL_LOG] = enabled }
    }

    suspend fun updateCleaningConfirmation(enabled: Boolean) {
        context.cleanerDataStore.edit { it[Keys.CLEANING_CONFIRMATION] = enabled }
    }

    suspend fun updateSecureShredEnabled(enabled: Boolean) {
        context.cleanerDataStore.edit { it[Keys.SECURE_SHRED_ENABLED] = enabled }
    }

    suspend fun updateZeroTraceHistoryEnabled(enabled: Boolean) {
        context.cleanerDataStore.edit { prefs ->
            prefs[Keys.ZERO_TRACE_HISTORY_ENABLED] = enabled
            if (enabled) {
                prefs[Keys.HISTORY_JSON] = "[]"
            }
        }
    }

    suspend fun updateOperationMode(mode: OperationMode) {
        context.cleanerDataStore.edit { it[Keys.OPERATION_MODE] = mode.name }
    }

    suspend fun addSafUri(uriString: String) {
        if (uriString.isBlank()) return
        context.cleanerDataStore.edit { prefs ->
            val current = parseStringList(prefs[Keys.GRANTED_SAF_URIS]).toMutableList()
            if (!current.contains(uriString)) {
                current.add(uriString)
                prefs[Keys.GRANTED_SAF_URIS] = serializeStringList(current)
            }
        }
    }

    suspend fun removeSafUri(uriString: String) {
        context.cleanerDataStore.edit { prefs ->
            val current = parseStringList(prefs[Keys.GRANTED_SAF_URIS]).filterNot { it == uriString }
            prefs[Keys.GRANTED_SAF_URIS] = serializeStringList(current)
        }
    }

    suspend fun addExclusion(pattern: String) {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return
        context.cleanerDataStore.edit { prefs ->
            val current = parseStringList(prefs[Keys.CUSTOM_EXCLUSIONS]).toMutableList()
            if (!current.contains(trimmed)) {
                current.add(trimmed)
                prefs[Keys.CUSTOM_EXCLUSIONS] = serializeStringList(current)
            }
        }
    }

    suspend fun removeExclusion(pattern: String) {
        context.cleanerDataStore.edit { prefs ->
            val current = parseStringList(prefs[Keys.CUSTOM_EXCLUSIONS]).filterNot { it == pattern }
            prefs[Keys.CUSTOM_EXCLUSIONS] = serializeStringList(current)
        }
    }

    suspend fun recordCleaningRun(
        mode: String,
        freedBytes: Long,
        filesDeleted: Int,
        details: String,
        success: Boolean
    ) {
        val now = System.currentTimeMillis()
        val safeFreed = freedBytes.coerceAtLeast(0L)
        context.cleanerDataStore.edit { prefs ->
            val prevTotal = prefs[Keys.TOTAL_FREED_BYTES] ?: 0L
            prefs[Keys.TOTAL_FREED_BYTES] = prevTotal + safeFreed
            prefs[Keys.LAST_CLEAN_FREED_BYTES] = safeFreed
            prefs[Keys.LAST_CLEAN_FILES] = filesDeleted.coerceAtLeast(0)
            prefs[Keys.LAST_CLEAN_MODE] = mode
            prefs[Keys.LAST_CLEAN_TS] = now

            val zeroTrace = prefs[Keys.ZERO_TRACE_HISTORY_ENABLED] ?: false
            if (zeroTrace) {
                prefs[Keys.HISTORY_JSON] = "[]"
            } else {
                val currentHistory = parseHistoryList(prefs[Keys.HISTORY_JSON]).toMutableList()
                currentHistory.add(
                    0,
                    CleaningHistoryItem(
                        id = now,
                        timestampMillis = now,
                        mode = mode,
                        freedBytes = safeFreed,
                        filesDeleted = filesDeleted,
                        details = details,
                        success = success
                    )
                )
                val trimmed = currentHistory.take(30)
                prefs[Keys.HISTORY_JSON] = serializeHistoryList(trimmed)
            }
        }
    }

    suspend fun clearHistory() {
        context.cleanerDataStore.edit { prefs ->
            prefs[Keys.HISTORY_JSON] = "[]"
            prefs[Keys.TOTAL_FREED_BYTES] = 0L
            prefs[Keys.LAST_CLEAN_FREED_BYTES] = 0L
            prefs[Keys.LAST_CLEAN_FILES] = 0
            prefs[Keys.LAST_CLEAN_MODE] = ""
            prefs[Keys.LAST_CLEAN_TS] = 0L
        }
    }

    suspend fun clearHistoryLogsOnly() {
        context.cleanerDataStore.edit { prefs ->
            prefs[Keys.HISTORY_JSON] = "[]"
        }
    }

    suspend fun updateScheduledAutomation(enabled: Boolean, intervalHours: Int = 12) {
        context.cleanerDataStore.edit {
            it[Keys.AUTO_SCHEDULED] = enabled
            it[Keys.AUTO_INTERVAL_HOURS] = intervalHours
        }
    }

    suspend fun updateScreenOffAutomation(enabled: Boolean) {
        context.cleanerDataStore.edit { it[Keys.AUTO_SCREEN_OFF] = enabled }
    }

    suspend fun updateBatterySaverAutomation(enabled: Boolean) {
        context.cleanerDataStore.edit { it[Keys.AUTO_BATTERY_SAVER] = enabled }
    }

    suspend fun updateLowStorageGuard(enabled: Boolean, thresholdGb: Int) {
        context.cleanerDataStore.edit {
            it[Keys.AUTO_LOW_STORAGE_GUARD] = enabled
            it[Keys.AUTO_LOW_STORAGE_GB] = thresholdGb.coerceIn(1, 5)
        }
    }

    suspend fun updateAutomationCooldown(minutes: Int) {
        context.cleanerDataStore.edit {
            it[Keys.AUTO_COOLDOWN_MINUTES] = minutes.coerceIn(5, 180)
        }
    }

    suspend fun recordBootEvent(timestampMillis: Long, message: String) {
        context.cleanerDataStore.edit {
            it[Keys.LAST_BOOT_TS] = timestampMillis
            it[Keys.BOOT_STATUS_MSG] = message
        }
    }

    suspend fun recordAutoCleanResult(
        timestampMillis: Long,
        trigger: String,
        summary: String
    ) {
        context.cleanerDataStore.edit {
            it[Keys.LAST_AUTO_CLEAN_TS] = timestampMillis
            it[Keys.LAST_AUTO_CLEAN_TRIGGER] = trigger
            it[Keys.LAST_AUTO_CLEAN_SUMMARY] = summary
        }
    }

    // AI Forecaster Persistence
    suspend fun getStorageHistoryPoints(): List<StorageHistoryPoint> {
        val raw = context.cleanerDataStore.data.first()[Keys.STORAGE_HISTORY_JSON] ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { idx ->
                val obj = arr.getJSONObject(idx)
                StorageHistoryPoint(
                    timestampMillis = obj.optLong("ts"),
                    usedBytes = obj.optLong("used"),
                    availableBytes = obj.optLong("avail")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun appendStorageHistoryPoint(point: StorageHistoryPoint) {
        context.cleanerDataStore.edit { prefs ->
            val current = try {
                val arr = JSONArray(prefs[Keys.STORAGE_HISTORY_JSON] ?: "[]")
                MutableList(arr.length()) { idx ->
                    val obj = arr.getJSONObject(idx)
                    StorageHistoryPoint(obj.optLong("ts"), obj.optLong("used"), obj.optLong("avail"))
                }
            } catch (_: Exception) {
                mutableListOf()
            }
            val last = current.lastOrNull()
            // Replace if recorded within last 5 minutes, else append up to 40 points
            if (last != null && (point.timestampMillis - last.timestampMillis) < 5 * 60 * 1000L) {
                current[current.lastIndex] = point
            } else {
                current.add(point)
            }
            val trimmed = current.takeLast(40)
            val out = JSONArray()
            trimmed.forEach { p ->
                out.put(JSONObject().apply {
                    put("ts", p.timestampMillis)
                    put("used", p.usedBytes)
                    put("avail", p.availableBytes)
                })
            }
            prefs[Keys.STORAGE_HISTORY_JSON] = out.toString()
        }
    }

    suspend fun getPreviousAppCacheSnapshot(): Pair<Long, Map<String, Long>> {
        val prefs = context.cleanerDataStore.data.first()
        val ts = prefs[Keys.PREV_APP_CACHE_TS] ?: 0L
        val raw = prefs[Keys.PREV_APP_CACHE_JSON] ?: return Pair(ts, emptyMap())
        return Pair(ts, parseLongMap(raw))
    }

    suspend fun saveAppCacheSnapshot(timestampMillis: Long, map: Map<String, Long>) {
        context.cleanerDataStore.edit {
            it[Keys.PREV_APP_CACHE_TS] = timestampMillis
            it[Keys.PREV_APP_CACHE_JSON] = serializeLongMap(map)
        }
    }

    suspend fun getBaselineDiffSnapshot(): Pair<Long, Map<String, Long>> {
        val prefs = context.cleanerDataStore.data.first()
        val ts = prefs[Keys.BASELINE_DIFF_TS] ?: 0L
        val raw = prefs[Keys.BASELINE_DIFF_JSON] ?: return Pair(ts, emptyMap())
        return Pair(ts, parseLongMap(raw))
    }

    suspend fun saveBaselineDiffSnapshot(timestampMillis: Long, map: Map<String, Long>) {
        context.cleanerDataStore.edit {
            it[Keys.BASELINE_DIFF_TS] = timestampMillis
            it[Keys.BASELINE_DIFF_JSON] = serializeLongMap(map)
        }
    }

    private fun parseLongMap(raw: String): Map<String, Long> {
        return try {
            val obj = JSONObject(raw)
            val out = linkedMapOf<String, Long>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = obj.optLong(k, 0L)
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun serializeLongMap(map: Map<String, Long>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun parseStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { idx -> arr.getString(idx) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeStringList(items: List<String>): String {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun parseHistoryList(raw: String?): List<CleaningHistoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { idx ->
                val obj = arr.getJSONObject(idx)
                CleaningHistoryItem(
                    id = obj.optLong("id"),
                    timestampMillis = obj.optLong("timestampMillis"),
                    mode = obj.optString("mode"),
                    freedBytes = obj.optLong("freedBytes"),
                    filesDeleted = obj.optInt("filesDeleted"),
                    details = obj.optString("details"),
                    success = obj.optBoolean("success", true)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeHistoryList(items: List<CleaningHistoryItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestampMillis", item.timestampMillis)
                put("mode", item.mode)
                put("freedBytes", item.freedBytes)
                put("filesDeleted", item.filesDeleted)
                put("details", item.details)
                put("success", item.success)
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}
