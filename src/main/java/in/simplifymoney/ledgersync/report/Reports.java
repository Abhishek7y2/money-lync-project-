package in.simplifymoney.ledgersync.report;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The two reports the assignment asks for.
 *
 * summary() below is a first cut: it adds up what is in the ledger. It does not
 * know that a transfer is not spending, and it does not roll micro spends up.
 *
 * reconciliation() has not been written at all.
 */
public final class Reports {

    private Reports() {}

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public static Map<String, Object> summary(List<NormalizedTxn> ledger) {
        Map<String, Object> byAccount = new LinkedHashMap<>();
        List<String> accounts = ledger.stream()
                .map(NormalizedTxn::accountLast4).distinct().sorted().toList();

        for (String acct : accounts) {
            BigDecimal spend = ZERO;
            BigDecimal income = ZERO;
            long microCount = 0;
            BigDecimal microTotal = ZERO;
            BigDecimal transferredOut = ZERO;
            BigDecimal transferredIn = ZERO;

            for (NormalizedTxn t : ledger) {
                if (!t.accountLast4().equals(acct)) continue;
                
                switch (t.category()) {
                    case SPEND -> spend = spend.add(t.amount());
                    case INCOME -> income = income.add(t.amount());
                    case MICRO -> {
                        microCount++;
                        microTotal = microTotal.add(t.amount());
                    }
                    case TRANSFER -> {
                        if (t.direction() == Direction.DEBIT) {
                            transferredOut = transferredOut.add(t.amount());
                        } else {
                            transferredIn = transferredIn.add(t.amount());
                        }
                    }
                }
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("spend", spend.toPlainString());
            out.put("income", income.toPlainString());
            out.put("micro_count", microCount);
            out.put("micro_total", microTotal.toPlainString());
            out.put("transferred_out", transferredOut.toPlainString());
            out.put("transferred_in", transferredIn.toPlainString());
            byAccount.put(acct, out);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("accounts", byAccount);
        return doc;
    }

    public static Map<String, Object> ledgerDocument(List<NormalizedTxn> ledger) {
        List<Object> rows = ledger.stream().map(t -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("account_last4", t.accountLast4());
            r.put("occurred_at", t.occurredAt().toString());
            r.put("direction", t.direction().name().toLowerCase());
            r.put("amount", t.amount().toPlainString());
            r.put("category", t.category().name());
            r.put("merchant", t.merchant());
            r.put("source_message_ids", t.sourceMessageIds());
            return (Object) r;
        }).toList();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("transactions", rows);
        return doc;
    }

    public static Map<String, Object> reconciliation(List<NormalizedTxn> ledger, Map<String, Object> checkpoint) {
        Map<String, Object> report = new LinkedHashMap<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> checkpointAccounts = (Map<String, Map<String, Object>>) checkpoint.get("accounts");
        
        for (Map.Entry<String, Map<String, Object>> entry : checkpointAccounts.entrySet()) {
            String account = entry.getKey();
            Map<String, Object> check = entry.getValue();
            
            BigDecimal opening = new BigDecimal((String) check.get("opening_balance"));
            BigDecimal expectedClosing = new BigDecimal((String) check.get("closing_balance"));
            int expectedTxns = (Integer) check.get("transactions_expected");
            
            // Calculate actuals from ledger
            BigDecimal calculatedClosing = opening;
            int actualTxns = 0;
            
            for (NormalizedTxn t : ledger) {
                if (!t.accountLast4().equals(account)) continue;
                actualTxns++;
                if (t.direction() == Direction.CREDIT) {
                    calculatedClosing = calculatedClosing.add(t.amount());
                } else if (t.direction() == Direction.DEBIT) {
                    calculatedClosing = calculatedClosing.subtract(t.amount());
                }
            }
            
            if (expectedClosing.compareTo(calculatedClosing) != 0 || expectedTxns != actualTxns) {
                Map<String, Object> discrepancy = new LinkedHashMap<>();
                discrepancy.put("expected_transactions", expectedTxns);
                discrepancy.put("actual_transactions", actualTxns);
                discrepancy.put("expected_closing_balance", expectedClosing.toPlainString());
                discrepancy.put("calculated_closing_balance", calculatedClosing.toPlainString());
                BigDecimal diff = expectedClosing.subtract(calculatedClosing).abs();
                discrepancy.put("balance_difference", diff.toPlainString());
                report.put(account, discrepancy);
            }
        }
        
        return report;
    }

    public static Map<Category, BigDecimal> byCategory(List<NormalizedTxn> ledger) {
        Map<Category, BigDecimal> out = new LinkedHashMap<>();
        for (Category c : Category.values()) out.put(c, ZERO);
        for (NormalizedTxn t : ledger) {
            out.put(t.category(), out.get(t.category()).add(t.amount()));
        }
        return out;
    }
}
