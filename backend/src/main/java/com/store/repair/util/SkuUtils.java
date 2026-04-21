package com.store.repair.util;

import com.store.repair.config.SanitizadorTexto;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class SkuUtils {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern INVALID_CHARS = Pattern.compile("[^A-Z0-9-]");
    private static final Pattern MULTIPLE_DASHES = Pattern.compile("-{2,}");
    private static final Pattern SKU_PATTERN = Pattern.compile("^[A-Z0-9]+(?:-[A-Z0-9]+)*$");

    private static final Map<String, String> CATEGORY_CODES = new LinkedHashMap<>();
    private static final Map<String, String> QUALITY_CODES = new LinkedHashMap<>();

    static {
        CATEGORY_CODES.put("bateria", "BAT");
        CATEGORY_CODES.put("pantalla", "PAN");
        CATEGORY_CODES.put("display", "DIS");
        CATEGORY_CODES.put("modulo", "MOD");
        CATEGORY_CODES.put("flex", "FLEX");
        CATEGORY_CODES.put("camara", "CAM");
        CATEGORY_CODES.put("cargador", "CRG");
        CATEGORY_CODES.put("cable", "CAB");
        CATEGORY_CODES.put("tapa", "TAP");
        CATEGORY_CODES.put("mica", "MIC");
        CATEGORY_CODES.put("parlante", "PAR");
        CATEGORY_CODES.put("auricular", "AUR");

        QUALITY_CODES.put("original", "ORI");
        QUALITY_CODES.put("premium", "PRE");
        QUALITY_CODES.put("service pack", "SP");
        QUALITY_CODES.put("incell", "INC");
        QUALITY_CODES.put("oled", "OLED");
        QUALITY_CODES.put("amoled", "AMOLED");
        QUALITY_CODES.put("genérico", "GEN");
        QUALITY_CODES.put("generico", "GEN");
        QUALITY_CODES.put("compatible", "COM");
        QUALITY_CODES.put("refurbished", "REF");
    }

    private SkuUtils() {
    }

    public static String normalize(String rawSku) {
        String clean = sanitizeToken(rawSku);
        if (clean == null || clean.isBlank()) {
            return null;
        }

        String normalized = clean
                .replace(' ', '-')
                .replace('_', '-')
                .replace('/', '-');

        normalized = INVALID_CHARS.matcher(normalized).replaceAll("");
        normalized = MULTIPLE_DASHES.matcher(normalized).replaceAll("-");
        normalized = normalized.replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? null : normalized;
    }

    public static boolean isValid(String sku) {
        String normalized = normalize(sku);
        return normalized != null && SKU_PATTERN.matcher(normalized).matches();
    }

    public static String suggest(String categoria, String marca, String nombreModelo, String calidad) {
        String categoriaToken = firstMeaningfulToken(resolveCategoryCode(categoria), "ITEM");
        String marcaToken = firstMeaningfulToken(shortCode(marca, 3), "GEN");
        String nombreToken = firstMeaningfulToken(compactModelToken(nombreModelo), "ITEM");
        String calidadToken = firstMeaningfulToken(resolveQualityCode(calidad), null);

        StringBuilder suggestion = new StringBuilder()
                .append(categoriaToken)
                .append('-')
                .append(marcaToken)
                .append('-')
                .append(nombreToken);

        if (calidadToken != null) {
            suggestion.append('-').append(calidadToken);
        }

        return normalize(suggestion.toString());
    }

    public static String ensureUnique(String baseSku, java.util.function.Predicate<String> existsPredicate) {
        String normalized = normalize(baseSku);
        String seed = normalized == null ? "ITEM" : normalized;
        if (!existsPredicate.test(seed)) {
            return seed;
        }

        int suffix = 2;
        while (existsPredicate.test(seed + "-" + suffix)) {
            suffix++;
        }
        return seed + "-" + suffix;
    }

    private static String resolveCategoryCode(String categoria) {
        String normalized = sanitizeToken(categoria);
        if (normalized == null) {
            return null;
        }

        for (Map.Entry<String, String> entry : CATEGORY_CODES.entrySet()) {
            if (normalized.contains(entry.getKey().toUpperCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }

        return shortCode(normalized, 3);
    }

    private static String resolveQualityCode(String calidad) {
        String normalized = sanitizeToken(calidad);
        if (normalized == null) {
            return null;
        }

        for (Map.Entry<String, String> entry : QUALITY_CODES.entrySet()) {
            if (normalized.equals(entry.getKey().toUpperCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }

        return shortCode(normalized, Math.min(4, normalized.length()));
    }

    private static String compactModelToken(String value) {
        String normalized = sanitizeToken(value);
        if (normalized == null) {
            return null;
        }

        String compact = normalized.replace(" ", "-");
        compact = MULTIPLE_DASHES.matcher(compact).replaceAll("-");
        compact = compact.replaceAll("(^-|-$)", "");
        return compact;
    }

    private static String shortCode(String value, int length) {
        String normalized = sanitizeToken(value);
        if (normalized == null) {
            return null;
        }

        String collapsed = normalized.replace(" ", "");
        if (collapsed.isBlank()) {
            return null;
        }

        int end = Math.min(Math.max(length, 1), collapsed.length());
        return collapsed.substring(0, end);
    }

    private static String firstMeaningfulToken(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }

    private static String sanitizeToken(String value) {
        String cleaned = SanitizadorTexto.limpiar(value);
        if (cleaned == null || cleaned.isBlank()) {
            return null;
        }

        String normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        normalized = normalized.toUpperCase(Locale.ROOT).trim();
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }
}
