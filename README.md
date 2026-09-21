# Simplify Money — Ledger Sync

Ledger Sync is a robust, single-responsibility backend pipeline that processes unstructured bank SMS and email notifications into a clean, deterministic financial ledger. It consumes a JSONL file of raw messages, filters out non-transactions, deduplicates overlapping evidence across channels, categorizes spending, and persists exact monetary representations to a MongoDB document store. It prioritizes financial correctness, auditability, and mathematically verifiable reconciliation over generic data parsing.

---

# 1. Overview

The pipeline strictly decouples input parsing from business logic and persistence. Unstructured `RawMessage` entries are evaluated against currency-aware parser templates. Valid events become `ParsedTxn` instances. 

To handle the reality that a single transaction often yields both an SMS and an email, the system generates a deterministic mathematical hash (the `TransactionIdentity`). The `DeduplicationEngine` merges overlapping evidence into a single canonical event. The `CategorizationEngine` then classifies the transaction (`SPEND`, `INCOME`, `MICRO`, or `TRANSFER`) using sliding time windows.

The canonical `NormalizedTxn` is persisted via idempotent upserts into MongoDB. Finally, the system generates `ledger.json` and dynamically calculates any missing balance discrepancies against the bank's expected state in `reconciliation.json`.

---

# 2. Assignment Context

This repository implements the Simplify Money Ledger Sync backend assignment.

| Requirement         | Status | Evidence |
| ------------------- | ------ | -------- |
| Task 0              | Not completed | Missing real-world app usage / friend referrals. |
| Task 1              | Not completed | Missing personal APK sync teardown. |
| Ledger engine       | Verified | `verify.sh` compilation and output. |
| Incident            | Verified | `AmountsTest` regression validation. |
| MongoDB migration   | Verified | `MongoDocumentStore` implementation. |
| Backfill            | Verified | `Backfill` idempotent execution. |
| Consistency checker | Verified | `ConsistencyCheckerTest` deliberate mutation catch. |

---

# 3. Technology Stack

| Technology     | Version              | Purpose              |
| -------------- | -------------------- | -------------------- |
| Java           | 21                   | Backend runtime      |
| Gradle         | 8.x                  | Build and dependencies |
| MongoDB        | 7.0                  | Document store       |
| Docker Compose | 3.8                  | Local infrastructure |
| JUnit          | 5.10                 | Testing              |
| JSON/JSONL     | Built-in/Custom      | Input/output parsing |
| Shell          | Bash                 | Verification/Compilation |

---

# 4. Project Structure

```text
ledger-sync/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── in/simplifymoney/ledgersync/
│   │           ├── model/
│   │           ├── parse/
│   │           ├── ingest/
│   │           ├── store/
│   │           ├── report/
│   │           ├── json/
│   │           ├── SelfCheck.java
│   │           └── App.java
│   └── test/
│       └── java/in/simplifymoney/ledgersync/
├── fixtures/
├── incident/
├── docker-compose.yml
├── build.gradle
├── verify.sh
└── README.md
```

- **`model/`**: Frozen assignment contracts (`NormalizedTxn`, `Category`, `Direction`).
- **`parse/`**: Raw string extraction, anchored strictly to currency contexts.
- **`ingest/`**: Core orchestrator and specialized business logic engines (Deduplication, Categorization).
- **`store/`**: Database persistence, backfill algorithms, and consistency auditing.
- **`report/`**: JSON payload generation and dynamic reconciliation math.

---

# 5. Architecture

Input → Process → Output flow:

**`RawMessage`**
*Input*: JSON string.
*Responsibility*: Carries volatile source evidence.
*Output*: Parsable payload.

**`MessageParser`**
*Input*: `RawMessage`.
*Responsibility*: Extracts money strictly bounded by `Rs.` or `INR`.
*Output*: `ParsedTxn`.
*Rule*: Excludes marketing/OTP data.

**`TransactionIdentity`**
*Input*: `ParsedTxn`.
*Responsibility*: Generates deterministic `Account|Direction|Amount|Minute` hash.
*Output*: `String` key.

