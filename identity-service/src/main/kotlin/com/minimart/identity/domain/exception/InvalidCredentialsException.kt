package com.minimart.identity.domain.exception

/**
 * ACC-005: raised for every failed login, whatever the underlying cause
 * (wrong password, unregistered email, or a Deactivated account per
 * ACC-007). Deliberately carries no field that would let a caller further
 * up the stack distinguish which case occurred — that distinction must
 * never reach the HTTP response.
 */
class InvalidCredentialsException : RuntimeException("Email or password is incorrect.")
