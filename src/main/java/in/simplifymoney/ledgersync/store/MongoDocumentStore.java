package in.simplifymoney.ledgersync.store;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.UpdateOptions;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MongoDocumentStore implements DocumentStore, LedgerStore, AutoCloseable {

    private final MongoClient client;
    private final MongoCollection<Document> ledger;

    public MongoDocumentStore(String uri) {
        this.client = MongoClients.create(uri);
        MongoDatabase database = this.client.getDatabase("ledgersync");
        this.ledger = database.getCollection("transactions");
        setupIndexes();
    }

    private void setupIndexes() {
        // Query 1: forAccountMonth
        ledger.createIndex(Indexes.compoundIndex(Indexes.ascending("accountLast4"), Indexes.ascending("yearMonth"), Indexes.descending("occurredAt")));
        
        // Query 2: categoryTotals
        ledger.createIndex(Indexes.compoundIndex(Indexes.ascending("accountLast4"), Indexes.ascending("category")));
        
        // Query 3: byMessageId
        ledger.createIndex(Indexes.ascending("sourceMessageIds"));
        
        // Idempotency / Identity Key
        ledger.createIndex(Indexes.ascending("identityKey"), new IndexOptions().unique(true));
    }

    @Override
    public List<NormalizedTxn> forAccountMonth(String accountLast4, YearMonth month) {
        List<NormalizedTxn> out = new ArrayList<>();
        Bson filter = Filters.and(
            Filters.eq("accountLast4", accountLast4),
            Filters.eq("yearMonth", month.toString())
        );
        for (Document doc : ledger.find(filter).sort(Sorts.descending("occurredAt"))) {
            out.add(toModel(doc));
        }
        return out;
    }

    @Override
    public Map<Category, BigDecimal> categoryTotals(String accountLast4) {
        Map<Category, BigDecimal> out = new LinkedHashMap<>();
        for (Category c : Category.values()) out.put(c, BigDecimal.ZERO.setScale(2));

        Bson filter = Filters.eq("accountLast4", accountLast4);
        for (Document doc : ledger.find(filter)) {
            Category c = Category.valueOf(doc.getString("category"));
            BigDecimal amount = new BigDecimal(doc.getString("amount"));
            out.put(c, out.get(c).add(amount));
        }
        return out;
    }

    @Override
    public Optional<NormalizedTxn> byMessageId(String messageId) {
        Bson filter = Filters.eq("sourceMessageIds", messageId);
        Document doc = ledger.find(filter).first();
        if (doc == null) return Optional.empty();
        return Optional.of(toModel(doc));
    }

    @Override
    public void save(NormalizedTxn t) {
        String identityKey = String.format("%s|%s|%s|%s",
                t.accountLast4(),
                t.direction(),
                t.amount().toPlainString(),
                t.occurredAt().toInstant().truncatedTo(ChronoUnit.MINUTES).toString());

        Document doc = new Document("accountLast4", t.accountLast4())
                .append("occurredAt", t.occurredAt().toString())
                .append("yearMonth", YearMonth.from(t.occurredAt()).toString())
                .append("direction", t.direction().name())
                .append("amount", t.amount().toPlainString())
                .append("category", t.category().name())
                .append("merchant", t.merchant())
                .append("sourceMessageIds", t.sourceMessageIds())
                .append("identityKey", identityKey);

        Bson filter = Filters.eq("identityKey", identityKey);
        Bson update = new Document("$set", doc);
        UpdateOptions options = new UpdateOptions().upsert(true);
        ledger.updateOne(filter, update, options);
    }

    public List<NormalizedTxn> all() {
        List<NormalizedTxn> out = new ArrayList<>();
        for (Document doc : ledger.find().sort(Sorts.ascending("occurredAt"))) {
            out.add(toModel(doc));
        }
        return out;
    }

    public long count() {
        return ledger.countDocuments();
    }
    
    public void clear() {
        ledger.deleteMany(new Document());
    }

    @SuppressWarnings("unchecked")
    private NormalizedTxn toModel(Document doc) {
        return new NormalizedTxn(
                doc.getString("accountLast4"),
                OffsetDateTime.parse(doc.getString("occurredAt")),
                Direction.valueOf(doc.getString("direction")),
                new BigDecimal(doc.getString("amount")).setScale(2),
                Category.valueOf(doc.getString("category")),
                doc.getString("merchant"),
                (List<String>) doc.get("sourceMessageIds")
        );
    }

    @Override
    public void close() {
        client.close();
    }
}