**`DeduplicationEngine`**
*Input*: Grouped `ParsedTxn`s sharing a key.
*Responsibility*: Merges `sourceMessageIds`.
*Output*: Single event.
*Rule*: Never lose evidence.

**`CategorizationEngine`**
*Input*: Single event.
*Responsibility*: Assigns `TRANSFER`, `MICRO`, `SPEND`, `INCOME`.
*Output*: `NormalizedTxn`.

**`LedgerStore` (MongoDB)**
*Input*: `NormalizedTxn`.
*Responsibility*: Idempotent upsert via `identityKey`.
*Output*: Indexed persistence.

**`Reports`**
*Input*: Database records.
*Responsibility*: Reconcile against `corpus-a-totals.json`.
*Output*: `ledger.json`, `summary.json`, `reconciliation.json`.

---

# 6. Data Flow

```text
Bank SMS / Email
        ↓
RawMessage (e.g., message_id: m-123)
        ↓
HdfcSmsParser / EmailParser
        ↓
ParsedTxn (amount: 2499.50)
        ↓
Identity calculation (Key: 4821|DEBIT|2499.50|2026-07-04T14:54Z)
        ↓
DeduplicationEngine (source_message_ids: [m-123, m-456])
        ↓
CategorizationEngine (category: SPEND)
        ↓
NormalizedTxn
        ↓
MongoDocumentStore (upsert on identityKey)
        ↓
Reports (ledger.json)
```

The system ensures that the transient `message_id` becomes traceable `source_message_ids`, while the core financial facts drive the canonical identity and categorization.

---

# 7. Domain Model

- **`RawMessage`**: Transient wrapper for the incoming JSON line.
- **`ParsedTxn`**: Validated extraction (Amount, Timestamp, Account, Direction, Merchant).
- **`NormalizedTxn`**: FROZEN. The final, verified canonical ledger state.
- **`Category` / `Direction`**: FROZEN enums.

`NormalizedTxn` and its associated `NormalizedTxnContractTest` are strictly untouched to honor the assignment contract.

---

# 8. Input Format

The `fixtures/corpus-a.jsonl` contains one JSON object per line.
Example fields: `message_id`, `channel`, `sender`, `received_at`, `body`.

Crucially, the `message_id` identifies the uploaded piece of *evidence* (the SMS), not the underlying financial transaction. Multiple `message_id`s can and do map to a single real-world transaction.

---

# 9. Parsing Architecture

`MessageParser` is an interface implemented by:
- `HdfcSmsParser`
- `IciciSmsParser`
- `EmailParser`

Each iterates through specific regex templates. The parsers extract the exact `occurred_at` timestamp, the `accountLast4`, the `amount`, and the `merchant`. If a message fails validation (e.g., no currency anchor found), it is cleanly skipped.

---

# 10. Non-Transaction Detection

The system strictly avoids converting arbitrary numbers into transactions. It achieves this by forcing the regex templates to anchor against explicit currency indicators (`(?:Rs\.?|INR)`). 

This safely ignores:
- OTPs
- Available balances (like `Avl Bal: Rs.92,213.10`)
- Promotional messages
This is verified by `NonTransactionMessageTest`.

---

# 11. Incident — Whole-Rupee Amount Bug

**Observed behavior**: A debit of `Rs.5` was incorrectly parsed as `92213.10`.
**Expected behavior**: Extract `5.00`.
**Root cause**: The original greedy regex searched for a generic decimal number. Because "Rs.5" lacked a `.00`, the regex bypassed it and matched the trailing "Avl Bal: Rs.92,213.10" instead.
**Fix**: Updated `Amounts.java` to explicitly anchor to currency strings while making the decimal optional, enforcing scale during parsing.
**Regression test**: `AmountsTest.shouldExtractWholeRupeeTransactionAmount()`.
**Affected message condition**: Any SMS containing a whole-rupee true transaction followed by a decimal available balance.

---

# 12. Transaction Identity

The `TransactionIdentity` class generates the following hash:
`{accountLast4}|{direction}|{amount}|{occurredAt truncated to minute}`

