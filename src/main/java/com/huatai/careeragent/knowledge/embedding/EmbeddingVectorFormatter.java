package com.huatai.careeragent.knowledge.embedding;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EmbeddingVectorFormatter {
    public String toPgVector(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.ROOT, "%.8f", vector[i]));
        }
        return builder.append(']').toString();
    }
}
