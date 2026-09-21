package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.parse.ParsedTxn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class DeduplicationEngine {

    /**
     * Groups raw parsed transactions into deduplicated equivalence classes
     * based on their canonical transaction identity.
     */
    public List<List<ParsedTxn>> deduplicate(List<ParsedTxn> parsedTxns) {
        Map<String, List<ParsedTxn>> grouped = parsedTxns.stream()
                .collect(Collectors.groupingBy(TransactionIdentity::generateKey));

        return new ArrayList<>(grouped.values());
    }
}
