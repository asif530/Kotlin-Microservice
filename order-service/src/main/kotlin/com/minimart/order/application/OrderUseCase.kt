package com.minimart.order.application

import com.minimart.order.application.dto.CancelOrderCommand
import com.minimart.order.application.dto.GetOrderCommand
import com.minimart.order.application.dto.ListOrdersCommand
import com.minimart.order.application.dto.PlaceOrderCommand
import com.minimart.order.domain.model.Order
import com.minimart.order.domain.model.OrderSummary

/**
 * Inbound port (use-case boundary) for Phase-5/Phase-6's order endpoints.
 * The web layer (OrderController) depends on this interface, not on the
 * concrete OrderService, mirroring identity-service/catalog-service's own
 * Use-case/Service split.
 */
interface OrderUseCase {

    /**
     * ORD-001/ORD-002..ORD-009: places an order. Idempotent on
     * [PlaceOrderCommand.idempotencyKey] — retrying with the same key
     * returns the already-created order rather than creating a second one
     * or re-running the identity/catalog gRPC calls.
     *
     * @throws com.minimart.order.domain.exception.OrderValidationException
     *   if `items` is empty (ORD-002) or contains a duplicate product id (ORD-004).
     * @throws com.minimart.order.domain.exception.NotEligibleToOrderException
     *   if the caller is not a real, Active account (ORD-001).
     * @throws com.minimart.order.domain.exception.InsufficientStockException
     *   if any line item couldn't be reserved in the requested quantity (ORD-007).
     */
    fun placeOrder(command: PlaceOrderCommand): Order

    /**
     * ORD-013: a Customer can view only their own orders; an
     * Administrator can view any order.
     *
     * @throws com.minimart.order.domain.exception.OrderNotFoundException
     *   if no order exists with the given id, or it exists but doesn't
     *   belong to a non-admin caller (indistinguishable, by design).
     */
    fun getOrder(command: GetOrderCommand): Order

    /**
     * ORD-013: a Customer's own order history, or — for an Administrator
     * passing `?customerId=` — any one customer's history (support
     * lookup), or every customer's orders if an Administrator omits it.
     *
     * @throws com.minimart.order.domain.exception.ForbiddenActionException
     *   if a non-admin caller passes `?customerId=` for anyone but themselves.
     */
    fun listOrders(command: ListOrdersCommand): List<OrderSummary>

    /**
     * ORD-011/ORD-012: a Customer can cancel their own Placed order at any
     * time, which also restores its line items' quantities to the
     * corresponding products' available stock.
     *
     * @throws com.minimart.order.domain.exception.OrderNotFoundException
     *   if no order exists with the given id, or it exists but doesn't
     *   belong to the caller (indistinguishable, by design — same
     *   ORD-011/ORD-013 posture as getOrder).
     * @throws com.minimart.order.domain.exception.OrderNotCancellableException
     *   if the order is not currently in Placed status.
     */
    fun cancelOrder(command: CancelOrderCommand): Order
}
