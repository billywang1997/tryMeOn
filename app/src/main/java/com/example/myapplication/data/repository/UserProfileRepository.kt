package com.example.myapplication.data.repository

import com.example.myapplication.data.local.DataStoreManager
import com.example.myapplication.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

class UserProfileRepository(private val store: DataStoreManager) {

    fun getProfile(): Flow<UserProfile?> = store.profileFlow

    suspend fun getProfileOnce(): UserProfile? = store.getProfileOnce()

    suspend fun saveProfile(profile: UserProfile) = store.saveProfile(profile)
}
