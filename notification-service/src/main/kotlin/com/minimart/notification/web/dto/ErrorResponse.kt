package com.minimart.notification.web.dto

/** The flat error envelope used project-wide: {"error":{"code":"...","message":"..."}}. */
data class ErrorResponse(val error: ErrorBody)

data class ErrorBody(val code: String, val message: String)
