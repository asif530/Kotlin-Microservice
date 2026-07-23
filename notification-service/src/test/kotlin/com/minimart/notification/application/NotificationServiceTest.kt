package com.minimart.notification.application

import com.minimart.notification.application.dto.ListNotificationsCommand
import com.minimart.notification.application.dto.RecordNotificationCommand
import com.minimart.notification.application.testsupport.FakeNotificationRepository
import com.minimart.notification.domain.exception.ForbiddenActionException
import com.minimart.notification.domain.model.NotificationType
import com.minimart.notification.domain.model.RoleCode
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/** Pure unit tests for the Phase-7 use-case interactor — no Spring context, no database. */
class NotificationServiceTest {

    private lateinit var notificationRepository: FakeNotificationRepository
    private lateinit var notificationService: NotificationService

    private val customerId = UUID.randomUUID()
    private val otherCustomerId = UUID.randomUUID()
    private val adminId = UUID.randomUUID()
    private val orderId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        notificationRepository = FakeNotificationRepository()
        notificationService = NotificationService(notificationRepository, SimpleMeterRegistry())
    }

    // ---- recordNotification (NTF-001/NTF-002, scenarios 24/25) ------------------------------

    @Test
    fun `recordNotification for ORDER_PLACED uses the exact Phase-7 message text — NTF-001`() {
        val notification = notificationService.recordNotification(RecordNotificationCommand(customerId, orderId, NotificationType.ORDER_PLACED))

        assertEquals("Your order has been placed.", notification.message)
        assertEquals(NotificationType.ORDER_PLACED, notification.type)
        assertEquals(customerId, notification.accountId)
        assertEquals(orderId, notification.orderId)
    }

    @Test
    fun `recordNotification for ORDER_CANCELLED uses the exact Phase-7 message text — NTF-002`() {
        val notification = notificationService.recordNotification(RecordNotificationCommand(customerId, orderId, NotificationType.ORDER_CANCELLED))

        assertEquals("Your order has been cancelled.", notification.message)
        assertEquals(NotificationType.ORDER_CANCELLED, notification.type)
    }

    @Test
    fun `recordNotification persists the notification`() {
        notificationService.recordNotification(RecordNotificationCommand(customerId, orderId, NotificationType.ORDER_PLACED))

        assertEquals(1, notificationRepository.search(customerId).size)
    }

    // ---- listNotifications (NTF-003, scenarios 26/27) --------------------------------------

    @Test
    fun `listNotifications with no filter returns only the caller's own history`() {
        notificationService.recordNotification(RecordNotificationCommand(customerId, orderId, NotificationType.ORDER_PLACED))
        notificationService.recordNotification(RecordNotificationCommand(otherCustomerId, UUID.randomUUID(), NotificationType.ORDER_PLACED))

        val notifications = notificationService.listNotifications(ListNotificationsCommand(customerId, RoleCode.CUSTOMER, null))

        assertEquals(1, notifications.size)
        assertEquals(customerId, notifications.single().accountId)
    }

    @Test
    fun `listNotifications with the caller's own accountId filter is allowed`() {
        notificationService.recordNotification(RecordNotificationCommand(customerId, orderId, NotificationType.ORDER_PLACED))

        val notifications = notificationService.listNotifications(ListNotificationsCommand(customerId, RoleCode.CUSTOMER, customerId))

        assertEquals(1, notifications.size)
    }

    @Test
    fun `listNotifications with another account's id filter throws Forbidden`() {
        assertThrows(ForbiddenActionException::class.java) {
            notificationService.listNotifications(ListNotificationsCommand(customerId, RoleCode.CUSTOMER, otherCustomerId))
        }
    }

    @Test
    fun `listNotifications as an Administrator with an accountId filter returns that account's notifications — scenario 27`() {
        notificationService.recordNotification(RecordNotificationCommand(customerId, orderId, NotificationType.ORDER_PLACED))
        notificationService.recordNotification(RecordNotificationCommand(otherCustomerId, UUID.randomUUID(), NotificationType.ORDER_PLACED))

        val notifications = notificationService.listNotifications(ListNotificationsCommand(adminId, RoleCode.ADMIN, otherCustomerId))

        assertEquals(1, notifications.size)
        assertEquals(otherCustomerId, notifications.single().accountId)
    }

    @Test
    fun `listNotifications as an Administrator for an account with no events returns an empty list — scenario 17 correlation`() {
        val notifications = notificationService.listNotifications(ListNotificationsCommand(adminId, RoleCode.ADMIN, UUID.randomUUID()))

        assertEquals(0, notifications.size)
    }

    @Test
    fun `listNotifications as an Administrator with no filter returns every account's notifications`() {
        notificationService.recordNotification(RecordNotificationCommand(customerId, orderId, NotificationType.ORDER_PLACED))
        notificationService.recordNotification(RecordNotificationCommand(otherCustomerId, UUID.randomUUID(), NotificationType.ORDER_PLACED))

        val notifications = notificationService.listNotifications(ListNotificationsCommand(adminId, RoleCode.ADMIN, null))

        assertEquals(2, notifications.size)
    }

    @Test
    fun `listNotifications returns newest first`() {
        val first = notificationService.recordNotification(RecordNotificationCommand(customerId, orderId, NotificationType.ORDER_PLACED))
        Thread.sleep(5)
        val second = notificationService.recordNotification(RecordNotificationCommand(customerId, orderId, NotificationType.ORDER_CANCELLED))

        val notifications = notificationService.listNotifications(ListNotificationsCommand(customerId, RoleCode.CUSTOMER, null))

        assertEquals(listOf(second.id, first.id), notifications.map { it.id })
    }
}