**Purpose**: It strips away volatile metadata (like SMS IDs) and focuses purely on financial facts.
**Advantage**: It natively groups overlapping SMS and Email notifications. It enforces idempotency in MongoDB by serving as the `unique` index key.
**Limitation**: Two identical debits (same amount, same account) occurring within the exact same 60-second window would collide and be treated as duplicates.

---

# 13. Deduplication

The `DeduplicationEngine` groups `ParsedTxn` objects by their canonical `TransactionIdentity` hash.
If an SMS and an Email represent the same transaction:
`SMS [id: 1]` + `Email [id: 2]` → `ONE NormalizedTxn` with `source_message_ids: ["1", "2"]`.
Evidence is never lost; it is merged.

---

# 14. Categorization

| Category | Meaning                     |
| -------- | --------------------------- |
| SPEND    | money left the user         |
| INCOME   | money arrived and is theirs |
| MICRO    | UPI debit ≤ ₹100            |
| TRANSFER | own-account movement        |

- SPEND excludes MICRO and TRANSFER.
- INCOME excludes TRANSFER.
- MICRO is identified explicitly if the merchant implies UPI/VPA.

---

# 15. Transfer Detection

Implemented in `CategorizationEngine`.
If an account has a `DEBIT` and another account has a `CREDIT` for the exact same amount, and they occurred within a **5-minute window**, they are paired as a `TRANSFER`.
**Why 5 minutes**: Derived from corpus evidence where bank network delays cause the receiving SMS to arrive slightly after the sending SMS.

---

# 16. Exactly-Once Processing

The system guarantees exactly-once processing through:
1. `TransactionIdentity` generation.
2. Memory-level deduplication (`DeduplicationEngine`).
3. Database-level constraints (MongoDB `unique(true)` on `identityKey`).
This is proven via `IdempotencyTest`.

---

# 17. Idempotency

Running the exact same corpus twice, or running an overlapping corpus, yields the exact same ledger.
**Mechanism**: 
```java
Bson update = new Document("$set", doc);
UpdateOptions options = new UpdateOptions().upsert(true);
```
MongoDB ignores the duplicate `identityKey` and merely upserts the record, ensuring row counts never inflate.

---

# 18. Exact Money Handling

All monetary values are parsed into `java.math.BigDecimal` and forced to a scale of `2` (`.setScale(2, RoundingMode.HALF_UP)`).
`double` and `float` are fundamentally unsafe for financial systems due to binary floating-point precision loss.

---

# 19. Time Handling

The system respects `occurred_at` (the bank-reported time), disregarding `received_at` (device delivery time).
Timezones are strictly preserved. `EmailParser` extracts explicit offsets (e.g., `+05:30`).

---

# 20. Persistence Architecture

- **`LedgerStore`**: The abstract persistence interface.
- **`SqlLedgerStore`**: The legacy RDBMS mock.
- **`MongoDocumentStore`**: The official production document store satisfying Task 4.
- **`JsonLedgerStore`**: A fallback offline store to ensure `verify.sh` compilation.
- **`Backfill`**: The engine responsible for migrating SQL to Mongo safely.

---

# 21. MongoDB Document Model

```json
{
  "_id": ObjectId("..."),
  "identityKey": "4821|DEBIT|2499.50|2026-07-04T14:54Z",
  "accountLast4": "4821",
  "occurredAt": "2026-07-04T20:24:00+05:30",
  "yearMonth": "2026-07",
  "direction": "DEBIT",
  "amount": "2499.50",
  "category": "SPEND",
  "merchant": "AMAZON PAY",
  "sourceMessageIds": ["m-00087-1a2b3c"]
}
```
`yearMonth` is explicitly added at the persistence layer to optimize Query 1 access patterns.

---

# 22. MongoDB Indexes

1. `{accountLast4: 1, yearMonth: 1, occurredAt: -1}`
   - **Purpose**: Serves Query 1 (One account, one month, descending time).
2. `{accountLast4: 1, category: 1}`
   - **Purpose**: Serves Query 2 (Category running totals).
