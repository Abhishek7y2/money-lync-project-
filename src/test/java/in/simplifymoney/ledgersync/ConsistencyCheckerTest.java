package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.store.ConsistencyChecker;
import in.simplifymoney.ledgersync.store.InMemoryLedgerStore;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ConsistencyCheckerTest {

    @Test
    void testDetectsAmountMismatch() {
        InMemoryLedgerStore sql = new InMemoryLedgerStore();
        InMemoryLedgerStore mongo = new InMemoryLedgerStore();
        
        NormalizedTxn sqlTxn = new NormalizedTxn(
            "4821", OffsetDateTime.now(), Direction.DEBIT, new BigDecimal("2499.50"), Category.SPEND, "AMAZON", List.of("m-1")
        );
        
        // Corrupted in Mongo!
        NormalizedTxn mongoTxn = new NormalizedTxn(
            "4821", sqlTxn.occurredAt(), Direction.DEBIT, new BigDecimal("249.50"), Category.SPEND, "AMAZON", List.of("m-1")
        );
        
        sql.save(sqlTxn);
        mongo.save(mongoTxn);
        
        ConsistencyChecker checker = new ConsistencyChecker(sql, mongo);
        List<ConsistencyChecker.Divergence> diffs = checker.check();
        
        assertEquals(1, diffs.size());
        assertEquals("Amount mismatch (SQL: 2499.50, Mongo: 249.50)", diffs.get(0).reason());
    }
}
