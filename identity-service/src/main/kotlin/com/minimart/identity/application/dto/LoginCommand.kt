package com.minimart.identity.application.dto

/** Application-layer input for login, decoupled from the web JSON shape. */
data class LoginCommand(
    val email: String,
    val password: String,
)
