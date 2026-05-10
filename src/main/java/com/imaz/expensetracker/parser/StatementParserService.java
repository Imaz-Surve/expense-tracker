package com.imaz.expensetracker.parser;

import com.imaz.expensetracker.entity.Transaction;
import com.imaz.expensetracker.entity.Transaction.Category;
import com.imaz.expensetracker.entity.Transaction.StatementType;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessRead;
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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class StatementParserService {

    private static final Map<Category, List<String>> CATEGORY_KEYWORDS = Map.of(
            Category.FOOD_AND_DINING, List.of(
                    "swiggy", "zomato", "uber eats", "restaurant", "cafe", "coffee",
                    "hotel", "domino", "pizza", "burger", "kfc", "mcdonald", "subway",
                    "biryani", "food", "dining", "dine", "eatery", "barbeque"
            ),
            Category.TRANSPORT, List.of(
                    "uber", "ola", "rapido", "petrol", "fuel", "diesel", "metro",
                    "irctc", "railway", "bus", "cab", "taxi", "parking", "toll",
                    "redbus", "makemytrip flight", "indigo", "spicejet", "air india"
            ),
            Category.SHOPPING, List.of(
                    "amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho",
                    "snapdeal", "tatacliq", "reliance", "zara", "h&m", "shopping",
                    "mall", "retail", "store", "clothe"
            ),
            Category.UTILITIES, List.of(
                    "electricity", "bescom", "mseb", "tneb", "water bill", "gas bill",
                    "airtel", "jio", "vi ", "vodafone", "bsnl", "broadband", "wifi",
                    "internet", "recharge", "postpaid", "prepaid", "mobile bill"
            ),
            Category.HEALTH, List.of(
                    "pharmacy", "medplus", "apollo", "hospital", "clinic", "doctor",
                    "medicine", "health", "pharmeasy", "1mg", "netmeds", "dental",
                    "lab test", "diagnostic", "wellness"
            ),
            Category.ENTERTAINMENT, List.of(
                    "netflix", "spotify", "prime video", "hotstar", "zee5", "sonyliv",
                    "pvr", "inox", "bookmyshow", "gaming", "playstation", "xbox",
                    "youtube premium", "gaana", "jiotv", "subscription"
            ),
            Category.TRAVEL, List.of(
                    "makemytrip", "goibibo", "yatra", "cleartrip", "booking.com",
                    "airbnb", "oyo", "hotel booking", "resort", "travel", "tour",
                    "holiday", "vacation"
            ),
            Category.EDUCATION, List.of(
                    "udemy", "coursera", "unacademy", "byjus", "school", "college",
                    "university", "tuition", "education", "course", "training",
                    "workshop", "seminar"
            ),
            Category.GROCERIES, List.of(
                    "bigbasket", "grofers", "blinkit", "zepto", "dunzo", "instamart",
                    "dmart", "supermarket", "grocery", "vegetables", "fruits", "kirana"
            )
    );

    // â”€â”€ Date patterns commonly found in Indian bank statements â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    // â”€â”€ Transaction line pattern: date + description + amount â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Handles formats like: 12/03/2024  SWIGGY ORDER 12345  450.00  Dr
    private static final Pattern TRANSACTION_PATTERN = Pattern.compile(
            "(?i)(\\d{2}[/\\-]\\d{2}[/\\-]\\d{2,4}|\\d{2}\\s+[A-Za-z]{3}\\s+\\d{4})" +
                    "\\s+(.{5,60}?)\\s+" +
                    "(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?)\\s*" +
                    "(Dr|Cr|DR|CR|Debit|Credit|D|C)?"
    );

    public List<Transaction> parsePdf(MultipartFile file, StatementType statementType)
            throws IOException {

        String rawText = extractText(file);
        log.info("Extracted {} characters from PDF: {}", rawText.length(), file.getOriginalFilename());

        List<Transaction> transactions = extractTransactions(rawText, statementType);
        log.info("Parsed {} transactions from {}", transactions.size(), file.getOriginalFilename());
        return transactions;
    }

    private String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF((RandomAccessRead) file.getInputStream())) {
            //Loader PDDocument.load(file.getInputStream())
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private List<Transaction> extractTransactions(String text, StatementType statementType) {
        List<Transaction> transactions = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isBlank() || line.length() < 10) continue;

            Matcher m = TRANSACTION_PATTERN.matcher(line);
            if (m.find()) {
                try {
                    LocalDate date = parseDate(m.group(1));
                    if (date == null) continue;

                    String description = m.group(2).trim();
                    BigDecimal amount = parseAmount(m.group(3));
                    boolean isDebit = determineDebit(m.group(4), description);

                    // Skip credits (income) â€” only track expenses
                    if (!isDebit) continue;

                    Category category = categorize(description);

                    Transaction tx = Transaction.builder()
                            .description(description)
                            .merchantName(extractMerchantName(description))
                            .amount(amount)
                            .transactionDate(date)
                            .category(category)
                            .statementType(statementType)
                            .isDebit(true)
                            .build();

                    transactions.add(tx);
                } catch (Exception e) {
                    log.debug("Could not parse line: {}", line);
                }
            }
        }
        return transactions;
    }

    public Category categorize(String description) {
        String lower = description.toLowerCase();
        for (Map.Entry<Category, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return Category.OTHER;
    }

    private LocalDate parseDate(String dateStr) {
        dateStr = dateStr.trim();
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null) return BigDecimal.ZERO;
        return new BigDecimal(amountStr.replace(",", ""));
    }

    private boolean determineDebit(String drCrFlag, String description) {
        if (drCrFlag != null) {
            String flag = drCrFlag.toUpperCase();
            return flag.startsWith("D");
        }
        // Heuristic: if description contains credit-like words, treat as credit
        String lower = description.toLowerCase();
        return !(lower.contains("credit") || lower.contains("refund")
                || lower.contains("cashback") || lower.contains("reversal"));
    }

    private String extractMerchantName(String description) {
        // Take first 2-3 words as merchant name
        String[] parts = description.split("\\s+");
        int wordCount = Math.min(parts.length, 3);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wordCount; i++) {
            if (i > 0) sb.append(" ");
            sb.append(parts[i]);
        }
        return sb.toString().toUpperCase();
    }
}
