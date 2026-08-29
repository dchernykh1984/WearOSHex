package com.dchernykh.hex.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dchernykh.hex.game.BoardSize
import com.dchernykh.hex.game.Level
import com.dchernykh.hex.game.Mode
import com.dchernykh.hex.game.SwapRule
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * What survives closing the app: how the last game was set up.
 *
 * Hex keeps no records - a game against the watch is not scored, and two people at
 * one watch keep their own count - so this is settings and nothing else.
 *
 * An interface, because everything interesting happens above it: a JVM test drives
 * the view model against an in-memory implementation instead of an emulator.
 */
interface SettingsStore {
    suspend fun read(): Settings

    suspend fun write(settings: Settings)
}

/** How a game is set up. */
data class Settings(
    val mode: Mode = Mode.DEFAULT,
    val level: Level = Level.DEFAULT,
    val boardSize: BoardSize = BoardSize.DEFAULT,
    val swapRule: SwapRule = SwapRule.DEFAULT,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val MODE_KEY = stringPreferencesKey("mode")
private val LEVEL_KEY = stringPreferencesKey("level")
private val SIZE_KEY = stringPreferencesKey("boardSize")
private val SWAP_KEY = stringPreferencesKey("swapRule")

/**
 * The real store, on top of Preferences DataStore.
 *
 * Storage that has gone wrong must not stop anyone playing: a failed read reads as
 * nothing stored and a failed write is dropped, so a corrupt preferences file costs
 * a setting rather than the app.
 */
class DataStoreSettingsStore(
    context: Context,
) : SettingsStore {
    // The application context, not the activity's: a DataStore outlives any one
    // screen, and holding the activity here would leak it for the life of the app.
    private val dataStore = context.applicationContext.settingsDataStore

    override suspend fun read(): Settings {
        val stored =
            dataStore.data
                .catch { cause ->
                    // Only I/O. Anything else is a bug in this file rather than a
                    // broken disk, and swallowing it would hide it.
                    if (cause is IOException) emit(emptyPreferences()) else throw cause
                }.first()
        return Settings(
            mode = Mode.fromStoredName(stored[MODE_KEY]),
            level = Level.fromStoredName(stored[LEVEL_KEY]),
            boardSize = BoardSize.fromStoredName(stored[SIZE_KEY]),
            swapRule = SwapRule.fromStoredName(stored[SWAP_KEY]),
        )
    }

    override suspend fun write(settings: Settings) =
        edit {
            it[MODE_KEY] = settings.mode.name
            it[LEVEL_KEY] = settings.level.name
            it[SIZE_KEY] = settings.boardSize.name
            it[SWAP_KEY] = settings.swapRule.name
        }

    private suspend fun edit(change: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(change)
        } catch (_: IOException) {
            // Nothing to do and nothing worth saying: the game carries on.
        }
    }
}
