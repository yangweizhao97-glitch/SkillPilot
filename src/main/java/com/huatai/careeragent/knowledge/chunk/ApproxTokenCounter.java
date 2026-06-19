package com.huatai.careeragent.knowledge.chunk;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ApproxTokenCounter {
    public int count(String text) {
        return tokenize(text).size();
    }

    public List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }

        StringBuilder word = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                flushWord(word, tokens);
            } else if (isCjk(ch)) {
                flushWord(word, tokens);
                tokens.add(String.valueOf(ch));
            } else if (Character.isLetterOrDigit(ch)) {
                word.append(ch);
            } else {
                flushWord(word, tokens);
                tokens.add(String.valueOf(ch));
            }
        }
        flushWord(word, tokens);
        return tokens;
    }

    private void flushWord(StringBuilder word, List<String> tokens) {
        if (!word.isEmpty()) {
            tokens.add(word.toString());
            word.setLength(0);
        }
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }
}
