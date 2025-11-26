package com.example.apfeed;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProductTypeDetector {
    private static final Set<String> STOPWORDS = Set.of(
            "a","an","the","and","or","with","to","of","in","on","for"
    );

    private static final List<String> dictRaw = new ArrayList<>();
    private static final List<String> dictNorm = new ArrayList<>();

    static {
        try {
            InputStream in = ProductTypeDetector.class.getResourceAsStream("/product_types.txt");
            if (in != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            dictRaw.add(line);
                            dictNorm.add(normPhrase(line));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static String normPhrase(String s) {
        String out = s.toLowerCase();
        out = out.replaceAll("[^a-z0-9\\s\\+\\-/]", " ");
        out = out.replaceAll("\\s+", " ").trim();
        return out;
    }

    public static String detect(String title, String description, List<String> breadcrumbs) {
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append(title).append(" ");
        if (description != null) sb.append(description).append(" ");
        if (breadcrumbs != null) {
            for (String b : breadcrumbs) sb.append(b).append(" ");
        }
        String hay = normPhrase(sb.toString());
        if (hay.isEmpty() || dictRaw.isEmpty()) return "unknown";

        // 1) exact/phrase match
        String best = null;
        int bestLen = 0;
        for (int i = 0; i < dictRaw.size(); i++) {
            String raw = dictRaw.get(i);
            String norm = dictNorm.get(i);
            if (norm.isEmpty()) continue;
            Pattern p = Pattern.compile("(^|\\W)" + Pattern.quote(norm) + "(\\W|$)");
            if (p.matcher(hay).find()) {
                int len = norm.length();
                if (len > bestLen) {
                    bestLen = len;
                    best = raw;
                }
            }
        }
        if (best != null) return best;

        // 2) breadcrumb head match
        if (breadcrumbs != null) {
            for (String b : breadcrumbs) {
                String bn = normPhrase(b);
                if (bn.isEmpty()) continue;
                for (int i = 0; i < dictRaw.size(); i++) {
                    String raw = dictRaw.get(i);
                    String norm = dictNorm.get(i);
                    if (!norm.isEmpty() && (norm.contains(bn) || bn.contains(norm))) {
                        return raw;
                    }
                }
            }
        }

        // 3) token containment
        Set<String> tokens = Arrays.stream(hay.split("\\s+"))
                .filter(t -> t.length() >= 3 && !STOPWORDS.contains(t))
                .collect(Collectors.toSet());

        best = null;
        double bestScore = 0.0;
        for (int i = 0; i < dictRaw.size(); i++) {
            String raw = dictRaw.get(i);
            String norm = dictNorm.get(i);
            if (norm.isEmpty()) continue;
            Set<String> words = new HashSet<>(Arrays.asList(norm.split("\\s+")));
            Set<String> inter = new HashSet<>(words);
            inter.retainAll(tokens);
            if (!inter.isEmpty()) {
                double score = inter.size() + 0.25 * norm.length();
                if (score > bestScore) {
                    bestScore = score;
                    best = raw;
                }
            }
        }

        return best != null ? best : "unknown";
    }
}
