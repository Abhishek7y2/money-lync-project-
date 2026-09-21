package in.simplifymoney.ledgersync.parse;

import static org.junit.jupiter.api.Assertions.assertTrue;

import in.simplifymoney.ledgersync.model.RawMessage;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NonTransactionMessageTest {

    @Test
    void ignoresAdversarialMarketingMessage() {
        RawMessage marketing = new RawMessage("msg-1", "sms", "AD-HDFCBK-S", 
            OffsetDateTime.now(), "dev-1", 
            "Imagine Rs.5,000 debited from your account! Protect yourself today with our new insurance plan.");
        
        Parsers parsers = new Parsers();
        Optional<ParsedTxn> parsed = parsers.parse(marketing);
        
        // Should return empty because it doesn't match the strict transaction regex templates (no account number or date context)
        assertTrue(parsed.isEmpty(), "Marketing message imitating a transaction must be ignored");
    }
}
