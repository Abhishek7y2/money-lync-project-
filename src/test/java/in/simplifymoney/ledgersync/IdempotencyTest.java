package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.simplifymoney.ledgersync.ingest.IngestService;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.InMemoryLedgerStore;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class IdempotencyTest {

    @Test
    void testIngestIsIdempotent() throws Exception {
        InMemoryLedgerStore store = new InMemoryLedgerStore();
        IngestService ingest = new IngestService(new Parsers(), store);
        
        // Run first time
        IngestService.Stats firstRun = ingest.ingestFile(Path.of("fixtures/corpus-a.jsonl"));
        int firstCount = store.all().size();
        
        // Run second time (Identical Corpus)
        IngestService.Stats secondRun = ingest.ingestFile(Path.of("fixtures/corpus-a.jsonl"));
        int secondCount = store.all().size();
        
        // Transactions written on second run must be 0, and total count must remain the same
        assertEquals(0, secondRun.transactionsWritten(), "Second run should write 0 new transactions");
        assertEquals(firstCount, secondCount, "Ledger size must remain identical");
    }
}
