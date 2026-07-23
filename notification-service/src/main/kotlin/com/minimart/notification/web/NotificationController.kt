package com.minimart.notification.web

import com.minimart.notification.application.NotificationUseCase
import com.minimart.notification.application.dto.ListNotificationsCommand
import com.minimart.notification.domain.model.CallerPrincipal
import com.minimart.notification.web.dto.NotificationListResponse
import com.minimart.notification.web.dto.toNotificationResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Phase 7 — Notifications. Implements the one endpoint specified in
 * Archive/Development/Backend/Phase/Phase-7-Notifications.
 *
 * Kong verifies the token's signature and expiry at the gateway
 * (kong.decl.yaml's `notifications-list` route) but never role-based/
 * ownership authorization; that's entirely this service's own
 * responsibility, enforced in NotificationService (NTF-003), not here —
 * this controller only maps HTTP <-> the use-case boundary.
 */
@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationUseCase: NotificationUseCase,
) {

    private val logger = LoggerFactory.getLogger(NotificationController::class.java)

    @GetMapping
    fun listNotifications(caller: CallerPrincipal, @RequestParam(required = false) accountId: UUID?): ResponseEntity<NotificationListResponse> {
        logger.info("GET /api/notifications accountId={}", accountId)
        val notifications = notificationUseCase.listNotifications(
            ListNotificationsCommand(callerId = caller.accountId, callerRole = caller.role, accountIdFilter = accountId),
        )
        val items = notifications.map { it.toNotificationResponse() }
        return ResponseEntity.ok(NotificationListResponse(items = items, total = items.size))
    }
}
