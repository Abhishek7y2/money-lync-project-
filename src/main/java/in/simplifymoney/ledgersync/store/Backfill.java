package in.simplifymoney.ledgersync.store;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.util.List;
import java.util.Map;
/**
 * Moves everything already in the SQL store into the document store.
 *
 * NOT IMPLEMENTED - this is yours.
 *
 * Two things to know before you start:
 *  - the SQL store is not clean. It has been running without a uniqueness
 *    guarantee for a long time
 *  - this will be run more than once, including after a partial failure
 */
public final class Backfill {

    private final SqlLedgerStore source;
    private final DocumentStore target;

    public Backfill(SqlLedgerStore source, DocumentStore target) {
        this.source = source;
        this.target = target;
    }

    public Result run() {
        System.out.println("Starting backfill from SQL to JSON...");
        
        List<NormalizedTxn> sqlRows = source.all();
        System.out.println("Found " + sqlRows.size() + " rows in SQL store.");

        Map<String, List<NormalizedTxn>> grouped = new java.util.LinkedHashMap<>();
        for (NormalizedTxn t : sqlRows) {
            String key = identityKey(t);
            grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(t);
        }

        List<NormalizedTxn> deduplicated = new java.util.ArrayList<>();
        for (List<NormalizedTxn> group : grouped.values()) {
            NormalizedTxn primary = group.get(0);
            java.util.Set<String> mergedIds = new java.util.TreeSet<>();
            for (NormalizedTxn t : group) {
                mergedIds.addAll(t.sourceMessageIds());
            }
            deduplicated.add(new NormalizedTxn(
                    primary.accountLast4(),
                    primary.occurredAt(),
                    primary.direction(),
                    primary.amount(),
                    primary.category(),
                    primary.merchant(),
                    new java.util.ArrayList<>(mergedIds)
            ));
        }

        System.out.println("Deduplicated down to " + deduplicated.size() + " unique transactions.");

        for (NormalizedTxn t : deduplicated) {
            target.save(t);
        }
        
        System.out.println("Backfill complete.");
        return new Result(sqlRows.size(), deduplicated.size(), sqlRows.size() - deduplicated.size());
    }

    private String identityKey(NormalizedTxn t) {
        return String.format("%s|%s|%s|%s",
                t.accountLast4(),
                t.direction(),
                t.amount().toPlainString(),
                t.occurredAt().toInstant().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString());
    }

    public record Result(long read, long written, long skipped) {}
}
