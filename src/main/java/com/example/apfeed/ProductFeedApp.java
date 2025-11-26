package com.example.apfeed;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

public class ProductFeedApp {

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage:");
            System.err.println("  java -jar ap-manual-feed-extractor.jar <companyName> <currencyCode> <urlListFile> <outputXlsx>");
            System.exit(1);
        }

        String companyName = args[0].trim();
        String currencyCode = args[1].trim().toUpperCase(Locale.ROOT);
        String urlListFile = args[2];
        String outputFile = args[3];

        System.out.println("Company: " + companyName);
        System.out.println("Currency: " + currencyCode);
        System.out.println("URL list file: " + urlListFile);
        System.out.println("Output Excel: " + outputFile);

        List<String> inputUrls = readUrlList(urlListFile);
        if (inputUrls.isEmpty()) {
            System.err.println("No URLs found in " + urlListFile);
            System.exit(1);
        }

        List<String> deduped = inputUrls.stream().distinct().collect(Collectors.toList());
        System.out.println("Got " + deduped.size() + " unique input URL(s).");

        List<Product> scraped = new ArrayList<>();

        try (BrowserPool pool = new BrowserPool()) {
            pool.start();
            ProductScraper scraper = new ProductScraper(pool);

            List<String> allLinks = new ArrayList<>();

            // Discover product links if URL is not obviously a product
            for (String u : deduped) {
                String path = URI.create(u).getPath();
                if (UrlUtils.looksLikeProductPath(path)) {
                    allLinks.add(u);
                } else {
                    System.out.println("Discovering product links from listing: " + u);
                    List<String> found = scraper.discoverLinksWithBrowser(u, 2500);
                    System.out.println("  Found " + found.size() + " product link(s).");
                    allLinks.addAll(found);
                }
            }

            // Fallback: if still empty, keep only input URLs that look like products
            if (allLinks.isEmpty()) {
                allLinks = deduped.stream()
                        .filter(u -> UrlUtils.looksLikeProductPath(URI.create(u).getPath()))
                        .collect(Collectors.toList());
            }

            // Stable de-dup
            LinkedHashSet<String> seen = new LinkedHashSet<>(allLinks);
            allLinks = new ArrayList<>(seen);

            System.out.println("Total product URLs to scrape: " + allLinks.size());
            String prefix = Arrays.stream(companyName.split("\\s+"))
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.substring(0, 1).toUpperCase(Locale.ROOT))
                    .collect(Collectors.joining());

            long start = System.currentTimeMillis();
            int idx = 0;
            for (String url : allLinks) {
                idx++;
                System.out.printf("(%d/%d) Scraping %s%n", idx, allLinks.size(), url);
                try {
                    Product p = scraper.scrapeProduct(url, idx, prefix, currencyCode, companyName);
                    scraped.add(p);
                    System.out.println("  -> OK: " + p.title + " | " + p.price);
                } catch (Exception e) {
                    System.err.println("  -> Error: " + e.getMessage());
                }
            }
            long elapsed = (System.currentTimeMillis() - start) / 1000;
            System.out.printf("Done. Scraped %d product(s) in %d s%n", scraped.size(), elapsed);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        try {
            ExcelExporter exporter = new ExcelExporter();
            boolean append = false;  // change to true if you want append behaviour
            exporter.export(scraped, outputFile, append);
            System.out.println("Feed written to: " + outputFile);
        } catch (Exception e) {
            System.err.println("Error writing Excel: " + e.getMessage());
            System.exit(1);
        }
    }

    private static List<String> readUrlList(String path) {
        List<String> urls = new ArrayList<>();
        File f = new File(path);
        if (!f.exists()) {
            System.err.println("URL list file not found: " + path);
            return urls;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) urls.add(line);
            }
        } catch (Exception e) {
            System.err.println("Error reading URL list: " + e.getMessage());
        }
        return urls;
    }
}
