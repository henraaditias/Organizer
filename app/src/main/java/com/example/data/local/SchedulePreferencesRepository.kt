package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.DestinationConfig
import com.example.domain.ScheduleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.scheduleDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "organizer_schedule_prefs"
)

class SchedulePreferencesRepository(private val context: Context) {

    private object Keys {
        val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        val INTERVAL_HOURS = intPreferencesKey("interval_hours")
        val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        val REQUIRE_BATTERY_NOT_LOW = booleanPreferencesKey("require_battery_not_low")
        val REQUIRE_CHARGING = booleanPreferencesKey("require_charging")
        val REQUIRE_DEVICE_IDLE = booleanPreferencesKey("require_device_idle")
        val AUTO_ORGANIZE_ON_LAUNCH = booleanPreferencesKey("auto_organize_on_launch")
        val DESTINATION_TREE_URI = stringPreferencesKey("destination_tree_uri")
        val DESTINATION_DISPLAY_NAME = stringPreferencesKey("destination_display_name")
    }

    val scheduleConfig: Flow<ScheduleConfig> = context.scheduleDataStore.data.map { prefs ->
        val legacyHours = prefs[Keys.INTERVAL_HOURS] ?: 24
        val minutes = prefs[Keys.INTERVAL_MINUTES] ?: (legacyHours * 60)
        ScheduleConfig(
            enabled = prefs[Keys.SCHEDULE_ENABLED] ?: false,
            intervalHours = (minutes / 60).coerceAtLeast(1),
            intervalMinutes = minutes.coerceAtLeast(15),
            requireBatteryNotLow = prefs[Keys.REQUIRE_BATTERY_NOT_LOW] ?: true,
            requireCharging = prefs[Keys.REQUIRE_CHARGING] ?: false,
            requireDeviceIdle = prefs[Keys.REQUIRE_DEVICE_IDLE] ?: false,
            autoOrganizeOnLaunch = prefs[Keys.AUTO_ORGANIZE_ON_LAUNCH] ?: false
        )
    }

    val destinationConfig: Flow<DestinationConfig> = context.scheduleDataStore.data.map { prefs ->
        DestinationConfig(
            treeUri = prefs[Keys.DESTINATION_TREE_URI],
            displayName = prefs[Keys.DESTINATION_DISPLAY_NAME]
        )
    }

    suspend fun getScheduleConfigSync(): ScheduleConfig = scheduleConfig.first()

    suspend fun getDestinationConfigSync(): DestinationConfig = destinationConfig.first()

    suspend fun updateScheduleConfig(config: ScheduleConfig) {
        context.scheduleDataStore.edit { prefs ->
            prefs[Keys.SCHEDULE_ENABLED] = config.enabled
            prefs[Keys.INTERVAL_HOURS] = config.intervalHours
            prefs[Keys.INTERVAL_MINUTES] = config.intervalMinutes
            prefs[Keys.REQUIRE_BATTERY_NOT_LOW] = config.requireBatteryNotLow
            prefs[Keys.REQUIRE_CHARGING] = config.requireCharging
            prefs[Keys.REQUIRE_DEVICE_IDLE] = config.requireDeviceIdle
            prefs[Keys.AUTO_ORGANIZE_ON_LAUNCH] = config.autoOrganizeOnLaunch
        }
    }

    suspend fun setCustomDestination(treeUri: String?, displayName: String?) {
        context.scheduleDataStore.edit { prefs ->
            if (treeUri == null || displayName == null) {
                prefs.remove(Keys.DESTINATION_TREE_URI)
                prefs.remove(Keys.DESTINATION_DISPLAY_NAME)
            } else {
                prefs[Keys.DESTINATION_TREE_URI] = treeUri
                prefs[Keys.DESTINATION_DISPLAY_NAME] = displayName
            }
        }
    }
}
