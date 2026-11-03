package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.model.Product;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

// COM-21: the same search as the product box of the POS (POS-01, shared/utils/product-search.ts), on the server so the
// catalog never leaves it. Barcodes are not looked at: a supplier's text does not carry them.
final class ProductTextMatcher {

    static final int EXACT_SCORE = 500;
    private static final int MIN_PARTIAL_WORD = 3;
    private static final Set<String> STOP_WORDS = Set.of("con", "del", "las", "los", "para", "por", "una", "uno", "unos", "unas");

    // clear: every word is there, at most in plural. With typos or missing words it can only be a suggestion
    record Match(Product product, int score, boolean clear) {}

    private enum Tolerance { NONE, PLURALS, TYPOS }

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
        for (Tolerance tolerance : Tolerance.values()) {
            List<Match> matches = rank(normalized, tokens, products, tolerance);
            if (!matches.isEmpty()) {
                return matches;
            }
        }
        return List.of();
    }

    /**
     * COM-21: when no product has every word, the ones with the most of them, to suggest and never to choose: «100 grs»
     * still finds «100 gramos». Sizes rank the results («67», «70», «2L») but alone do not make a match, or they would
     * bring half the catalog.
     */
    static List<Match> searchPartial(String term, List<Product> products) {
        List<String> tokens = words(normalize(term));
        List<String> meaningful = tokens.stream().filter(token -> hasDigits(token) || isWord(token)).toList();
        if (meaningful.stream().noneMatch(ProductTextMatcher::isWord)) {
            return List.of();
        }
        record Partial(Product product, int words, int score) {}
        List<Partial> partial = new ArrayList<>();
        for (Product product : products) {
            String code = normalize(product.getCode());
            String name = normalize(product.getName());
            List<String> nameWords = words(name);
            int found = 0;
            int score = 0;
            boolean withWord = false;
            for (String token : meaningful) {
                int value = tokenScore(token, code, name, nameWords, "", List.of(), true);
                if (value > 0) {
                    found++;
                    score += value;
                    withWord |= isWord(token);
                }
            }
            if (withWord) {
                partial.add(new Partial(product, found, score));
            }
        }
        partial.sort(Comparator.comparingInt(Partial::words).reversed()
            .thenComparing(Comparator.comparingInt(Partial::score).reversed())
            .thenComparing(match -> normalize(match.product().getName())));
        return partial.stream().map(match -> new Match(match.product(), match.score(), false)).toList();
    }

    private static boolean hasDigits(String token) {
        return token.chars().anyMatch(Character::isDigit);
    }

    private static boolean isWord(String token) {
        return token.length() >= MIN_PARTIAL_WORD && !hasDigits(token) && !STOP_WORDS.contains(token);
    }

    private static List<Match> rank(String term, List<String> tokens, List<Product> products, Tolerance tolerance) {
        List<Match> matches = new ArrayList<>();
        for (Product product : products) {
            int score = productScore(term, tokens, product, tolerance);
            if (score > 0) {
                matches.add(new Match(product, score, tolerance != Tolerance.TYPOS));
            }
        }
        matches.sort(Comparator.comparingInt(Match::score).reversed()
            .thenComparing(match -> normalize(match.product().getName())));
        return matches;
    }

    private static int productScore(String term, List<String> tokens, Product product, Tolerance tolerance) {
        String code = normalize(product.getCode());
        String name = normalize(product.getName());
        List<String> nameWords = words(name);
        String other = normalize(product.getSupplier() != null ? product.getSupplier().getName() : null) + " "
            + normalize(product.getCategory() != null ? product.getCategory().getName() : null);
        List<String> otherWords = words(other);
        int score = 0;
        for (String token : tokens) {
            int value = tokenScore(token, code, name, nameWords, other, otherWords, tolerance == Tolerance.TYPOS);
            if (value == 0 && tolerance == Tolerance.PLURALS) {
                for (String singular : singulars(token)) {
                    value = Math.max(value, tokenScore(singular, code, name, nameWords, other, otherWords, false));
                }
            }
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
        // A size is exact: 250ml is not 200ml, and 50ml is not inside 250ml
        if (hasDigits(token)) {
            return Stream.concat(nameWords.stream(), words(code).stream()).anyMatch(word -> sameSize(token, word)) ? 50 : 0;
        }
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

    // The written size may leave out the unit: 500 is 500gr, but never 5000gr
    private static boolean sameSize(String token, String word) {
        return word.startsWith(token)
            && (word.length() == token.length() || !Character.isDigit(word.charAt(token.length())));
    }

    private static List<String> singulars(String token) {
        if (!token.endsWith("s") || token.length() < MIN_PARTIAL_WORD + 1) {
            return List.of();
        }
        String withoutS = token.substring(0, token.length() - 1);
        return token.endsWith("es") ? List.of(withoutS, token.substring(0, token.length() - 2)) : List.of(withoutS);
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
