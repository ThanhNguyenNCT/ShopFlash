package com.shopflash.modules.indexing.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tách chuỗi khách gõ thành từ khóa, dùng chung cho RediSearch và Oracle Text để hai nguồn tìm như nhau.
 * Chỉ giữ chữ và số (kể cả chữ có dấu tiếng Việt), đưa về chữ thường, mỗi từ ít nhất 2 ký tự, tối đa 5 từ, bỏ trùng.
 * Bỏ các từ là toán tử của Oracle Text (and, or, not, near...) và stopword tiếng Anh mặc định của RediSearch:
 * để nguyên thì Oracle Text báo lỗi cú pháp, còn RediSearch tự bỏ, khiến hai nguồn khác nhau.
 * Vì chỉ còn chữ và số nên không còn ký tự đặc biệt nào của hai cú pháp truy vấn.
 */
public final class Keywords {

    public static final int MAX_WORDS = 5;
    public static final int MIN_LENGTH = 2;

    static final Set<String> STOP_WORDS = Set.of(
            // Toán tử và từ dành riêng của Oracle Text
            "about", "accum", "and", "bt", "btg", "bti", "btp", "equiv", "fuzzy", "haspath", "inpath", "minus",
            "mdata", "near", "not", "nt", "ntg", "nti", "ntp", "or", "pt", "rt", "sdata", "sqe", "syn", "tr",
            "trsyn", "tt", "within",
            // Stopword tiếng Anh mặc định của RediSearch
            "an", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into", "is", "it", "no", "of", "on",
            "such", "that", "the", "their", "then", "there", "these", "they", "this", "to", "was", "will", "with");

    private Keywords() {
    }

    public static List<String> parse(String input) {
        List<String> words = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return words;
        }
        String text = Normalizer.normalize(input, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < text.length() && words.size() < MAX_WORDS) {
            int cp = text.codePointAt(i);
            if (Character.isLetterOrDigit(cp) || Character.getType(cp) == Character.NON_SPACING_MARK) {
                current.appendCodePoint(cp);
            } else {
                add(words, current);
            }
            i += Character.charCount(cp);
        }
        if (words.size() < MAX_WORDS) {
            add(words, current);
        }
        return words;
    }

    private static void add(List<String> words, StringBuilder current) {
        String word = current.toString();
        current.setLength(0);
        if (word.codePointCount(0, word.length()) < MIN_LENGTH || STOP_WORDS.contains(word) || words.contains(word)) {
            return;
        }
        words.add(word);
    }
}
