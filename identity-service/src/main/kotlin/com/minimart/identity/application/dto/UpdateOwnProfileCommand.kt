package com.minimart.identity.application.dto

import java.util.UUID

/** Application-layer input for ACC-011's self-service profile update (PATCH /api/users/me), decoupled from the web JSON shape. */
data class UpdateOwnProfileCommand(
    val callerId: UUID,
    val fullName: String,
)
