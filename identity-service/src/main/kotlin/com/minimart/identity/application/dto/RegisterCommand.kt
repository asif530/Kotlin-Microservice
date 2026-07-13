package com.minimart.identity.application.dto

/** Application-layer input for account registration (ACC-001), decoupled from the web JSON shape. */
data class RegisterCommand(
    val email: String,
    val password: String,
    val fullName: String,
)
