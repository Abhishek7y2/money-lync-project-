package in.simplifymoney.ledgersync.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TransactionIdentityTest {

    @Test
    void identicalFactsProduceSameIdentity() {
        ParsedTxn p1 = new ParsedTxn("1234", OffsetDateTime.parse("2026-07-04T10:15:30Z"), Direction.DEBIT, new BigDecimal("100.00"), "MERCHANT", "msg1");
        ParsedTxn p2 = new ParsedTxn("1234", OffsetDateTime.parse("2026-07-04T10:15:45Z"), Direction.DEBIT, new BigDecimal("100.00"), "OTHER", "msg2");
        
        assertEquals(TransactionIdentity.generateKey(p1), TransactionIdentity.generateKey(p2));
    }

    @Test
    void differentMinuteProducesDifferentIdentity() {
        ParsedTxn p1 = new ParsedTxn("1234", OffsetDateTime.parse("2026-07-04T10:15:30Z"), Direction.DEBIT, new BigDecimal("100.00"), "MERCHANT", "msg1");
        ParsedTxn p2 = new ParsedTxn("1234", OffsetDateTime.parse("2026-07-04T10:16:30Z"), Direction.DEBIT, new BigDecimal("100.00"), "MERCHANT", "msg2");
        
        assertNotEquals(TransactionIdentity.generateKey(p1), TransactionIdentity.generateKey(p2));
    }
}
