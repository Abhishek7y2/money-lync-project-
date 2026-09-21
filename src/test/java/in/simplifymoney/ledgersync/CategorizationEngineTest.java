package in.simplifymoney.ledgersync.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CategorizationEngineTest {

    @Test
    void pairsTransfersWithin5Minutes() {
        ParsedTxn debit = new ParsedTxn("1234", OffsetDateTime.parse("2026-07-04T10:15:30Z"), Direction.DEBIT, new BigDecimal("500.00"), "TRANSFER TO", "msg1");
        ParsedTxn credit = new ParsedTxn("5678", OffsetDateTime.parse("2026-07-04T10:19:45Z"), Direction.CREDIT, new BigDecimal("500.00"), "TRANSFER FROM", "msg2");
        
        CategorizationEngine engine = new CategorizationEngine();
        Map<ParsedTxn, Category> cats = engine.categorize(List.of(debit, credit));
        
        assertEquals(Category.TRANSFER, cats.get(debit));
        assertEquals(Category.TRANSFER, cats.get(credit));
    }
}
