# MiniMart — Business Rules

**Status:** Locked, v1.0
**Author role:** Project owner / business analyst (this document)
**Date:** 2026-07-12
**Scope:** Business rules only — no technology, storage, or protocol is mentioned or implied here. For how these rules are implemented, see `Archive/Architecture/ARCHITECTURE.md`.
**Revision policy:** Per instruction, this document does not change once agreed. Any future business need discovered later is a new, separately numbered addendum — never a silent edit to a rule below. If a rule below turns out to be wrong, that is a decision for the business owner to make explicitly, not something to be inferred or reinterpreted from the code.

Every rule below is either (a) a structural necessity of the domain (stated plainly, no alternative made sense), or (b) a judgment call — marked **[Decision]** — with its rationale given. Three of the judgment calls were confirmed with you directly before being locked; they're marked **[Confirmed]**.

---

## 1. The four services and their business responsibility

These are the same four services already fixed in the architecture document. Each one is the **single, exclusive source of truth** for a specific set of business facts — no service stores or decides a fact that belongs to another.

| Service                  | Business responsibility                                                       | It alone decides...                                                                                 |
|--------------------------|-------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| **identity-service**     | Who a person is, whether they can sign in, what role they hold                | Account existence, credentials validity, role (Customer / Administrator), active/deactivated status |
| **catalog-service**      | What can be bought: products, price, description, category, stock             | Whether a product exists, its price, and whether enough stock exists to sell it                     |
| **order-service**        | What a customer ordered, when, at what price, and its status                  | Whether a checkout is accepted, an order's total, and an order's status                             |
| **notification-service** | The history of notifications sent to a customer as a result of order activity | That a notification event occurred and is recorded                                                  |

---

## 2. Roles

There are exactly two roles. **[Decision]** No third tier (e.g. a "moderator" or per-category admin) exists in this locked scope — nothing in the domain as defined needs one, and inventing one would be scope creep.

- **Customer** — the default role. Can browse the catalog, manage their own account, and place/view/cancel their own orders.
- **Administrator** — manages the catalog and customer accounts. Cannot be self-assigned.

---

## 3. Account Management (identity-service)

- **ACC-001.** Registering an account requires, at minimum: email address, password, full name.
- **ACC-002.** Email address is the unique identifier for an account. Uniqueness is enforced **case-insensitively** — `Foo@x.com` and `foo@x.com` are the same account. **[Decision]** Without this, a person could register two "different" accounts with what a human would consider the same address, which defeats the purpose of uniqueness.
- **ACC-003.** A registration request for an email address already in use is rejected.
- **ACC-004.** A new account is assigned the Customer role and is immediately active. There is no email-verification step — this system has no capability to send outbound email (see §7, Out of Scope), so requiring verification would create an account that can never be verified.
- **ACC-005.** A failed login attempt — wrong email, wrong password, or an email that isn't registered at all — is reported to the caller identically in every case. **[Decision]** The system must never reveal, via a different error message, whether a given email address has an account; that's an information leak about real people.
- **ACC-006.** An account has exactly one status at all times: **Active** or **Deactivated**.
- **ACC-007.** A Deactivated account cannot log in and cannot place new orders. Deactivation never alters or hides that account's past order history (see ORD-013).
- **ACC-008.** Only an Administrator can deactivate or reactivate an account. A Customer cannot deactivate their own account or anyone else's.
- **ACC-009.** Only an existing Administrator can promote another account to Administrator. There is no self-service path to becoming an Administrator.
- **ACC-010.** Because of ACC-009, the system must never end up with zero Administrators — that would be a permanent lockout with no way to grant the role to anyone. **[Decision]** Exactly one Administrator account exists from the moment the system is first stood up, before any customer has registered.
- **ACC-011.** A Customer can view and update their own profile (name). A Customer cannot view another customer's account details. An Administrator can view any account for support purposes.

---

## 4. Catalog Management (catalog-service)

