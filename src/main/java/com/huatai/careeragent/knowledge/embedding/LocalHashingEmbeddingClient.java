package com.huatai.careeragent.knowledge.embedding;

import com.huatai.careeragent.knowledge.chunk.ApproxTokenCounter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Component
public class LocalHashingEmbeddingClient implements EmbeddingClient {
    private final EmbeddingProperties embeddingProperties;
    private final ApproxTokenCounter tokenCounter;

    public LocalHashingEmbeddingClient(EmbeddingProperties embeddingProperties, ApproxTokenCounter tokenCounter) {
        this.embeddingProperties = embeddingProperties;
        this.tokenCounter = tokenCounter;
    }

    @Override
    public EmbeddingResponse embed(String input) {
        int dimension = embeddingProperties.getEmbeddingDimension();
        float[] vector = new float[dimension];
        List<String> tokens = tokenCounter.tokenize(input == null ? "" : input.toLowerCase());
        for (String token : tokens) {
            byte[] digest = sha256(token);
            int index = Math.floorMod(toInt(digest, 0), dimension);
            float sign = (digest[4] & 1) == 0 ? 1.0f : -1.0f;
            vector[index] += sign;
        }
        normalize(vector);
        return new EmbeddingResponse(vector, tokens.size(), "local-hashing-" + dimension);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private int toInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private void normalize(float[] vector) {
        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0.0) {
            return;
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
    }
}
