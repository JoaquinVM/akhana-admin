package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.model.Product;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// COM-21: the same search as the product box of the POS (POS-01, shared/utils/product-search.ts), on the server so the
// catalog never leaves it. Barcodes are not looked at: a supplier's text does not carry them.
final class ProductTextMatcher {

    static final int EXACT_SCORE = 500;

    record Match(Product product, int score) {}

    private ProductTextMatcher() {}

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    /** Every product matching the text, best first; when none has every word, similar words are accepted. */
    static List<Match> search(String term, List<Product> products) {
        String normalized = normalize(term);
        List<String> tokens = words(normalized);
        if (tokens.isEmpty()) {
            return List.of();
        }
        List<Match> exact = rank(normalized, tokens, products, false);
        return exact.isEmpty() ? rank(normalized, tokens, products, true) : exact;
    }

    private static List<Match> rank(String term, List<String> tokens, List<Product> products, boolean similar) {
        List<Match> matches = new ArrayList<>();
        for (Product product : products) {
            int score = productScore(term, tokens, product, similar);
            if (score > 0) {
                matches.add(new Match(product, score));
            }
        }
        matches.sort(Comparator.comparingInt(Match::score).reversed()
            .thenComparing(match -> normalize(match.product().getName())));
        return matches;
    }

    private static int productScore(String term, List<String> tokens, Product product, boolean similar) {
        String code = normalize(product.getCode());
        String name = normalize(product.getName());
        List<String> nameWords = words(name);
        String other = normalize(product.getSupplier() != null ? product.getSupplier().getName() : null) + " "
            + normalize(product.getCategory() != null ? product.getCategory().getName() : null);
        List<String> otherWords = words(other);
        int score = 0;
        for (String token : tokens) {
            int value = tokenScore(token, code, name, nameWords, other, otherWords, similar);
            if (value == 0) {
                return 0;
            }
            score += value;
        }
        if (code.equals(term)) {
            score += 1000;
        } else if (name.equals(term)) {
            score += EXACT_SCORE;
        } else if (name.startsWith(term)) {
            score += 100;
        }
        return score;
    }

    private static int tokenScore(String token, String code, String name, List<String> nameWords, String other,
                                  List<String> otherWords, boolean similar) {
        if (code.startsWith(token)) return 60;
        if (nameWords.stream().anyMatch(word -> word.startsWith(token))) return 50;
        if (code.contains(token)) return 40;
        if (name.contains(token)) return 30;
        if (otherWords.stream().anyMatch(word -> word.startsWith(token))) return 20;
        if (other.contains(token)) return 15;
        if (!similar) return 0;
        if (similarWord(token, nameWords)) return 10;
        if (similarWord(token, otherWords)) return 5;
        return 0;
    }

    private static List<String> words(String text) {
        return Arrays.stream(text.split("[^a-z0-9ñ]+")).filter(word -> !word.isEmpty()).toList();
    }

    private static int allowedTypos(String token) {
        return token.length() >= 8 ? 2 : token.length() >= 4 ? 1 : 0;
    }

    private static boolean similarWord(String token, List<String> candidates) {
        int limit = allowedTypos(token);
        return limit > 0 && candidates.stream().anyMatch(word ->
            editDistance(token, word, limit) <= limit
                || (word.length() > token.length() && editDistance(token, word.substring(0, token.length()), limit) <= limit));
    }

    // Edit distance with swapped neighbours counted as one typo, stopping early once over the limit
    private static int editDistance(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) {
            return limit + 1;
        }
        int[] before = new int[b.length() + 1];
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            current[0] = i;
            int rowMin = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int value = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    value = Math.min(value, before[j - 2] + 1);
                }
                current[j] = value;
                rowMin = Math.min(rowMin, value);
            }
            if (rowMin > limit) {
                return limit + 1;
            }
            before = previous;
            previous = current;
        }
        return previous[b.length()];
    }
}
