# AI Disclosure

**Tools Used**: Claude / ChatGPT

**Primary Uses**:
- Discussing architectural trade-offs between JSON file stores vs MongoDB.
- Reviewing regular expression boundary conditions.
- Validating the Sliding Window algorithmic approach for `TRANSFER` detection.

### Concrete Example of AI Being Wrong

While diagnosing the `INC-2026-09-11` amount bug (where a ₹5 spend was recorded as ₹92,213.10), the AI initially suggested a simplistic fix:

**AI's Suggestion**:
"To capture the ₹5, just update the regex to grab the first numerical value with optional decimals, like this: `[0-9]+(?:\\.[0-9]{2})?` and extract it directly."

**Why it was wrong**:
While this technically grabs `5` instead of `92213.10`, it blindly assumes the *first* number in the text is always the transaction amount. In unseen data, a message might start with "Your 1st alert: Rs.5...". If the code blindly trusts the first matching number, it would extract `1` instead of `5.00`.

**My Implementation**:
I rejected the blind extraction approach. The first proposed fix solved the observed example but was not robust enough for unseen data. It treated extraction as a lexical problem ("find the first number") instead of a semantic/template problem ("identify the transaction amount among multiple monetary values"). I therefore changed the parser to use currency markers and transaction-context rules to distinguish the transaction amount from balances and other monetary values.
