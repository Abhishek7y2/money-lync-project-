# Simplify Money: Ledger Sync Architecture

This document provides a comprehensive breakdown of the entire Ledger Sync system we built, detailing how raw unstructured data from bank messages is transformed into a highly reliable and mathematically precise financial ledger.

## 📐 System Architecture & Data Flow

The system operates as a linear data pipeline that strictly enforces a separation of concerns. Evidence (messages) comes in, is parsed, deduplicated, categorized, and then permanently stored as normalized financial facts.

```mermaid
flowchart TD
    subgraph Input
        A[RawMessage (corpus.jsonl)]
    end

    subgraph Parsing Layer
        B[Parsers]
        C[IciciSmsParser]
        D[HdfcSmsParser]
        E[EmailParser]
        B --> C
        B --> D
        B --> E
        C --> F[ParsedTxn]
        D --> F
        E --> F
    end

    subgraph Normalization Engine
        F --> G[Identity Resolver / Deduplication]
        G --> H[Categorization Engine]
        H --> I[NormalizedTxn (Frozen Contract)]
    end

    subgraph Storage & Utilities
        I --> J[(JsonLedgerStore)]
        K[SqlLedgerStore (Legacy)] -.->|Backfill| J
        J <--> L[Consistency Checker] <--> K
    end

    subgraph Reporting
        J --> M[ledger.json]
        J --> N[summary.json]
        J --> O[reconciliation.json]
    end

    A --> B
```

---

## 🔍 Component Deep-Dive

### 1. The Parsing Layer (`in.simplifymoney.ledgersync.parse`)
**Goal:** Extract structured data (`ParsedTxn`) from unstructured raw text (`RawMessage`).
- **`EmailParser`**: Newly implemented to handle standard email structures. Crucially, it parses absolute timestamps (e.g., `+0530`) which are converted to absolute `Instant` values. This ensures that timezones don't break deduplication later on.
- **`IciciSmsParser`**: Upgraded to handle the "V2" multi-line format where messages span several lines instead of a continuous string.
- **`Amounts.java` (The Bug Fix)**: Previously, the regex `[0-9,]+\\.[0-9]{2}` strictly required `.00` at the end of a transaction. If a user spent exactly `Rs.5`, the regex failed and accidentally matched the trailing "Available Balance" instead. We updated the regex to make the decimal optional `(?:\\.[0-9]{2})?`, ensuring accurate extraction.

### 2. Identity & Deduplication (`in.simplifymoney.ledgersync.ingest`)
**Goal:** Prevent the system from recording the same transaction twice.
- **The Problem:** The same physical transaction often generates both an SMS and an Email. These arrive with different `message_id`s but represent the same event.
- **The Solution:** We designed a deterministic identity hash: `AccountLast4 | Direction | Amount | Timestamp (truncated to the minute)`.
- If an SMS and an Email share the same hash, they are merged into a single transaction, and both of their `sourceMessageIds` are combined into the evidence list.

### 3. Categorization Engine
**Goal:** Assign every unique transaction exactly one category (`SPEND`, `INCOME`, `MICRO`, or `TRANSFER`).
- **TRANSFER**: The engine uses a 5-minute sliding window to look for a `DEBIT` on one account and a `CREDIT` on another account for the exact same amount. If found, both legs are classified as a `TRANSFER`.
- **MICRO**: If a `DEBIT` is `<= 100.00` and contains "UPI" or "VPA" in the merchant name, it is classified as a `MICRO` spend (which gets rolled up in summaries).
- **SPEND/INCOME**: Everything else defaults to `SPEND` (if debit) or `INCOME` (if credit).

### 4. Storage (`in.simplifymoney.ledgersync.store`)
**Goal:** Persist the `NormalizedTxn` facts safely without external dependencies.
- **`JsonLedgerStore` (Document Store)**: We migrated off the legacy H2 SQL database and built a pure JSON embedded document store. This writes the entire ledger to `data/ledger/ledger.json`. By using a flat JSON file, we satisfied the assignment's strict requirement that the `./verify.sh` test suite must run with zero network/gradle dependencies.
- **`Backfill.java`**: Safely migrates legacy data from `SqlLedgerStore` to `JsonLedgerStore`. Since the old SQL store lacked uniqueness guarantees, the Backfill engine aggressively deduplicates the SQL rows in-memory before writing them to the new Document Store.
- **`ConsistencyChecker.java`**: A diagnostic utility that iterates through both the SQL and Document stores to prove they contain the exact same financial truth, flagging any missing, extra, or modified transactions.

### 5. Reporting (`in.simplifymoney.ledgersync.report`)
**Goal:** Output the final deliverables to the user.
- **Ledger & Summary**: Accurately sums up `SPEND` and `INCOME` independently of `TRANSFER` amounts, ensuring the user's balances aren't artificially inflated by moving their own money around.
- **Reconciliation**: We discovered that the bank's own internal balances had a `7500.00` discrepancy on Account `4821` due to a completely dropped message (no SMS or email was ever sent for a 7500.00 debit). The `reconciliation.json` report natively documents this gap honestly instead of fabricating data.
