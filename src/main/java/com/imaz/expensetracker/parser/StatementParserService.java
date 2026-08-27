package com.imaz.expensetracker.parser;

import com.imaz.expensetracker.dto.TransactionDto.ParsedTransactionDto;
import com.imaz.expensetracker.entity.Category;
import com.imaz.expensetracker.entity.Transaction.StatementType;
import com.imaz.expensetracker.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementParserService {

    private final CategoryService categoryService;

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    /**
     * Handles HDFC-style credit-card lines like:
     *   21/07/2026| 10:45   RAZ*CrunchyroliMumbai         ₹ 475.00
     *   26/07/2026 | 00:00  1% Swiggy CashBack        + ₹ 7.75
     *   04/08/2026| 18:07   MYNTRA DESIGNS PRIVATEBangalore ₹ 191.30
     * Groups:
     *   1: date  2: time  3: description  4: sign (+/- optional)  5: amount
     */
    private static final Pattern HDFC_LINE = Pattern.compile(
            "^\\s*(\\d{2}[/\\-]\\d{2}[/\\-]\\d{2,4})\\s*[|/]?\\s*(\\d{1,2}:\\d{2})?\\s+" +
                    "(.+?)\\s+" +
                    "([+\\-])?\\s*(?:\\u20B9|Rs\\.?|INR)?\\s*" +
                    "([\\d]{1,3}(?:,\\d{2,3})*(?:\\.\\d{2}))\\s*" +
                    "(Dr|Cr|DR|CR|Debit|Credit)?\\s*$",
            Pattern.UNICODE_CASE
    );

    /** Legacy pattern (kept as fallback for older statements). */
    private static final Pattern LEGACY_LINE = Pattern.compile(
            "(?i)(\\d{2}[/\\-]\\d{2}[/\\-]\\d{2,4}|\\d{2}\\s+[A-Za-z]{3}\\s+\\d{4})" +
                    "\\s+(.{5,80}?)\\s+" +
                    "(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?)\\s*" +
                    "(Dr|Cr|DR|CR|Debit|Credit|D|C)?"
    );

    /** Lines matching any of these are dropped as noise (case-insensitive). */
    private static final List<String> NOISE_TOKENS = List.of(
            "page ", "hsn code", "gstin", "opening balance", "closing balance",
            "important information", "your card control", "purchase indicator",
            "domestic transaction", "international transaction",
            "previous statement", "payments/credits", "purchases/debit",
            "total amount due", "minimum due", "credit limit", "cash limit",
            "past dues", "over limit",
            "cashback offer", "get vouchers", "get flat", "get ₹", "get rs",
            "apply now", "click here", "know more", "check out", "get 50% discount",
            "smartpay", "mycards", "swiggy hdfc bank credit card statement",
            "rewards progress", "refer.", "share the benefits",
            "domestic transactions"
    );

    public List<ParsedTransactionDto> parsePdf(MultipartFile file,
                                                StatementType statementType,
                                                Long userId) throws IOException {

        String rawText = extractText(file);
        log.info("Extracted {} chars from {}", rawText.length(), file.getOriginalFilename());

        List<String> lines = normalizeLines(rawText);

        List<ParsedTransactionDto> primary = extract(lines, HDFC_LINE, true, userId);
        if (primary.size() >= 3) {
            log.info("Parsed {} transactions with HDFC pattern from {}", primary.size(), file.getOriginalFilename());
            return primary;
        }

        List<ParsedTransactionDto> legacy = extract(lines, LEGACY_LINE, false, userId);
        List<ParsedTransactionDto> best = legacy.size() > primary.size() ? legacy : primary;
        log.info("Parsed {} transactions (best of HDFC={} / legacy={}) from {}",
                best.size(), primary.size(), legacy.size(), file.getOriginalFilename());
        return best;
    }

    private String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * Clean up raw text into candidate transaction lines. Also joins wrapped rows
     * where the amount ended up on the next line.
     */
    private List<String> normalizeLines(String raw) {
        String[] rawLines = raw.split("\\r?\\n");
        List<String> out = new ArrayList<>(rawLines.length);
        StringBuilder pending = null;

        for (String orig : rawLines) {
            String line = orig.replace('\u00A0', ' ').trim();
            if (line.isEmpty()) { flush(pending, out); pending = null; continue; }
            if (isNoise(line)) { flush(pending, out); pending = null; continue; }

            // If line starts with a date, it's the start of a candidate row
            boolean startsWithDate = line.matches("^\\d{2}[/\\-]\\d{2}[/\\-]\\d{2,4}.*");

            if (startsWithDate) {
                flush(pending, out);
                pending = new StringBuilder(line);
                // If the row is complete (has amount at end), flush immediately
                if (endsWithAmount(line)) { out.add(pending.toString()); pending = null; }
            } else if (pending != null) {
                // Continuation: append until we see the amount tail
                pending.append(' ').append(line);
                if (endsWithAmount(pending.toString())) { out.add(pending.toString()); pending = null; }
            } else {
                // Non-transaction line — ignore
            }
        }
        flush(pending, out);
        return out;
    }

    private static void flush(StringBuilder sb, List<String> out) {
        if (sb != null && sb.length() > 0) out.add(sb.toString());
    }

    private static boolean endsWithAmount(String line) {
        return line.matches(".*[\\d,]+\\.\\d{2}\\s*(Dr|Cr|DR|CR|Debit|Credit)?\\s*$");
    }

    private boolean isNoise(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String tok : NOISE_TOKENS) {
            if (lower.contains(tok)) return true;
        }
        return false;
    }

    private List<ParsedTransactionDto> extract(List<String> lines, Pattern pattern,
                                               boolean hdfc, Long userId) {
        List<ParsedTransactionDto> transactions = new ArrayList<>();
        for (String line : lines) {
            Matcher m = pattern.matcher(line);
            if (!m.find()) continue;
            try {
                LocalDate date;
                String description;
                BigDecimal amount;
                boolean isDebit;

                if (hdfc) {
                    date = parseDate(m.group(1));
                    if (date == null) continue;
                    description = m.group(3).trim();
                    String sign = m.group(4);
                    amount = parseAmount(m.group(5));
                    String flag = m.group(6);
                    // Credit-card statement: `+` prefix denotes cashback / credit.
                    // Trailing "Cr" also = credit. Everything else = debit (spend).
                    boolean isCredit = "+".equals(sign)
                            || (flag != null && flag.toUpperCase(Locale.ROOT).startsWith("C"));
                    isDebit = !isCredit;
                } else {
                    date = parseDate(m.group(1));
                    if (date == null) continue;
                    description = m.group(2).trim();
                    amount = parseAmount(m.group(3));
                    isDebit = determineDebit(m.group(4), description);
                }

                if (description.isBlank() || amount.signum() == 0) continue;
                // Drop obvious non-transactions (e.g. summary rows like "Total")
                if (description.toLowerCase(Locale.ROOT).matches(".*(total|balance|opening|closing).*")) continue;

                Category suggested = categoryService.suggest(description, userId);

                transactions.add(ParsedTransactionDto.builder()
                        .description(description)
                        .merchantName(extractMerchantName(description))
                        .amount(amount)
                        .transactionDate(date)
                        .isDebit(isDebit)
                        .suggestedCategoryId(suggested == null ? null : suggested.getId())
                        .build());
            } catch (Exception e) {
                log.debug("Could not parse line: {} — {}", line, e.getMessage());
            }
        }
        return transactions;
    }

    private LocalDate parseDate(String dateStr) {
        String s = dateStr.trim();
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try { return LocalDate.parse(s, fmt); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private BigDecimal parseAmount(String amountStr) {
        return new BigDecimal(amountStr.replace(",", ""));
    }

    private boolean determineDebit(String drCrFlag, String description) {
        if (drCrFlag != null) return drCrFlag.toUpperCase(Locale.ROOT).startsWith("D");
        String lower = description.toLowerCase(Locale.ROOT);
        return !(lower.contains("credit") || lower.contains("refund")
                || lower.contains("cashback") || lower.contains("reversal"));
    }

    private String extractMerchantName(String description) {
        // Take the first "word block" after stripping common prefixes / tokens
        String cleaned = description
                .replaceAll("(?i)^(upi|neft|imps|pos|atm|raz\\*|pay[a-z]*\\s*)+", "")
                .trim();
        String[] parts = cleaned.split("[\\s\\*/|-]+");
        if (parts.length == 0) return description;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            if (i > 0) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
