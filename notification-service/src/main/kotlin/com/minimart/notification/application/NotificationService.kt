package com.minimart.notification.application

import com.minimart.notification.application.dto.ListNotificationsCommand
import com.minimart.notification.application.dto.RecordNotificationCommand
import com.minimart.notification.domain.exception.ForbiddenActionException
import com.minimart.notification.domain.model.Notification
import com.minimart.notification.domain.model.NotificationType
import com.minimart.notification.domain.model.RoleCode
import com.minimart.notification.domain.port.NotificationRepositoryPort
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Use-case interactor for Phase-7's notification recording and history
 * endpoints. Depends only on the domain's NotificationRepositoryPort
 * (constructor injection, no field injection), mirroring
 * identity-service/catalog-service/order-service's own Service style.
 */
@Service
class NotificationService(
    private val notificationRepository: NotificationRepositoryPort,
    private val meterRegistry: MeterRegistry,
) : NotificationUseCase {

    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    override fun recordNotification(command: RecordNotificationCommand): Notification {
        val notification = Notification(
            id = UUID.randomUUID(),
            accountId = command.accountId,
            orderId = command.orderId,
            type = command.type,
            message = messageFor(command.type),
            createdAt = Instant.now(),
        )

        val saved = notificationRepository.insert(notification)
        logger.info("Notification recorded: id={} accountId={} orderId={} type={}", saved.id, saved.accountId, saved.orderId, saved.type)
        meterRegistry.counter(METRIC_RECORD, "type", command.type.name).increment()
        return saved
    }

    override fun listNotifications(command: ListNotificationsCommand): List<Notification> {
        val effectiveAccountId = when {
            command.callerRole == RoleCode.ADMIN -> command.accountIdFilter
            command.accountIdFilter == null || command.accountIdFilter == command.callerId -> command.callerId
            else -> {
                logger.warn("Forbidden: account id={} requested another account's notification history (id={})", command.callerId, command.accountIdFilter)
                meterRegistry.counter(METRIC_LIST, "result", "forbidden").increment()
                throw ForbiddenActionException("You can only view your own notifications.")
            }
        }

        meterRegistry.counter(METRIC_LIST, "result", "success").increment()
        return notificationRepository.search(effectiveAccountId)
    }

    /** NTF-001/NTF-002: fixed, type-derived text — matches the Phase-7 doc's response examples verbatim. */
    private fun messageFor(type: NotificationType): String = when (type) {
        NotificationType.ORDER_PLACED -> "Your order has been placed."
        NotificationType.ORDER_CANCELLED -> "Your order has been cancelled."
    }

    private companion object {
        const val METRIC_RECORD = "notification.notifications.record"
        const val METRIC_LIST = "notification.notifications.list"
    }
}
