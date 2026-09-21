package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CategorizationEngine {

    /**
     * Determines the exact Category (SPEND, INCOME, TRANSFER, MICRO) for every deduplicated transaction.
     */
    public Map<ParsedTxn, Category> categorize(List<ParsedTxn> uniqueTxns) {
        Map<ParsedTxn, Category> categories = new HashMap<>();

        for (ParsedTxn t1 : uniqueTxns) {
            if (categories.containsKey(t1)) continue;

            // Check for TRANSFER
            Optional<ParsedTxn> transferLeg = uniqueTxns.stream()
                    .filter(t2 -> !t1.equals(t2))
                    .filter(t2 -> !t1.accountLast4().equals(t2.accountLast4()))
                    .filter(t2 -> t1.direction() != t2.direction())
                    .filter(t2 -> t1.amount().compareTo(t2.amount()) == 0)
                    .filter(t2 -> Math.abs(Duration.between(t1.occurredAt(), t2.occurredAt()).toMinutes()) <= 5)
                    .findFirst();

            if (transferLeg.isPresent()) {
                categories.put(t1, Category.TRANSFER);
                categories.put(transferLeg.get(), Category.TRANSFER);
                continue;
            }

            // Check for MICRO
            boolean isUpi = t1.merchant().toUpperCase().contains("UPI") || t1.merchant().toUpperCase().contains("VPA");
            if (t1.direction() == Direction.DEBIT && isUpi && t1.amount().compareTo(new BigDecimal("100.00")) <= 0) {
                categories.put(t1, Category.MICRO);
                continue;
            }

            // Default
            categories.put(t1, t1.direction() == Direction.DEBIT ? Category.SPEND : Category.INCOME);
        }

        return categories;
    }
}