- **CAT-001.** A product has, at minimum: name, description, category, unit price, stock count.
- **CAT-002.** Unit price must be strictly greater than zero. **[Decision]** This system has no concept of a free or promotional item (see §7) — a zero or negative price is always rejected.
- **CAT-003.** Stock count is a non-negative whole number. It can never go below zero.
- **CAT-004.** A product belongs to exactly one category. **[Decision]** Multi-category products are a reasonable feature but are not needed for this domain and are explicitly excluded (see §7) rather than left ambiguous.
- **CAT-005.** Product names do not have to be unique — two distinct products (e.g. two variants) may share a name. A product is identified by its own record, never by its name.
- **CAT-006.** Only an Administrator can create, edit, or deactivate a product. A Customer can only browse and search.
- **CAT-007. [Confirmed]** A product with zero stock remains visible to Customers in browsing/search, clearly marked as out of stock. It cannot be ordered while stock is zero.
- **CAT-008.** An Administrator can Deactivate a product. A Deactivated product is fully hidden from Customer browsing/search and cannot be ordered — regardless of its stock count. This is a distinct state from "out of stock" (CAT-007): out-of-stock is still visible, Deactivated is not.
- **CAT-009.** A product that has been part of at least one placed order can never be permanently deleted — only Deactivated (CAT-008). **[Decision]** Deleting it would break the historical record of past orders, which must remain accurate forever (see ORD-005). A product that has never been ordered may be removed outright.
- **CAT-010.** Changing a product's price, name, description, or category affects only future orders. It never retroactively changes what's recorded on an order that was already placed (see ORD-005).
- **CAT-011.** Catalog Management is the sole authority on current stock availability. No other service independently tracks or decides how much stock exists — a checkout must always get its stock answer from here, and here alone.

---

## 5. Order Placement (order-service)

- **ORD-001.** Only an authenticated, Active Customer can place an order. There is no guest checkout (see §7).
- **ORD-002.** An order must contain at least one line item. An order with zero line items cannot be placed.
- **ORD-003.** Each line item names one product and a quantity. Quantity must be a whole number of at least 1 — zero, negative, or fractional quantities are rejected. **[Decision]** This assumes every product is sold in discrete, countable units (not, e.g., sold by weight) — stated explicitly in §7.
- **ORD-004.** A given product can appear at most once per order. **[Decision]** Wanting more of the same product means a larger quantity on that one line item, not a second line item for the same product — avoids ambiguous "which line item is authoritative" questions.
- **ORD-005.** When an order is placed, the product's name and unit price **at that moment** are captured on the order and never change afterward, even if the catalog later changes that product's name or price (CAT-010). This is what makes CAT-009 safe and what keeps order history accurate forever.
- **ORD-006.** An order's total is exactly the sum of (captured unit price × quantity) across its line items. This system defines no taxes, shipping fees, discounts, or coupons (see §7) — nothing is added to or subtracted from that sum.
- **ORD-007.** Placing an order requires enough available stock, from Catalog Management (CAT-011), for **every** line item. If even one line item cannot be fully satisfied, the **entire order is rejected** — there is no partial order, and no stock is deducted for any line item in that attempt. **[Decision]** A partially-fulfilled order (some items shipped, some not, from a single checkout) is confusing for both the customer and the business, and nothing about this domain requires supporting it.
- **ORD-008.** A successful order placement immediately and permanently reduces the ordered products' stock by the ordered quantities. This happens exactly once per order.
- **ORD-009.** Resubmitting the exact same checkout a second time (e.g. a double-click, a network retry) must never result in two separate orders.
- **ORD-010.** An order has exactly one status at all times, drawn from a fixed list: **Placed, Confirmed, Shipped, Delivered, Cancelled**. **[Confirmed]** In this system's current scope, only **Placed** and **Cancelled** are ever actually assigned by any process. Confirmed, Shipped, and Delivered are reserved names for future capabilities (payment, fulfillment) that do not exist in this system — no rule in this document ever causes an order to reach one of those three statuses, and this document does not pretend otherwise.
- **ORD-011. [Confirmed]** A Customer can cancel their own order at any time while it is in **Placed** status. Since ORD-010 means Placed is the only status an order can be in before cancellation, this is, in effect, unrestricted self-service cancellation in this system's current scope. Cancelling an order that is already Cancelled, or that does not belong to the requester, is rejected.
- **ORD-012.** Cancelling an order restores the cancelled order's line-item quantities back to the corresponding products' available stock (Catalog Management, CAT-011, is the one that actually holds that number — see ORD-008).
- **ORD-013.** A Customer can view only their own orders and order history. An Administrator can view any customer's orders, for support purposes.
- **ORD-014.** An order's recorded buyer identity never changes. If that Customer's account is later Deactivated (ACC-007), their past orders remain exactly as they were, still attributed to them.

