package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.LedgerStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads a corpus of raw messages and puts transactions in the ledger.
 *
 * This is the naive version. It parses each message on its own and saves
 * whatever comes back. It does not ask whether two messages describe the same
 * transaction, and it decides the category from the direction alone.
 */
public final class IngestService {

    private final Parsers parsers;
    private final LedgerStore store;

    public IngestService(Parsers parsers, LedgerStore store) {
        this.parsers = parsers;
        this.store = store;
    }

    public Stats ingestFile(Path corpus) throws IOException {
        List<RawMessage> messages = readCorpus(corpus);
        List<ParsedTxn> parsedTxns = new ArrayList<>();
        int skipped = 0;
        
        for (RawMessage m : messages) {
            Optional<ParsedTxn> p = parsers.parse(m);
            if (p.isEmpty()) {
                skipped++;
                continue;
            }
            parsedTxns.add(p.get());
        }

        // Deduplication
        DeduplicationEngine dedup = new DeduplicationEngine();
        List<List<ParsedTxn>> duplicateGroups = dedup.deduplicate(parsedTxns);
        
        List<ParsedTxn> uniqueTxns = new ArrayList<>();
        for (List<ParsedTxn> group : duplicateGroups) {
            uniqueTxns.add(group.get(0));
        }

        // Categorize
        CategorizationEngine catEngine = new CategorizationEngine();
        Map<ParsedTxn, Category> categories = catEngine.categorize(uniqueTxns);

        List<NormalizedTxn> finalTxns = new ArrayList<>();
        for (List<ParsedTxn> group : duplicateGroups) {
            ParsedTxn primary = group.get(0);
            List<String> msgIds = group.stream().map(ParsedTxn::sourceMessageId).sorted().toList();
            Category c = categories.get(primary);
            finalTxns.add(new NormalizedTxn(primary.accountLast4(), primary.occurredAt(), 
                    primary.direction(), primary.amount(), c, primary.merchant(), msgIds));
        }

        // Save deduplicated txns
        for (NormalizedTxn t : finalTxns) {
            store.save(t);
        }

        return new Stats(messages.size(), finalTxns.size(), skipped);
    }

    public static List<RawMessage> readCorpus(Path corpus) throws IOException {
        List<RawMessage> out = new ArrayList<>();
        try (Stream<String> lines = Files.lines(corpus)) {
            for (String line : (Iterable<String>) lines.filter(s -> !s.isBlank())::iterator) {
                Map<String, Object> o = Json.parseObject(line);
                out.add(new RawMessage(
                        (String) o.get("message_id"),
                        (String) o.get("channel"),
                        (String) o.get("sender"),
                        OffsetDateTime.parse((String) o.get("received_at")),
                        (String) o.get("device_id"),
                        (String) o.get("body")));
            }
        }
        return out;
    }

    public record Stats(int messagesRead, int transactionsWritten, int messagesSkipped) {}
}