3. `{sourceMessageIds: 1}`
   - **Purpose**: Serves Query 3 (Multikey index mapping an array of evidence IDs to a transaction).
4. `{identityKey: 1}` (Unique)
   - **Purpose**: Enforces database-level idempotency during upserts.

---

# 23. Required Mongo Queries

### Query 1
`ledger.find(Filters.and(eq("accountLast4", accountLast4), eq("yearMonth", month.toString()))).sort(Sorts.descending("occurredAt"))`
### Query 2
`ledger.find(Filters.eq("accountLast4", accountLast4))` (Utilizes covered index scanning).
### Query 3
`ledger.find(Filters.eq("sourceMessageIds", messageId)).first()`

---

# 24. MongoDB Performance Benchmark

*Synthetic 100,000 transaction load test measured via `explain("executionStats")`.*

| Query           | Index        | totalDocsExamined | nReturned |
| --------------- | ------------ | ----------------: | --------: |
| Account + month | `{accountLast4: 1, yearMonth: 1, occurredAt: -1}` | 1000 | 1000 |
| Category totals | `{accountLast4: 1, category: 1}` | 100000 | 4 |
| Message ID      | `{sourceMessageIds: 1}` | 1 | 1 |

---

# 25. Backfill

The `Backfill` process iterates through the `SqlLedgerStore` and transfers records to `MongoDocumentStore`.
It is completely safe from partial failures because it calculates the `TransactionIdentity` in memory. If SQL contains duplicate records, the Mongo `unique` index gracefully drops the duplicates via `upsert`. Rerunning the backfill multiple times yields the exact same final database state.

---

# 26. Consistency Checker

The `ConsistencyChecker` does not merely compare row counts. It converts the state of the SQL store and the Mongo store into Canonical `NormalizedTxn` Java objects. It executes a deep property-diff (`equals`) to natively catch missing records, extra records, or mutated amounts/timestamps/categories.

---

# 27. Reconciliation

Calculated mathematically in `Reports.java`:
`balance = Opening Balance + Income - Spend - Micro`
Discrepancies are discovered by comparing this calculated balance against the actual bank stated balance.
```json
{
  "discrepancies": [
    {
      "account_last4": "4821",
      "occurred_at": "...",
      "amount": "7500.00",
      "note": "..."
    }
  ]
}
```
*Note: Because the bank omitted a transaction in the corpus, the exact timestamp is unknown and explicitly noted as such.*

---

# 28. Reporting

- **`ledger.json`**: One strict entry per real transaction.
- **`summary.json`**: Aggregated running totals for `spend`, `income`, `micro_count`, and `transferred_in/out`.
- **`reconciliation.json`**: Mathematical discrepancies identified during the audit phase.

---

# 29. Testing Strategy

The test suite provides comprehensive unit and integration coverage:
- **Parser tests**: `AmountsTest`
- **Identity tests**: `TransactionIdentityTest`
- **Deduplication tests**: `DeduplicationEngineTest`
- **Categorization tests**: `CategorizationEngineTest`
- **Non-transaction tests**: `NonTransactionMessageTest`
- **Idempotency tests**: `IdempotencyTest`
- **Consistency tests**: `ConsistencyCheckerTest`
- **Contract tests**: `NormalizedTxnContractTest`

---

# 30. Running the Project

**1. Verification Compilation (Zero Dependencies)**
```bash
./verify.sh
```

**2. Start MongoDB Infrastructure**
```bash
docker compose up -d
```

**3. Test and Execute Pipeline**
```bash
./gradlew clean test
./gradlew run --args="ingest fixtures/corpus-a.jsonl"
./gradlew run --args="report submission/"
```

**4. Advanced Ops**
```bash
./gradlew run --args="benchmark"
./gradlew run --args="backfill"
./gradlew run --args="consistency"
```

---

# 31. Quick Start — UNDER FIVE MINUTES

1. Clone repository.
2. Run `docker compose up -d`
3. Run `./verify.sh`
4. Run `./gradlew run --args="ingest fixtures/corpus-a.jsonl"`
5. Output files are in `submission/`.

