package com.example.myapplication.domain.model

data class AppUser(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val isAnonymous: Boolean = false
) {
    val initials: String get() {
        val name = displayName?.trim() ?: email?.substringBefore("@") ?: return "?"
        return name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
            .ifEmpty { name.take(1).uppercase() }
    }
}
