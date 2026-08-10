package com.casino.casinoerp.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class IdentityDocumentNumberNormalizer {

    public String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Document number is required.");
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace("-", "");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Document number is required.");
        }

        return normalized;
    }
}
