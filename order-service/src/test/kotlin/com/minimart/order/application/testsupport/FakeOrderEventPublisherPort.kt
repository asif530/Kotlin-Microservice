package com.minimart.order.application.testsupport

import com.minimart.order.domain.model.Order
import com.minimart.order.domain.port.OrderEventPublisherPort
import java.util.concurrent.CopyOnWriteArrayList

/** In-memory test double for OrderEventPublisherPort — records every published order for assertions. */
class FakeOrderEventPublisherPort : OrderEventPublisherPort {

    val published: MutableList<Order> = CopyOnWriteArrayList()
    val cancelled: MutableList<Order> = CopyOnWriteArrayList()

    override fun publishOrderPlaced(order: Order) {
        published += order
    }

    override fun publishOrderCancelled(order: Order) {
        cancelled += order
    }
}
