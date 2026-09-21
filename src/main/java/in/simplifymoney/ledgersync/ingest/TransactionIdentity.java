package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.parse.ParsedTxn;

public final class TransactionIdentity {
    
    private TransactionIdentity() {}

    /**
     * Generates a deterministic identity key for a parsed transaction based on mathematical facts:
     * Account | Direction | Amount | Minute (Truncated)
     */
    public static String generateKey(ParsedTxn p) {
        return String.format("%s|%s|%s|%s",
                p.accountLast4(),
                p.direction(),
                p.amount().toPlainString(),
                p.occurredAt().toInstant().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString());
    }
}
