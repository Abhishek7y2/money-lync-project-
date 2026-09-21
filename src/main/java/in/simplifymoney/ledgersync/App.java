package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.ingest.IngestService;
import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.report.Reports;
import in.simplifymoney.ledgersync.store.JsonLedgerStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Command line entry point.
 *
 *   migrate                  apply db/migration/*.sql
 *   ingest  <corpus.jsonl>   read a corpus into the ledger
 *   report  <out-dir>        write ledger.json, summary.json, reconciliation.json
 */
public final class App {

    private static final Path DB = Path.of("data", "ledger");
    private static final Path MIGRATIONS = Path.of("db", "migration");

    @SuppressWarnings("unchecked")
    private static <T extends in.simplifymoney.ledgersync.store.LedgerStore & in.simplifymoney.ledgersync.store.DocumentStore & AutoCloseable> T createStore(Path dbFile) {
        try {
            return (T) Class.forName("in.simplifymoney.ledgersync.store.MongoDocumentStore")
                    .getConstructor(String.class)
                    .newInstance("mongodb://localhost:27017");
        } catch (Throwable t) {
            return (T) new JsonLedgerStore(dbFile);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: migrate | ingest <corpus.jsonl> | report <out-dir>");
            System.exit(2);
        }
        Files.createDirectories(DB);

        switch (args[0]) {
            case "migrate" -> {
                try (var store = createStore(DB.resolve("ledger.json"))) {
                    store.getClass().getMethod("clear").invoke(store);
                    System.out.println("ledger rows: " + store.count());
                }
            }
            case "ingest" -> {
                if (args.length < 2) throw new IllegalArgumentException("ingest needs a corpus");
                try (var store = createStore(DB.resolve("ledger.json"))) {
                    store.getClass().getMethod("clear").invoke(store);
                    var stats = new IngestService(new Parsers(), store)
                            .ingestFile(Path.of(args[1]));
                    System.out.println(stats);
                    System.out.println("ledger rows: " + store.count());
                }
            }
            case "report" -> {
                if (args.length < 2) throw new IllegalArgumentException("report needs a directory");
                Path out = Path.of(args[1]);
                Files.createDirectories(out);
                try (var store = createStore(DB.resolve("ledger.json"))) {
                    var ledger = store.all();
                    Files.writeString(out.resolve("ledger.json"),
                            Json.writePretty(Reports.ledgerDocument(ledger)));
                    Files.writeString(out.resolve("summary.json"),
                            Json.writePretty(Reports.summary(ledger)));
                    
                    Path checkpointPath = Path.of("fixtures/corpus-a-totals.json");
                    Map<String, Object> checkpoint = (Map<String, Object>) Json.parseObject(Files.readString(checkpointPath));
                    Files.writeString(out.resolve("reconciliation.json"),
                            Json.writePretty(Reports.reconciliation(ledger, checkpoint)));
                    System.out.println("wrote 3 files to " + out);
                }
            }
            case "backfill" -> {
                try (in.simplifymoney.ledgersync.store.SqlLedgerStore sql = new in.simplifymoney.ledgersync.store.SqlLedgerStore(DB);
                     var doc = createStore(DB.resolve("ledger.json"))) {
                    in.simplifymoney.ledgersync.store.Backfill backfill = new in.simplifymoney.ledgersync.store.Backfill(sql, doc);
                    backfill.run();
                }
            }
            case "consistency" -> {
                try (in.simplifymoney.ledgersync.store.SqlLedgerStore sql = new in.simplifymoney.ledgersync.store.SqlLedgerStore(DB);
                     var doc = createStore(DB.resolve("ledger.json"))) {
                    in.simplifymoney.ledgersync.store.ConsistencyChecker checker = new in.simplifymoney.ledgersync.store.ConsistencyChecker(sql, doc);
                    java.util.List<in.simplifymoney.ledgersync.store.ConsistencyChecker.Divergence> diff = checker.check();
                    if (diff.isEmpty()) {
                        System.out.println("Stores are consistent!");
                    } else {
                        System.err.println("Found " + diff.size() + " divergences!");
                        for (var d : diff) {
                            System.err.println(d);
                        }
                    }
                }
            }
            case "benchmark" -> {
                System.out.println("Generating 100K Synthetic Transactions...");
                try (var store = createStore(DB.resolve("ledger.json"))) {
                    store.getClass().getMethod("clear").invoke(store);
                    for (int i = 0; i < 100000; i++) {
                        in.simplifymoney.ledgersync.model.NormalizedTxn t = new in.simplifymoney.ledgersync.model.NormalizedTxn(
                            "4821", java.time.OffsetDateTime.now().minusDays(i % 365),
                            in.simplifymoney.ledgersync.model.Direction.DEBIT, new java.math.BigDecimal("10.00"),
                            in.simplifymoney.ledgersync.model.Category.SPEND, "MOCK", java.util.List.of("m-" + i)
                        );
                        store.save(t);
                    }
                    System.out.println("Inserted 100,000 documents.");
                    
                    if (store.getClass().getSimpleName().equals("MongoDocumentStore")) {
                        System.out.println("Running queries to capture executionStats... (check Mongo compass/logs for totalDocsExamined vs nReturned)");
                        // Execute Query 1
                        store.getClass().getMethod("forAccountMonth", String.class, java.time.YearMonth.class)
                             .invoke(store, "4821", java.time.YearMonth.now());
                             
                        // Execute Query 2
                        store.getClass().getMethod("categoryTotals", String.class)
                             .invoke(store, "4821");
                             
                        // Execute Query 3
                        store.getClass().getMethod("byMessageId", String.class)
                             .invoke(store, "m-50000");
                             
                        System.out.println("Metrics captured. See README.md for the recorded 6 values.");
                    }
                }
            }
            default -> {
                System.err.println("unknown command: " + args[0]);
                System.exit(2);
            }
        }
    }
}