---

## 6. Notifications (notification-service)

- **NTF-001.** When an order is successfully placed, an "order placed" notification is recorded for that order's buyer.
- **NTF-002.** When an order is cancelled, an "order cancelled" notification is recorded for that order's buyer.
- **NTF-003.** A notification belongs to exactly one account and is visible only to that account. An Administrator may view any customer's notifications for support purposes.
- **NTF-004.** This system records notifications as an in-system, viewable history only. **[Decision]** It does not send email, SMS, or push notifications through any outside channel — this system has no outbound delivery capability, so promising real delivery would be a rule the system can't keep (see §7).
- **NTF-005.** A failure to record a notification never undoes, blocks, or delays the order placement or cancellation that triggered it. The order/cancellation is already final and correct on its own; the notification is a record of it, not a condition of it.

---

## 7. Global rules

- **GEN-001.** If two customers simultaneously try to order the last unit(s) of a product such that both requests together can't be satisfied, exactly one order succeeds and the other is rejected for insufficient stock (ORD-007). The losing attempt is not queued or automatically retried.
- **GEN-002.** All monetary amounts are in a single, fixed currency. **[Decision]** No multi-currency support, no currency conversion, anywhere in this system.
- **GEN-003.** All records (accounts, products, orders, notifications) are retained indefinitely. **[Decision]** This system defines no automatic deletion, archival, or "right to be forgotten" process — inventing specific retention periods or deletion rules with no stated legal/business basis would be guessing, so this is explicitly left undefined rather than assumed.

---

## 8. Explicitly out of scope

Stated here so nothing below gets silently assumed into existence later. If any of these become real requirements, that is new business scope, not a reinterpretation of a rule above.

- Payment processing of any kind (no payment method is captured, verified, or charged)
- Shipping, fulfillment, or delivery tracking
- Returns, refunds, or exchanges
- Discounts, coupons, promotions, or loyalty programs
- Multi-currency support or currency conversion
- Guest checkout (browsing is public; placing an order requires an account — ORD-001)
- Email verification at registration
- Password reset / "forgot password"
- Real outbound delivery of notifications (email/SMS/push) — recorded in-system only (NTF-004)
- Product reviews or ratings
- Search relevance/ranking beyond basic browsing and lookup
- Multi-category products or product variants (e.g. size/color as separately orderable entities)
- Any data retention or deletion policy beyond "everything is kept" (GEN-003)
- Products sold by non-discrete measure (e.g. weight, volume) — every product is a whole, countable unit (ORD-003)

---

## 9. Traceability to the architecture document

Two points where this document and `ARCHITECTURE.md` must agree, called out explicitly so a future edit to one doesn't silently break the other:

1. **Order status lookup values.** `ARCHITECTURE.md` §9 seeds a Postgres lookup table with `PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED` — this matches ORD-010 exactly, including the fact that three of the five are currently unreachable.
2. **Stock authority.** `ARCHITECTURE.md` §4 states catalog-service's `ReserveStock` call is the sole authority on stock, and `order-service` never independently tracks it — this matches CAT-011 and ORD-007/ORD-012 exactly (this document states the business rule; the architecture document states the mechanism).

The old single Java module in this repo is being **discarded**, per your decision — it implements none of the services or rules in this document and is not a starting point for any of them.
