package com.example.demo.catalog.search;

import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import lombok.Value;

/** Version-one structural primitives only; alphabetic model scope is a separate approval gate. */
public final class ProductSearchNormalizer {
    public static final int MAX_QUERY_CODE_POINTS = 256;
    public static final int MAX_RAW_TOKENS = 16;

    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        StringBuilder result = new StringBuilder();
        boolean pendingSpace = false;
        for (int cp : decomposed.codePoints().toArray()) {
            if (isWhiteSpace(cp)) {
                pendingSpace = !result.isEmpty();
            } else if (Character.isISOControl(cp)) {
                throw invalid("Invalid control character in search text");
            } else if (!isVietnameseMark(cp)) {
                if (pendingSpace) {
                    result.append(' ');
                    pendingSpace = false;
                }
                result.appendCodePoint(cp == 'đ' || cp == 'Đ' ? 'd' : cp);
            }
        }
        return result.toString().toLowerCase(Locale.ROOT);
    }

    public Query normalizeQuery(String query) {
        if (query != null && query.codePointCount(0, query.length()) > MAX_QUERY_CODE_POINTS) {
            throw invalid("Keyword exceeds 256 code points");
        }
        String original = normalize(query);
        List<Token> sequence = tokenizeNormalized(original);
        if (!original.isEmpty() && sequence.isEmpty()) {
            throw invalid("Keyword contains no searchable tokens");
        }
        if (sequence.size() > MAX_RAW_TOKENS) {
            throw invalid("Keyword exceeds 16 tokens");
        }
        LinkedHashMap<String, Token> unique = new LinkedHashMap<>();
        for (Token token : sequence) {
            unique.putIfAbsent(token.getText(), token);
        }
        return new Query(original, List.copyOf(sequence), List.copyOf(unique.values()));
    }

    /** Candidate text is not subject to query length/token limits. */
    public List<Token> tokenize(String text) {
        return List.copyOf(tokenizeNormalized(normalize(text)));
    }

    private List<Token> tokenizeNormalized(String text) {
        int[] points = text.codePoints().toArray();
        List<Token> tokens = new ArrayList<>();
        int cursor = 0;
        while (cursor < points.length) {
            int start = cursor;
            if (isSign(points[cursor]) && cursor + 1 < points.length
                    && (isDigit(points[cursor + 1]) || startsDecimal(points, cursor + 1))) {
                cursor++;
            }
            if (startsDecimal(points, cursor)) {
                cursor++;
            }
            if (cursor >= points.length || !isAlphanumeric(points[cursor])) {
                cursor = start + 1;
                continue;
            }
            cursor++;
            while (cursor < points.length) {
                int cp = points[cursor];
                if (isAlphanumeric(cp)) {
                    cursor++;
                } else if (isConnector(cp) && isAlphanumeric(points[cursor - 1])
                        && cursor + 1 < points.length && isAlphanumeric(points[cursor + 1])) {
                    cursor++;
                } else if (cp == '+' && isAlphanumeric(points[cursor - 1])) {
                    int end = cursor;
                    while (end < points.length && points[end] == '+') {
                        end++;
                    }
                    int count = end - cursor;
                    if (count <= 2) {
                        cursor = end;
                    }
                    break;
                } else {
                    break;
                }
            }
            String value = new String(points, start, cursor - start);
            boolean protectedToken = value.codePoints().anyMatch(cp -> isDigit(cp) || !Character.isLetter(cp));
            tokens.add(new Token(value, protectedToken));
        }
        return tokens;
    }

    private static boolean startsDecimal(int[] points, int offset) {
        return offset + 1 < points.length && points[offset] == '.' && isDigit(points[offset + 1]);
    }

    private static boolean isAlphanumeric(int cp) {
        return Character.isLetter(cp) || isDigit(cp);
    }

    private static boolean isDigit(int cp) {
        return Character.getType(cp) == Character.DECIMAL_DIGIT_NUMBER;
    }

    private static boolean isSign(int cp) {
        return cp == '+' || cp == '-';
    }

    private static boolean isConnector(int cp) {
        return cp == '-' || cp == '_' || cp == '/' || cp == '.' || cp == '\'' || cp == '+';
    }

    private static boolean isVietnameseMark(int cp) {
        return cp == 0x300 || cp == 0x301 || cp == 0x303 || cp == 0x309
                || cp == 0x323 || cp == 0x302 || cp == 0x306 || cp == 0x31b;
    }

    private static boolean isWhiteSpace(int cp) {
        return cp >= 0x9 && cp <= 0xd || cp == 0x20 || cp == 0x85 || cp == 0xa0
                || cp == 0x1680 || cp >= 0x2000 && cp <= 0x200a || cp == 0x2028
                || cp == 0x2029 || cp == 0x202f || cp == 0x205f || cp == 0x3000;
    }

    private static InvalidCatalogRequestException invalid(String detail) {
        return new InvalidCatalogRequestException(detail);
    }

    @Value
    public static class Token {
        String text;
        boolean structurallyProtected;
    }

    @Value
    public static class Query {
        String originalNormalized;
        List<Token> sequence;
        List<Token> tokens;
    }
}
