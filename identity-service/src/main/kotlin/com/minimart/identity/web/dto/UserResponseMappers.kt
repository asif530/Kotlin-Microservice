package com.minimart.identity.web.dto

import com.minimart.identity.domain.model.Account

/** Maps the domain Account to Phase-2's response DTOs. Kept out of the domain/application layers on purpose. */

fun Account.toUserProfileResponse(): UserProfileResponse = UserProfileResponse(
    id = id.toString(),
    email = email,
    fullName = fullName,
    role = role.dbCode,
    status = status.name,
    createdAt = createdAt.toString(),
)

fun Account.toUpdateProfileResponse(): UpdateProfileResponse = UpdateProfileResponse(
    id = id.toString(),
    email = email,
    fullName = fullName,
    role = role.dbCode,
    status = status.name,
    updatedAt = updatedAt.toString(),
)

fun Account.toUpdateStatusResponse(): UpdateStatusResponse = UpdateStatusResponse(
    id = id.toString(),
    status = status.name,
    updatedAt = updatedAt.toString(),
)

fun Account.toUpdateRoleResponse(): UpdateRoleResponse = UpdateRoleResponse(
    id = id.toString(),
    role = role.dbCode,
    updatedAt = updatedAt.toString(),
)
