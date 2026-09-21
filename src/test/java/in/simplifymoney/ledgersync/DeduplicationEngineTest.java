package in.simplifymoney.ledgersync.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeduplicationEngineTest {

    @Test
    void groupsIdenticalTransactions() {
        ParsedTxn sms = new ParsedTxn("1234", OffsetDateTime.parse("2026-07-04T10:15:30Z"), Direction.DEBIT, new BigDecimal("100.00"), "MERCHANT", "sms-id");
        ParsedTxn email = new ParsedTxn("1234", OffsetDateTime.parse("2026-07-04T10:15:45Z"), Direction.DEBIT, new BigDecimal("100.00"), "OTHER", "email-id");
        
        DeduplicationEngine engine = new DeduplicationEngine();
        List<List<ParsedTxn>> groups = engine.deduplicate(List.of(sms, email));
        
        assertEquals(1, groups.size(), "Should merge into exactly 1 equivalence class");
        assertEquals(2, groups.get(0).size(), "The single group should contain both evidence pieces");
    }
}
