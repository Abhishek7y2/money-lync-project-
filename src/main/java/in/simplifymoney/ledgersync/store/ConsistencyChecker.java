package in.simplifymoney.ledgersync.store;

import java.util.List;

/**
 * Proves the two stores agree, and says precisely where they do not.
 *
 * NOT IMPLEMENTED - this is yours.
 *
 * We will run your checker against a document store we have deliberately
 * altered. It has to find what we changed and name it. A checker that only
 * compares row counts will not.
 */
public final class ConsistencyChecker {

    private final SqlLedgerStore sql;
    private final DocumentStore documents;

    public ConsistencyChecker(SqlLedgerStore sql, DocumentStore documents) {
        this.sql = sql;
        this.documents = documents;
    }

    public List<Divergence> check() {
        List<Divergence> divergences = new java.util.ArrayList<>();
        
        List<in.simplifymoney.ledgersync.model.NormalizedTxn> sqlRows = sql.all();
        
        // Deduplicate SQL rows just like backfill did
        java.util.Map<String, List<in.simplifymoney.ledgersync.model.NormalizedTxn>> grouped = new java.util.LinkedHashMap<>();
        for (in.simplifymoney.ledgersync.model.NormalizedTxn t : sqlRows) {
            String key = identityKey(t);
            grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(t);
        }

        List<in.simplifymoney.ledgersync.model.NormalizedTxn> deduplicatedSql = new java.util.ArrayList<>();
        for (List<in.simplifymoney.ledgersync.model.NormalizedTxn> group : grouped.values()) {
            in.simplifymoney.ledgersync.model.NormalizedTxn primary = group.get(0);
            java.util.Set<String> mergedIds = new java.util.TreeSet<>();
            for (in.simplifymoney.ledgersync.model.NormalizedTxn t : group) {
                mergedIds.addAll(t.sourceMessageIds());
            }
            deduplicatedSql.add(new in.simplifymoney.ledgersync.model.NormalizedTxn(
                    primary.accountLast4(),
                    primary.occurredAt(),
                    primary.direction(),
                    primary.amount(),
                    primary.category(),
                    primary.merchant(),
                    new java.util.ArrayList<>(mergedIds)
            ));
        }

        // Gather all accounts and months from SQL
        java.util.Set<String> accounts = new java.util.HashSet<>();
        java.util.Set<java.time.YearMonth> months = new java.util.HashSet<>();
        for (in.simplifymoney.ledgersync.model.NormalizedTxn t : deduplicatedSql) {
            accounts.add(t.accountLast4());
            months.add(java.time.YearMonth.from(t.occurredAt()));
        }

        // Compare
        for (String account : accounts) {
            for (java.time.YearMonth month : months) {
                List<in.simplifymoney.ledgersync.model.NormalizedTxn> sqlTxns = deduplicatedSql.stream()
                        .filter(t -> t.accountLast4().equals(account))
                        .filter(t -> java.time.YearMonth.from(t.occurredAt()).equals(month))
                        .toList();

                List<in.simplifymoney.ledgersync.model.NormalizedTxn> docTxns = documents.forAccountMonth(account, month);
                
                // Fast path: if equal, skip
                if (sqlTxns.containsAll(docTxns) && docTxns.containsAll(sqlTxns) && sqlTxns.size() == docTxns.size()) {
                    continue;
                }

                // Find missing in docs
                for (in.simplifymoney.ledgersync.model.NormalizedTxn t : sqlTxns) {
                    if (!docTxns.contains(t)) {
                        divergences.add(new Divergence("missing_or_changed_in_docs", t.toString(), null));
                    }
                }

                // Find extra in docs
                for (in.simplifymoney.ledgersync.model.NormalizedTxn t : docTxns) {
                    if (!sqlTxns.contains(t)) {
                        divergences.add(new Divergence("extra_or_changed_in_docs", null, t.toString()));
                    }
                }
            }
        }
        return divergences;
    }

    private String identityKey(in.simplifymoney.ledgersync.model.NormalizedTxn t) {
        return String.format("%s|%s|%s|%s",
                t.accountLast4(),
                t.direction(),
                t.amount().toPlainString(),
                t.occurredAt().toInstant().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString());
    }

    /** One place the two stores disagree. */
    public record Divergence(String what, String inSql, String inDocuments) {}
}
