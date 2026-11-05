# Ownership audit of the REST API

Question for every endpoint that takes an id from the path or body: *can user B read or change user A's data by sending
A's id?* Audited by reading each controller and the service it calls (5 Nov 2026).

## Fixed

| Endpoint | Problem | Fix |
|---|---|---|
| `GET /slips/{id}/image` | Returned any slip's image to any logged-in user (the id is a sequential number, so easy to enumerate). | Compares the slip's `userId` with the caller; another user's slip answers `404`, the same as a missing one, and the file is never read. |
| `POST /transactions` and the slip auto-transaction (`TransactionService.createTransaction`) | The category was looked up by id alone. The budget lookup that followed happened to be scoped to the caller, so the write failed, but with a different message ("Category" vs "Budget" not found), which revealed whether a category id exists. | Category looked up with `findByCategoryIdAndUserId`. |
| `POST` receiver mapping (`ReceiverMappingService.createOrUpdateMapping`) | `categoryId` was looked up by id alone and `debtId` was not checked at all. A user could map a receiver to someone else's category (its name, type and budget id then came back in `suggestedCategory`) or to someone else's debt id. | Category scoped to the caller; the debt must belong to the caller (`404` otherwise). |
| `DELETE` receiver mapping, `GET /notifications/logs/{id}/clicked` | Answered `403` for another user's id but `404` for a missing one, which confirms the id exists. | Mapping delete now answers `404` in both cases. (`logs/{id}/clicked` still answers `403`; its ids are random UUIDs, so it was left as is.) |
| `POST`/`DELETE` on `/debt-types`, `/repayment-types`, `/repayment-plans/strategies` | These tables are shared by all users, but any logged-in user could add or delete rows (deleting a debt type breaks every user's debts of that type). The mobile app only reads them. | Changes require an administrator: `AdminAccess` checks the caller's e-mail against `app.admin.emails` (env `ADMIN_EMAILS`, comma separated). **The default is empty, so nobody can change them through the API until it is set.** Reading is unchanged. |

Tests: `SlipControllerTest` (owner gets the image, other user gets 404 and the file is not read), `TransactionServiceTest`,
`ReceiverMappingServiceTest` (foreign category / debt rejected, foreign mapping not found), `AdminAccessTest`, and a
forbidden-for-non-admin test in each of the three controller tests.

## Checked, no change needed

| Endpoint(s) | Why it is fine |
|---|---|
| `GET /debts`, `/debts/debtId/{id}`, `PUT/DELETE /debts/{id}`, `GET /debts/{id}/summary`, `/debts/{id}/history`, `PUT /debts/priorities`, payments | Lookups use `findByDebtIdAndUserId`; priorities are applied to the caller's debts only. |
| `GET/PUT/DELETE /categories/{id}`, `GET /categories` | `findByCategoryIdAndUserId`. |
| `GET /transactions/category/{id}`, `PUT/DELETE /transactions/{id}` | Scoped to the caller (`findByUserIdAndCategoryCategoryId`, `findByTransactionIdAndUserId`). |
| `PUT /budgets/{id}/amount/{amount}`, `GET /budgets`, overview | `findByUserIdAndBudgetId` / `findByUserId`. |
| `GET /slips`, `GET /slips/{id}/status`, `POST /slips/upload`, `/upload-async` | Scoped to the caller; `status` answers 404 for other users' slips. |
| `DELETE /notifications/devices/{id}` | Ignores devices of other users (no change, no error). |
| `GET /notifications/logs*`, rules | Scoped by the caller's user id. |
| `GET/PUT /user-setting` | Always the caller's own record. |
| `POST /repayment-plans`, `GET /repayment-plans*`, `total-min-payment`, `priority-suggestion` | Plan and debts are looked up by the caller's user id; the strategy id refers to shared reference data. |

## Not changed, worth knowing

- Missing `Authorization` header: the security filter answers `401` before the controller; inside the controller tests (filters off) it surfaces as a 500, which is not reachable in production.
- There is still no role model. `ADMIN_EMAILS` is the smallest thing that closes the hole; a real role claim in the JWT would be the next step.
- `GET /notifications/logs/{id}/clicked` is a `GET` that changes state. Not an ownership issue, but it should be a `POST`/`PATCH`.
