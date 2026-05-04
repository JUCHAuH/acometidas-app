package com.jucha.acometidasapp.core.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull

private val Context.sessionDataStore by preferencesDataStore(name = "session")

object SessionPreferences {

    private val USER_ID = stringPreferencesKey("user_id")
    private val USER_NAME = stringPreferencesKey("user_name")
    private val USER_USUARIO = stringPreferencesKey("user_usuario")
    private val USER_ROL = stringPreferencesKey("user_rol")

    suspend fun saveSession(
        context: Context,
        userId: String,
        userName: String,
        userUsuario: String,
        userRol: String
    ) {
        context.sessionDataStore.edit { preferences ->
            preferences[USER_ID] = userId
            preferences[USER_NAME] = userName
            preferences[USER_USUARIO] = userUsuario
            preferences[USER_ROL] = userRol
        }
    }

    suspend fun loadSession(context: Context): UserSession? {
        return try {
            val preferences = context.sessionDataStore.data.firstOrNull() ?: return null
            val userId = preferences[USER_ID] ?: return null
            val userName = preferences[USER_NAME] ?: return null
            val userUsuario = preferences[USER_USUARIO] ?: return null
            val userRol = preferences[USER_ROL] ?: return null

            UserSession(userId, userName, userUsuario, userRol)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun clearSession(context: Context) {
        context.sessionDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    data class UserSession(
        val id: String,
        val nombre: String,
        val usuario: String,
        val rol: String
    )
}
