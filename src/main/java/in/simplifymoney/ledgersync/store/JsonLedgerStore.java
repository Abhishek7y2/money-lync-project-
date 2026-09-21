package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import java.time.YearMonth;
import java.util.Optional;

public final class JsonLedgerStore implements LedgerStore, DocumentStore, AutoCloseable {

    private final Path dbFile;
    private List<NormalizedTxn> memory;

    public JsonLedgerStore(Path dbFile) {
        this.dbFile = dbFile;
        this.memory = new ArrayList<>();
        load();
    }

    private void load() {
        if (!Files.exists(dbFile)) return;
        try {
            String content = Files.readString(dbFile);
            if (content.isBlank()) return;
            List<Map<String, Object>> list = (List<Map<String, Object>>) Json.parseObject("{\"data\":" + content + "}").get("data");
            for (Map<String, Object> doc : list) {
                memory.add(new NormalizedTxn(
                        (String) doc.get("accountLast4"),
                        OffsetDateTime.parse((String) doc.get("occurredAt")),
                        Direction.valueOf((String) doc.get("direction")),
                        new BigDecimal((String) doc.get("amount")).setScale(2),
                        Category.valueOf((String) doc.get("category")),
                        (String) doc.get("merchant"),
                        (List<String>) doc.get("sourceMessageIds")
                ));
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not load json ledger from " + dbFile, e);
        }
    }

    private void saveAll() {
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (NormalizedTxn t : memory) {
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("accountLast4", t.accountLast4());
                doc.put("occurredAt", t.occurredAt().toString());
                doc.put("direction", t.direction().name());
                doc.put("amount", t.amount().toPlainString());
                doc.put("category", t.category().name());
                doc.put("merchant", t.merchant());
                doc.put("sourceMessageIds", t.sourceMessageIds());
                list.add(doc);
            }
            Files.writeString(dbFile, Json.writePretty(list));
        } catch (IOException e) {
            throw new IllegalStateException("could not save json ledger to " + dbFile, e);
        }
    }

    public void clear() {
        memory.clear();
        saveAll();
    }

    @Override
    public void save(NormalizedTxn t) {
        memory.removeIf(existing -> identityKey(existing).equals(identityKey(t)));
        memory.add(t);
        saveAll();
    }

    private String identityKey(NormalizedTxn t) {
        return String.format("%s|%s|%s|%s",
                t.accountLast4(),
                t.direction(),
                t.amount().toPlainString(),
                t.occurredAt().toInstant().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString());
    }

    public List<NormalizedTxn> all() {
        List<NormalizedTxn> copy = new ArrayList<>(memory);
        copy.sort(Comparator.comparing(NormalizedTxn::occurredAt));
        return copy;
    }

    public long count() {
        return memory.size();
    }

    @Override
    public List<NormalizedTxn> forAccountMonth(String accountLast4, YearMonth month) {
        return memory.stream()
                .filter(t -> t.accountLast4().equals(accountLast4))
                .filter(t -> YearMonth.from(t.occurredAt()).equals(month))
                .sorted(Comparator.comparing(NormalizedTxn::occurredAt).reversed())
                .toList();
    }

    @Override
    public Map<Category, BigDecimal> categoryTotals(String accountLast4) {
        Map<Category, BigDecimal> out = new LinkedHashMap<>();
        for (Category c : Category.values()) out.put(c, BigDecimal.ZERO.setScale(2));
        for (NormalizedTxn t : memory) {
            if (t.accountLast4().equals(accountLast4)) {
                out.put(t.category(), out.get(t.category()).add(t.amount()));
            }
        }
        return out;
    }

    @Override
    public Optional<NormalizedTxn> byMessageId(String messageId) {
        return memory.stream()
                .filter(t -> t.sourceMessageIds().contains(messageId))
                .findFirst();
    }

    @Override
    public void close() {
    }
}
