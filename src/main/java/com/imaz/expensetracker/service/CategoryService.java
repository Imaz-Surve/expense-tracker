package com.imaz.expensetracker.service;

import com.imaz.expensetracker.dto.TransactionDto.CategoryDto;
import com.imaz.expensetracker.dto.TransactionDto.CategoryRequest;
import com.imaz.expensetracker.entity.Category;
import com.imaz.expensetracker.entity.User;
import com.imaz.expensetracker.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepo;

    /** Ordered map: name -> [icon, color, keywords...] */
    private static final Map<String, DefaultCategory> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("Food & Dining", new DefaultCategory("🍔", "#f59e0b", List.of(
                "swiggy", "zomato", "uber eats", "restaurant", "cafe", "coffee",
                "hotel", "domino", "pizza", "burger", "kfc", "mcdonald", "subway",
                "biryani", "food", "dining", "dine", "eatery", "barbeque")));
        DEFAULTS.put("Transport", new DefaultCategory("🚗", "#3b82f6", List.of(
                "uber", "ola", "rapido", "petrol", "fuel", "diesel", "metro",
                "irctc", "railway", "bus", "cab", "taxi", "parking", "toll",
                "redbus", "indigo", "spicejet", "air india")));
        DEFAULTS.put("Shopping", new DefaultCategory("🛍️", "#ec4899", List.of(
                "amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho",
                "snapdeal", "tatacliq", "reliance", "zara", "h&m", "shopping",
                "mall", "retail", "store", "clothe")));
        DEFAULTS.put("Utilities", new DefaultCategory("💡", "#8b5cf6", List.of(
                "electricity", "bescom", "mseb", "tneb", "water bill", "gas bill",
                "airtel", "jio", "vi ", "vodafone", "bsnl", "broadband", "wifi",
                "internet", "recharge", "postpaid", "prepaid", "mobile bill")));
        DEFAULTS.put("Health", new DefaultCategory("💊", "#10b981", List.of(
                "pharmacy", "medplus", "apollo", "hospital", "clinic", "doctor",
                "medicine", "health", "pharmeasy", "1mg", "netmeds", "dental",
                "lab test", "diagnostic", "wellness")));
        DEFAULTS.put("Entertainment", new DefaultCategory("🎬", "#f97316", List.of(
                "netflix", "spotify", "prime video", "hotstar", "zee5", "sonyliv",
                "pvr", "inox", "bookmyshow", "gaming", "playstation", "xbox",
                "youtube premium", "gaana", "jiotv", "subscription")));
        DEFAULTS.put("Travel", new DefaultCategory("✈️", "#06b6d4", List.of(
                "makemytrip", "goibibo", "yatra", "cleartrip", "booking.com",
                "airbnb", "oyo", "hotel booking", "resort", "travel", "tour",
                "holiday", "vacation")));
        DEFAULTS.put("Education", new DefaultCategory("📚", "#64748b", List.of(
                "udemy", "coursera", "unacademy", "byjus", "school", "college",
                "university", "tuition", "education", "course", "training",
                "workshop", "seminar")));
        DEFAULTS.put("Groceries", new DefaultCategory("🛒", "#22c55e", List.of(
                "bigbasket", "grofers", "blinkit", "zepto", "dunzo", "instamart",
                "dmart", "supermarket", "grocery", "vegetables", "fruits", "kirana")));
        DEFAULTS.put("Other", new DefaultCategory("📦", "#94a3b8", List.of()));
    }

    @Transactional
    public void seedDefaultsForUser(User user) {
        List<Category> toSave = new ArrayList<>();
        for (Map.Entry<String, DefaultCategory> e : DEFAULTS.entrySet()) {
            String slug = slugify(e.getKey());
            if (categoryRepo.findByUserIdAndSlug(user.getId(), slug).isPresent()) continue;
            toSave.add(Category.builder()
                    .name(e.getKey())
                    .slug(slug)
                    .icon(e.getValue().icon)
                    .color(e.getValue().color)
                    .isSystem(true)
                    .user(user)
                    .keywords(new ArrayList<>(e.getValue().keywords))
                    .build());
        }
        categoryRepo.saveAll(toSave);
    }

    public List<CategoryDto> listForUser(Long userId) {
        return categoryRepo.findByUserIdOrderByNameAsc(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public CategoryDto create(User user, CategoryRequest req) {
        String name = req.getName().trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Name is required");
        if (categoryRepo.existsByUserIdAndNameIgnoreCase(user.getId(), name)) {
            throw new IllegalArgumentException("Category '" + name + "' already exists");
        }
        Category c = Category.builder()
                .name(name)
                .slug(slugify(name) + "-" + System.currentTimeMillis())
                .icon(req.getIcon())
                .color(req.getColor() == null ? "#94a3b8" : req.getColor())
                .isSystem(false)
                .user(user)
                .keywords(req.getKeywords() == null ? new ArrayList<>() : new ArrayList<>(req.getKeywords()))
                .build();
        return toDto(categoryRepo.save(c));
    }

    @Transactional
    public CategoryDto update(User user, Long id, CategoryRequest req) {
        Category c = categoryRepo.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        if (req.getName() != null && !req.getName().isBlank()) c.setName(req.getName().trim());
        if (req.getColor() != null) c.setColor(req.getColor());
        if (req.getIcon() != null) c.setIcon(req.getIcon());
        if (req.getKeywords() != null) c.setKeywords(new ArrayList<>(req.getKeywords()));
        return toDto(categoryRepo.save(c));
    }

    @Transactional
    public void delete(User user, Long id) {
        Category c = categoryRepo.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        if (Boolean.TRUE.equals(c.getIsSystem())) {
            throw new IllegalArgumentException("System categories cannot be deleted");
        }
        categoryRepo.delete(c);
    }

    /** Keyword-based suggestion; returns null if no match. */
    public Category suggest(String description, Long userId) {
        if (description == null) return null;
        String lower = description.toLowerCase(Locale.ROOT);
        List<Category> cats = categoryRepo.findByUserIdOrderByNameAsc(userId);
        Category fallback = null;
        for (Category c : cats) {
            if ("other".equalsIgnoreCase(c.getSlug()) || "Other".equalsIgnoreCase(c.getName())) {
                fallback = c;
            }
            if (c.getKeywords() == null) continue;
            for (String kw : c.getKeywords()) {
                if (kw != null && !kw.isBlank() && lower.contains(kw.toLowerCase(Locale.ROOT))) {
                    return c;
                }
            }
        }
        return fallback;
    }

    public CategoryDto toDto(Category c) {
        return CategoryDto.builder()
                .id(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .color(c.getColor())
                .icon(c.getIcon())
                .isSystem(c.getIsSystem())
                .keywords(c.getKeywords())
                .build();
    }

    private static String slugify(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private record DefaultCategory(String icon, String color, List<String> keywords) {}
}
