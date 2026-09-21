package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ICICI Bank SMS.
 *
 * TODO(ops): this only reads the "Dear Customer, Acct XX.... is debited with"
 * shape. There is at least one other ICICI format in the corpus that falls
 * straight through and is lost. Finish this.
 */
public final class IciciSmsParser implements MessageParser {

    public static final String SENDER = "VM-ICICIB-T";

    private static final Pattern V1 = Pattern.compile(
            "Acct XX(?<acct>\\d{4}) is (?<dir>debited|credited) with .*? "
                    + "on (?<when>\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2})\\. "
                    + "Info: (?<merchant>[^.]+)\\.");

    private static final Pattern V2 = Pattern.compile(
            "ICICI Bank Acct XX(?<acct>\\d{4}) (?<dir>Dr|Cr) (?:INR|Rs) .*? "
                    + "on (?<when>\\d{2}-[a-zA-Z]{3}-\\d{4} \\d{2}:\\d{2}); (?<merchant>.*?) ref no");

    @Override
    public boolean supports(RawMessage m) {
        return "sms".equals(m.channel()) && SENDER.equals(m.sender());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {
        BigDecimal amount = Amounts.first(m.body());
        if (amount == null) return Optional.empty();

        Matcher v1 = V1.matcher(m.body());
        if (v1.find()) {
            OffsetDateTime at = Dates.ist(v1.group("when"));
            if (at == null) return Optional.empty();
            Direction d = "debited".equals(v1.group("dir")) ? Direction.DEBIT : Direction.CREDIT;
            return Optional.of(new ParsedTxn(v1.group("acct"), at, d, amount,
                    v1.group("merchant").trim(), Amounts.statedBalance(m.body()), m.messageId()));
        }

        Matcher v2 = V2.matcher(m.body());
        if (v2.find()) {
            OffsetDateTime at = Dates.ist(v2.group("when"));
            if (at == null) return Optional.empty();
            Direction d = "Dr".equals(v2.group("dir")) ? Direction.DEBIT : Direction.CREDIT;
            return Optional.of(new ParsedTxn(v2.group("acct"), at, d, amount,
                    v2.group("merchant").trim(), Amounts.statedBalance(m.body()), m.messageId()));
        }

        return Optional.empty();
    }
}
