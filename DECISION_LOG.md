# Simplify Money - Decision Log

### 1. MongoDB vs DynamoDB
**Considered**: DynamoDB, MongoDB, JSON Flat File
**Decided**: MongoDB
**Why**: The assignment requires capturing `totalDocsExamined` vs `nReturned`. MongoDB's explicit indexing mechanisms (Compound + Multikey) make optimizing and proving this O(1) fetch capability extremely transparent. 

### 2. Transaction Identity Strategy
**Considered**: Hashing by `sourceMessageId` vs `Account + Amount + Minute`.
**Decided**: Hash by mathematical facts (`AccountLast4|Direction|Amount|Minute`)
**Why**: The core problem was deduplicating an SMS and an Email that describe the same real-world event. By ignoring the volatile message IDs and generating an identity string from the deterministic facts, the `DeduplicationEngine` reliably merges evidence.

### 3. Verification Script Constraint (`./verify.sh`)
**Considered**: Hard-importing MongoDB libraries vs Java Reflection
**Decided**: Java Reflection for Mongo loading.
**Why**: `verify.sh` compiles purely with `javac` and JDK 21 (0 dependencies). A hard import of `com.mongodb` would break compilation. I used reflection in `App.java` to dynamically boot Mongo if running via Gradle, but gracefully fall back to `JsonLedgerStore` during offline verification.

### 4. Transfer Detection Window
**Considered**: Checking all historical transactions vs a sliding window.
**Decided**: ±5 minute sliding window.
**Why**: Inspection of the supplied corpus showed that matching debit and credit legs for self-transfers occur within a short time window. I chose a five-minute window as a bounded matching rule and validated it against the corpus.

### 5. MICRO Classification
**Considered**: Thresholding purely by amount vs Context + Amount.
**Decided**: A transaction is `MICRO` if it's a `DEBIT`, `<=` 100.00, and explicitly contains "UPI" or "VPA" in the merchant string.
**Why**: The spec defines `MICRO` strictly as UPI <= 100. Non-UPI small spends are still regular `SPEND`s.

### 6. Backfill Idempotency Strategy
**Considered**: Blind inserts vs Idempotent Upserts.
**Decided**: Idempotent Upserts keyed by `identityKey`.
**Why**: If the Backfill script dies at 50%, a rerun needs to safely re-process 1-50 without creating duplicate documents. 

### 7. Consistency Checker Logic
**Considered**: Checking row counts vs field-level comparison.
**Decided**: Transaction-by-transaction canonical comparison.
**Why**: A count checker passes if SQL has 1000 docs and Mongo has 1000 docs, even if amounts differ. Our `ConsistencyChecker` serializes both states to canonical `NormalizedTxn` models and diffs them.

### 8. Reconciliation Behavior
**Considered**: Hardcoding the ₹7,500 difference vs dynamic discovery.
**Decided**: Dynamic discrepancy discovery.
**Why**: The reconciliation engine computes the closing balance from the canonical ledger and compares it with the supplied balance checkpoint. The observed ₹7,500 discrepancy for account 4821 is therefore discovered from the data rather than hardcoded.

### 9. Amount Parsing Strategy
**Considered**: Generic number extraction vs currency-context extraction.
**Decided**: Currency-context extraction.
**Why**: The incident demonstrated that blindly selecting numeric tokens can confuse transaction amounts with balances.

### 10. Transaction Timestamp Normalization
**Considered**: Message `received_at` vs bank transaction timestamp.
**Decided**: Bank-reported transaction timestamp.
**Why**: The assignment defines `occurred_at` as when the bank says the transaction occurred, not when the phone received the message.
