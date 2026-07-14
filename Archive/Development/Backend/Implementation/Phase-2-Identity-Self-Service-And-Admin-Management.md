# Phase 2 — Identity self-service & admin management: implementation guide

Source: Archive/Development/Backend/Phase/Phase-2-Identity-Self-Service-And-Admin-Management
(the five endpoint specs, request/response bodies, and error cases this
implementation follows exactly). Business rules: Archive/BusinessRules/BUSINESS_RULES.md
§2, ACC-004 through ACC-011. Gateway contract this implementation was built
against (already merged, describes what Kong does and does not enforce):
Archive/Development/Backend/Implementation/Gateway/Request-001 §1.2.

Everything below is either a decision the Phase-2 doc/BUSINESS_RULES.md
already locked in (cited as such), or a judgment call this implementation
had to make because neither source specifies it (also called out as such,
explicitly, matching this repo's existing documentation convention).

---

## 1. What was built

All in `identity-service/`, no other module touched, no schema change — the
Phase-2 doc's own framing ("Builds on Phase 1's accounts table... only new
endpoints against rows that already exist") held throughout.

Five endpoints in a new `web/UserController.kt`:

| Method + path                  | Access                                                 | Source scenario        |
|--------------------------------|--------------------------------------------------------|------------------------|
| `GET /api/users/me`            | Any authenticated caller, own account                  | Scenario 4, ACC-011    |
| `PATCH /api/users/me`          | Any authenticated caller, own account, `fullName` only | Scenario 5, ACC-011    |
| `GET /api/users/{id}`          | Admin only, unconditionally                            | Scenario 6, ACC-011    |
| `PATCH /api/users/{id}/status` | Admin only                                             | Scenarios 7/8, ACC-008 |
| `PATCH /api/users/{id}/role`   | Admin only, promotion to ADMIN only                    | Scenario 9, ACC-009    |

Supporting layers, following the exact Clean Architecture boundaries Phase 1
already established (`AuthService`/`AuthController` pattern):

- **Domain**: `CallerPrincipal` (resolved identity: account id + role),
  `TokenVerifier` port, `UnauthenticatedException`/`ForbiddenActionException`/
  `AccountNotFoundException`. `AccountRepositoryPort` gained `findById`/`update`.
- **Application**: `UserAccountService` — all five endpoints' business logic,
  including the ACC-008/009/011 authorization gates.
- **Infrastructure**: `JwtTokenVerifierAdapter` — verifies the RS256 tokens
  `JwtTokenIssuerAdapter` issues, using `RsaKeyPairProvider`'s public key.
- **Web**: `UserController` plus request/response DTOs, a
  `JwtAuthenticationFilter` + `CallerPrincipalArgumentResolver` pair (§2
  below), and four new `GlobalExceptionHandler` cases.

### 1.1 The gap this phase had to fill: identity-service verifies nothing on its own, as of Phase 1

Phase 1 shipped two open routes (`register`, `login`) and never needed to
authenticate an inbound request. Kong (Gateway/Request-001 §1.2) verifies a
token's **signature and expiry** at the edge, but is explicit that it does
**not** decide **authorization** — "Each service must check the forwarded
role claim itself for every `(admin only)` endpoint" — and the only Kong
consumer configured is identity-service's own signing identity, not a
per-end-user consumer, so there is no `X-Consumer-*` header that could stand
in for "who is calling, and what's their role." identity-service therefore
had to gain its own, independent token verification for the first time in
this phase, not just an authorization layer on top of something Kong already
resolved.

### 1.2 What Kong protects vs. what this phase enforces itself

Confirmed directly against `gateway/kong.decl.yaml`: all five `/api/users/*`
routes carry the `jwt` plugin (signature + `exp` check only). None carry a
role/ACL plugin — that gate is 100% this implementation's own, inside
`UserAccountService`. A request that reaches `UserController` has therefore
already passed Kong's identity check (in a real deployment) or this
service's own equivalent check (`JwtAuthenticationFilter`, §2) when called
directly, as it was during local verification (§5) — the two checks are
independent and redundant by design, not sequential.

---

## 2. Authentication mechanism, and why it isn't `spring-boot-starter-security`

**Approach chosen: a single `OncePerRequestFilter` scoped to `/api/users/*`
via an explicit `FilterRegistrationBean`, paired with a
`HandlerMethodArgumentResolver` that injects a `CallerPrincipal` into
controller methods — no new dependency.**

Phase 1's `PasswordEncoderConfig` kdoc already records a deliberate decision
to keep identity-service's security footprint minimal because Kong owned all
authentication at the gateway. Phase 2 needed *identity-service itself* to
verify tokens for the first time (§1.1), which changes what's needed, not
that original decision. Two realistic ways to add it:

1. A lightweight, hand-rolled filter + argument resolver, using only
   `jjwt` (already a Phase-1 dependency, via `JwtTokenIssuerAdapter`) and
   plain `spring-web` (chosen).
2. Pull in the full `spring-boot-starter-security` filter chain
   (`SecurityFilterChain`, `AuthenticationManager`, etc.) for what is, in
   this phase, five routes with two access levels.

(1) was chosen: it's the narrower change for the actual requirement, adds no
new dependency, and Phase 1 already established the "minimal footprint"
precedent this follows rather than reverses. If a later phase needs a
genuinely richer authorization model (method-level `@PreAuthorize`,
multiple roles per route, etc.), that's the point to revisit this decision
— not invented here ahead of that need.

**How it fits together:**

- `JwtSecurityWebConfig` (`@Configuration`) registers `JwtAuthenticationFilter`
  against `/api/users/*` only, at `Ordered.HIGHEST_PRECEDENCE + 10` — nothing
  outside that path pattern is touched, so `/api/auth/register` and
  `/api/auth/login` are provably unaffected (confirmed in §5.2).
- The filter reads `Authorization: Bearer <token>`, delegates to
  `TokenVerifier` (`JwtTokenVerifierAdapter`), and on success stashes the
  resolved `CallerPrincipal` as a request attribute.
- `CallerPrincipalArgumentResolverConfig` (`WebMvcConfigurer`) registers
  `CallerPrincipalArgumentResolver`, which reads that attribute back and
  injects it into any controller method parameter typed `CallerPrincipal` —
  every method on `UserController` takes one.
- On failure (missing header, bad signature, expired, unparseable `sub`,
  unrecognized `role` claim), the filter delegates to the same
  `HandlerExceptionResolver` that backs `@RestControllerAdvice`, so a
  request rejected by the filter — which runs *outside* `DispatcherServlet`'s
  normal exception handling — still produces the identical
  `{"error":{"code":"UNAUTHORIZED",...}}` envelope every other error in this
  service uses.

**A real bug hit while wiring this, and the fix:** the first version of
`JwtSecurityWebConfig` both implemented `WebMvcConfigurer` and injected the
`HandlerExceptionResolver` bean it needs in its own constructor. Spring MVC
assembles that resolver by calling every registered `WebMvcConfigurer`'s
configuration hooks — so a class that is itself a `WebMvcConfigurer` *and*
depends on the resolver those hooks help build is a circular dependency.
Confirmed directly: every integration test failed with
`BeanCurrentlyInCreationException` until the filter-registration concern
(`JwtSecurityWebConfig`, needs the resolver) and the argument-resolver
registration (`CallerPrincipalArgumentResolverConfig`, needs to *be* a
`WebMvcConfigurer`) were split into two separate configuration classes,
breaking the cycle.

---

## 3. Authorization ordering — role checked before existence

Every admin-only method in `UserAccountService`
(`getAccountForAdmin`/`updateAccountStatus`/`promoteToAdmin`) calls a shared
`requireAdmin(...)` gate *before* looking up the target account by id. This
means a non-admin caller gets the same 403 whether the target id exists or
not — they learn nothing about which account ids are valid. Not stated
explicitly by the Phase-2 doc (its 403 examples all use an id that does
exist), but a direct consequence of ACC-011's "A Customer cannot view
another customer's account details" read as a confidentiality rule, not
just an authorization rule: leaking existence via a 404-vs-403 timing/status
difference would itself be a (small) account-detail disclosure to someone
with no authorization to any of it.

---

## 4. Judgment calls where the source docs were silent

Stated plainly, matching this repo's existing convention for undocumented
decisions (see Gateway/Request-001 §3 for the precedent):

- **`401 UNAUTHORIZED`** for every authentication failure (missing header,
  malformed/expired/unsigned token, unparseable `sub`, unrecognized `role`
  claim) — one undifferentiated cause and message
  (`"Authentication is required."`), deliberately mirroring
  `InvalidCredentialsException`'s ACC-005 "single cause, no detail" pattern
  from Phase 1: telling a caller *which* part of their token was wrong is a
  minor information leak about the verification mechanism with no
  legitimate use to a genuine caller.
- **`404 ACCOUNT_NOT_FOUND`** for `GET`/`PATCH .../{id}` against an id that
  doesn't exist — not shown in the Phase-2 doc's examples at all. Chosen to
  mirror `EMAIL_ALREADY_REGISTERED`'s existing resource-oriented error-code
  naming style.
- **`400 VALIDATION_ERROR`** for a syntactically invalid path-variable UUID
  (e.g. `/api/users/not-a-uuid/status`) — previously an unhandled
  `MethodArgumentTypeMismatchException` that would have fallen through to
  the catch-all 500 handler; added its own handler so it degrades to the
  same validation-error envelope as every other malformed-request case.
- **`role: "ADMIN"` is the only value `PATCH /api/users/{id}/role` accepts**
  — including rejecting the otherwise-valid role code `"CUSTOMER"` as a 400.
  ACC-009 defines promotion only ("only an existing Administrator can
  promote another account to Administrator"); no rule anywhere describes
  demotion, so none is implemented. Confirmed live in §5.2: a demotion
  attempt gets `400 VALIDATION_ERROR`, not a silent no-op or an invented
  success response.
- **No self-action restriction beyond what the doc's own 403 examples show**
  — an admin can change their own status, and an admin "promoting" an
  already-ADMIN account (including themselves) succeeds idempotently.
  Neither ACC-008 nor ACC-009 restricts an Administrator acting on their own
  account; ACC-008's restriction is stated as applying to a Customer
  specifically. Not extended beyond what's written.
- **A verified JWT's `role` claim is trusted as-is for the lifetime of the
  token; it is not re-checked against the database on every request.** A
  consequence, confirmed live in §5.2: an admin demoted mid-session (were
  demotion to exist) or deactivated mid-session keeps working until their
  token expires (`expiration-seconds`, default 3600s) — because no
  token-revocation mechanism exists anywhere in this system, and none was
  asked for here. This is the same trust model Kong itself uses (verify
  signature + expiry, nothing else, per-request) — this implementation does
  not invent a stronger guarantee Kong doesn't already provide.

---

## 5. How to run and verify locally

### 5.1 What this phase does NOT yet solve: bootstrapping the first Administrator

ACC-010 requires "exactly one Administrator account exists from the moment
the system is first stood up, before any customer has registered." Nothing
in Phase 1 or Phase 2 creates that account — Phase 1's `register` endpoint
always assigns `CUSTOMER` (ACC-004), and every Phase-2 admin-only route
requires an existing Administrator's token to call it. This is a real,
known gap in the current state of the two merged phases, not something this
change was asked to close (no seed-data/bootstrap request exists in either
phase's source doc) — flagged here rather than silently worked around. The
verification below reproduces the same workaround a real first-deployment
would need: promoting the very first Administrator directly against the
database, once, out of band.

### 5.2 What was actually verified, and the real results

Run against a live `identity-service` (`./gradlew :identity-service:bootRun`
from repo root) talking to the existing `kotlincrud-postgres` container
(already running via `docker compose up -d postgres`, no other services
required — `catalog-service`/`order-service`/`notification-service` have no
dependency on this phase, matching the Phase-2 doc's own framing). Two
throwaway accounts, `bob.p2@example.test` (Customer) and
`carol.p2@example.test` (bootstrapped to Administrator via a single
one-off `UPDATE accounts SET role_id = 1` — see §5.1), were created,
exercised through every endpoint below, and deleted afterward; no
persistent change to the dev database was left behind.

```
GET  /api/users/me                          (Bob's own token)       -> 200, own profile
GET  /api/users/me                          (no Authorization)      -> 401 UNAUTHORIZED
PATCH /api/users/me   {"fullName":"..."}    (Bob's own token)       -> 200, fullName updated
GET  /api/users/{bobId}                     (Carol/admin token)     -> 200, Bob's profile
GET  /api/users/{bobId}                     (Bob's own token)       -> 403 FORBIDDEN
                                                "You can only view your own account."
PATCH /api/users/{bobId}/status {"status":"DEACTIVATED"}  (admin)   -> 200, status DEACTIVATED
PATCH /api/users/{bobId}/status {"status":"ACTIVE"}       (Bob)     -> 403 FORBIDDEN
                                                "Only an Administrator can change an account's status."
POST  /api/auth/login  (Bob, while DEACTIVATED)                     -> 401 INVALID_CREDENTIALS
                                                (regression check the Phase-2 doc calls for, §"Regression
                                                check against Phase 1" — confirmed live, not just by
                                                the pre-existing unit/integration tests)
PATCH /api/users/{bobId}/status {"status":"ACTIVE"}       (admin)   -> 200, status ACTIVE
POST  /api/auth/login  (Bob, after reactivation)                    -> 200, new token issued
PATCH /api/users/{bobId}/role {"role":"ADMIN"}            (Bob)     -> 403 FORBIDDEN
                                                "Only an Administrator can grant the Administrator role."
PATCH /api/users/{bobId}/role {"role":"ADMIN"}            (admin)   -> 200, role ADMIN
PATCH /api/users/{bobId}/role {"role":"CUSTOMER"}         (admin)   -> 400 VALIDATION_ERROR
                                                (demotion correctly rejected — see §4)
GET  /api/users/00000000-...-000000000000                 (admin)   -> 404 ACCOUNT_NOT_FOUND
PATCH /api/users/not-a-uuid/status {"status":"ACTIVE"}    (admin)   -> 400 VALIDATION_ERROR
PATCH /api/users/{bobId}/status {"status":"ACTIVE"} (Authorization: Bearer garbage.token.value)
                                                                     -> 401 UNAUTHORIZED
```

Every response body matched the Phase-2 doc's examples exactly where the
doc specifies one, and every judgment-call case in §4 behaved as documented
there. `/api/auth/register` and `/api/auth/login` were exercised throughout
(account creation, the deactivated-login regression, the post-reactivation
login) and never touched by the new filter, confirming the "unchanged, no
route/request/response shape here touches them" claim in the Phase-2 doc's
own regression section.

### 5.3 Automated tests

`./gradlew :identity-service:test` — **88 tests, 0 failures, 0 errors**,
across 10 test classes, including the new `UserAccountServiceTest`,
`JwtTokenVerifierAdapterTest`, `UserControllerIntegrationTest`, and
`UserRequestValidationTest`, plus every pre-existing Phase-1 test (including
`AuthServiceTest`'s and `AuthControllerIntegrationTest`'s deactivated-login
cases) unchanged and still passing.

---

## 6. Dev-placeholder vs. what a real deployment needs to change

| This implementation                                                                                           | What a real deployment needs instead                                                                                                                                                                                                                                                                   |
|---------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| First Administrator bootstrapped by a manual `UPDATE accounts SET role_id = 1` against the database (§5.1)    | A real provisioning step (seed migration, ops runbook, or an out-of-band admin CLI) that satisfies ACC-010 automatically at first stand-up — not designed here, since neither Phase-1 nor Phase-2's source docs ask for one.                                                                           |
| JWT `role` claim trusted for the token's full lifetime, no revocation (§4)                                    | A token-revocation or short-lived-token-plus-refresh strategy, if "deactivate/demote takes effect immediately, even for an already-issued token" ever becomes a real requirement — not currently required by ACC-007/008, which describe the *account's* state, not a claim about live-token behavior. |
| Hand-rolled `OncePerRequestFilter` or dependency, kept intentionally minimal per Phase 1's own precedent (§2) | If a later phase needs multi-role/method-level authorization rules richer than "any authenticated caller" vs. "admin only," `spring-boot-starter-security`'s `SecurityFilterChain` becomes worth its added footprint — revisit then, not preemptively here.                                            |
