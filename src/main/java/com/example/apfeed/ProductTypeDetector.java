package com.example.apfeed;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
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

    // Load dictionary once, from either classpath or plain file product_types.txt
    static {
        loadDictionary();
    }

    private static void loadDictionary() {
        if (!dictRaw.isEmpty()) return; // already loaded

        InputStream in = null;
        try {
            // 1) Try classpath resource: src/main/resources/product_types.txt
            in = ProductTypeDetector.class.getResourceAsStream("/product_types.txt");

            // 2) Fallback: plain file in working directory: ./product_types.txt
            if (in == null) {
                File f = new File("product_types.txt");
                if (f.exists() && f.isFile()) {
                    in = new FileInputStream(f);
                    System.out.println("Loaded product types from file: " + f.getAbsolutePath());
                }
            } else {
                System.out.println("Loaded product types from classpath /product_types.txt");
            }

            if (in == null) {
                System.err.println("WARNING: product_types.txt not found on classpath or in working directory. Product types will be 'unknown'.");
                return;
            }

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

            System.out.println("Loaded " + dictRaw.size() + " product type entries.");

        } catch (Exception e) {
            System.err.println("ERROR loading product_types.txt: " + e.getMessage());
        }
    }

    private static String normPhrase(String s) {
        String out = s.toLowerCase();
        // allow +, -, / so things like "t-shirt" or "usb-c" survive
        out = out.replaceAll("[^a-z0-9\\s\\+\\-/]", " ");
        out = out.replaceAll("\\s+", " ").trim();
        return out;
    }

    public static String detect(String title, String description, List<String> breadcrumbs) {
        // Ensure dictionary is loaded (in case classloader did something weird)
        if (dictRaw.isEmpty()) {
            loadDictionary();
        }

        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append(title).append(" ");
        if (description != null) sb.append(description).append(" ");
        if (breadcrumbs != null) {
            for (String b : breadcrumbs) sb.append(b).append(" ");
        }
        String hay = normPhrase(sb.toString());
        if (hay.isEmpty() || dictRaw.isEmpty()) return "unknown";

        // 1) Exact / phrase match: longest matching phrase wins
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

        // 2) Breadcrumb head match – if a breadcrumb looks like a category
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

        // 3) Token overlap scoring (looser match)
        Set<String> tokens = Arrays.stream(hay.split("\\s+"))
                .filter(t -> t.length() >= 3 && !STOPWORDS.contains(t))
                .collect(Collectors.toSet());

        String bestLoose = null;
        double bestScore = 0.0;
        for (int i = 0; i < dictRaw.size(); i++) {
            String raw = dictRaw.get(i);
            String norm = dictNorm.get(i);
            if (norm.isEmpty()) continue;

            Set<String> words = Arrays.stream(norm.split("\\s+"))
                    .filter(t -> t.length() >= 3 && !STOPWORDS.contains(t))
                    .collect(Collectors.toSet());

            if (words.isEmpty()) continue;

            Set<String> inter = new HashSet<>(words);
            inter.retainAll(tokens);
            if (!inter.isEmpty()) {
                double score = inter.size() + 0.25 * norm.length();
                if (score > bestScore) {
                    bestScore = score;
                    bestLoose = raw;
                }
            }
        }

        return bestLoose != null ? bestLoose : "unknown";
    }
}