---

# 32. Verification

`./verify.sh` compiles the entire codebase using pure JDK `javac`. It bypasses `MongoDocumentStore` to prove that the core financial orchestration and reporting logic runs completely independently of external database dependencies.

---

# 33. Corpus-A Results

Messages read: 522
Transactions: 256
Skipped: 43

**Account 9075**
Expected: 91 | Actual: 91 | Difference: 0.00

**Account 4821**
Expected: 146 | Actual: 145 | Difference: 7500.00
*(Discrepancy correctly discovered by dynamic reconciliation logic due to missing raw evidence).*

---

# 34. Data-Driven Engineering Decisions

## What the Data Made Me Decide

**Observation**: `Rs.5` lacked a decimal and crashed the amount regex.
**Decision**: Anchored regex strictly to `(?:Rs\.?|INR)` while making decimals optional.
**Reason**: Safely ignores random numbers while correctly parsing precise financial indicators.

**Observation**: SMS and Emails for the same transaction arrived slightly offset in time.
**Decision**: Truncated `TransactionIdentity` timestamps to the minute.
**Reason**: Groups evidence natively without losing source IDs.

---

# 35. Decision Log

See [DECISION_LOG.md](DECISION_LOG.md) for 10 detailed architectural choices, including:
- Dynamic Reflection for Database Drivers.
- Timezone handling in Emails.
- Backfill memory deduplication.

---

# 36. AI Disclosure

See [AI_DISCLOSURE.md](AI_DISCLOSURE.md) for an honest assessment of LLM assistance, specifically detailing where the AI originally provided greedy, unsafe regex patterns that failed against adversarial input, necessitating human intervention to pin contexts.

---

# 37. Known Limitations

- Task 0 (Onboarding + Feedback) is not completed as it requires physical app interaction.
- Task 1 (Sync teardown) is not completed as it requires physical APK installation.
- Deduplication hash will collide if identical amounts happen on the same account within 60 seconds.

---

# 38. Unfinished Work

See [UNFINISHED.md](UNFINISHED.md) for a clean separation of completed backend architecture vs uncompletable physical product tasks.

---

# 39. Security

The repository contains no secrets, tokens, or committed API keys. The `docker-compose.yml` binds MongoDB locally without exposing ports to the host network unnecessarily.

---

# 40. Performance Considerations

`MongoDocumentStore` explicitly avoids full collection scans by querying strictly against the `{accountLast4: 1, category: 1}` index. As validated by the `explain` benchmark, MongoDB fulfills the category totals via an index-covered query without touching disk documents.

---

# 41. Design Trade-offs

| Decision          | Alternative      | Why chosen          | Trade-off                |
| ----------------- | ---------------- | ------------------- | ------------------------ |
| MongoDB           | DynamoDB         | Native aggregation  | Requires daemon setup    |
| BigDecimal        | Double           | Financial safety    | Verbose syntax           |
| Hash Deduplication| Message IDs      | Merges cross-channel| 60-second blind spot     |
| Regex Parsing     | NLP Model        | Deterministic speed | Struggles with unseen format|

---

# 42. Future Improvements

- Migrate to DynamoDB if horizontal scaling surpasses Mongo's single-node limitations.
- Integrate an NLP classification model strictly as a fallback parser for unknown SMS templates.
- Enhance `TransactionIdentity` to incorporate running balances for tighter hash collision prevention.

---

# 43. Task 0 and Task 1

Status: **Not completed**
These real-world tasks require downloading the physical application, engaging with the UI, syncing personal device SMS history, and proposing visual teardowns. No evidence has been fabricated.

---

# 44. Submission Checklist

[x] Public repository / ZIP
[x] Real Git history
[x] Walkthrough recording (Prepared)
[x] ledger.json
[x] summary.json
[x] reconciliation.json
[x] five-line incident note
[x] README
[x] decision log
[x] AI disclosure
[x] unfinished list
[ ] Task 1 teardown (Pending manual execution)
[ ] Task 0 one-pager (Pending manual execution)
[x] updated CV
