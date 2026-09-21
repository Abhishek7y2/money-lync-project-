package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.model.Direction;
import java.util.Optional;

/**
 * Bank transaction alert emails.
 *
 * Not written yet. The corpus contains them and they are currently all dropped.
 */
public final class EmailParser implements MessageParser {

    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile(
            "Your account ending (?<acct>\\d{4}) has been (?<dir>debited|credited) with (?:INR|Rs\\.?)\\s*([0-9,.]+(?:\\.[0-9]{2})?)\\.\\n"
                    + "Merchant / Remarks: (?<merchant>.*?)\\n");
    private static final java.util.regex.Pattern DATE = java.util.regex.Pattern.compile(
            "Date: (?:Mon|Tue|Wed|Thu|Fri|Sat|Sun),\\s*(?<when>\\d{2} [a-zA-Z]{3} \\d{4} \\d{2}:\\d{2}:\\d{2} \\+\\d{4})");

    @Override
    public boolean supports(RawMessage m) {
        return "email".equals(m.channel());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {
        java.util.regex.Matcher em = EMAIL.matcher(m.body());
        if (!em.find()) return Optional.empty();

        java.util.regex.Matcher dm = DATE.matcher(m.body());
        if (!dm.find()) return Optional.empty();

        String amountStr = em.group(3).replace(",", "");
        java.math.BigDecimal amount = new java.math.BigDecimal(amountStr).setScale(2);

        java.time.OffsetDateTime at = java.time.OffsetDateTime.parse(dm.group("when"), 
                java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH));

        Direction d = "debited".equals(em.group("dir")) ? Direction.DEBIT : Direction.CREDIT;
        
        return Optional.of(new ParsedTxn(em.group("acct"), at, d, amount,
                em.group("merchant").trim(), null, m.messageId()));
    }
}
