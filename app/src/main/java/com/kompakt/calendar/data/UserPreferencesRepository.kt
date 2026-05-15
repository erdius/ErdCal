package com.kompakt.calendar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(
    private val context: Context
) {

    val showWeekNumbers: Flow<Boolean> = context.dataStore.data
        .map { it[SHOW_WEEK_NUMBERS] ?: false }

    val startWeekOnMonday: Flow<Boolean> = context.dataStore.data
        .map { it[START_WEEK_ON_MONDAY] ?: true }

    val defaultCalendarId: Flow<Long?> = context.dataStore.data
        .map { it[DEFAULT_CALENDAR_ID] }

    val defaultReminderMinutes: Flow<Int?> = context.dataStore.data
        .map { 
            val mins = it[DEFAULT_REMINDER_MINUTES] ?: 5
            if (mins == -1) null else mins
        }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { it[ONBOARDING_COMPLETED] ?: false }

    val useAmericanDateFormat: Flow<Boolean> = context.dataStore.data
        .map { it[USE_AMERICAN_DATE_FORMAT] ?: false }

    val startDestination: Flow<String> = context.dataStore.data
        .map { it[START_DESTINATION] ?: "calendar" }

    suspend fun saveShowWeekNumbers(value: Boolean) {
        context.dataStore.edit { it[SHOW_WEEK_NUMBERS] = value }
    }

    suspend fun saveStartWeekOnMonday(value: Boolean) {
        context.dataStore.edit { it[START_WEEK_ON_MONDAY] = value }
    }

    suspend fun saveDefaultCalendarId(id: Long) {
        context.dataStore.edit { it[DEFAULT_CALENDAR_ID] = id }
    }

    suspend fun saveDefaultReminderMinutes(minutes: Int?) {
        context.dataStore.edit { it[DEFAULT_REMINDER_MINUTES] = minutes ?: -1 }
    }

    suspend fun saveOnboardingCompleted(value: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETED] = value }
    }

    suspend fun saveUseAmericanDateFormat(value: Boolean) {
        context.dataStore.edit { it[USE_AMERICAN_DATE_FORMAT] = value }
    }

    suspend fun saveStartDestination(value: String) {
        context.dataStore.edit { it[START_DESTINATION] = value }
    }

    private companion object {
        val SHOW_WEEK_NUMBERS = booleanPreferencesKey("show_week_numbers")
        val START_WEEK_ON_MONDAY = booleanPreferencesKey("start_week_on_monday")
        val DEFAULT_CALENDAR_ID = longPreferencesKey("default_calendar_id")
        val DEFAULT_REMINDER_MINUTES = intPreferencesKey("default_reminder_minutes")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val USE_AMERICAN_DATE_FORMAT = booleanPreferencesKey("use_american_date_format")
        val START_DESTINATION = stringPreferencesKey("start_destination")
    }
}
