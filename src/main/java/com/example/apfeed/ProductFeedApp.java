package com.example.apfeed;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

public class ProductFeedApp {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String companyName;
        String currencyCode;
        String urlListFile;
        String outputFile;

        // -----------------------------
        // Interactive prompt if no args
        // -----------------------------
        if (args.length < 4) {
            System.out.println("=================================");
            System.out.println(" Manual Feed Extractor");
            System.out.println("=================================\n");

            companyName = prompt(scanner,
                    "Company name (e.g. Grandpas Goody Getter)");

            currencyCode = prompt(scanner,
                    "Currency code (USD, GBP, EUR)")
                    .toUpperCase(Locale.ROOT);

            urlListFile = prompt(scanner,
                    "URL list file (e.g. Urls.txt)");

            outputFile = prompt(scanner,
                    "Output Excel file (e.g. output.xlsx)");
        } else {
            // -----------------------------
            // Legacy CLI support
            // -----------------------------
            companyName = args[0].trim();
            currencyCode = args[1].trim().toUpperCase(Locale.ROOT);
            urlListFile = args[2];
            outputFile = args[3];
        }

        System.out.println("\n--- Configuration ---");
        System.out.println("Company: " + companyName);
        System.out.println("Currency: " + currencyCode);
        System.out.println("URL list file: " + urlListFile);
        System.out.println("Output Excel: " + outputFile);
        System.out.println("----------------------\n");

        // -----------------------------
        // Validate URL list file
        // -----------------------------
        File urlFile = new File(urlListFile);
        if (!urlFile.exists()) {
            System.err.println("ERROR: URL list file not found: " + urlFile.getAbsolutePath());
            System.exit(1);
        }

        List<String> inputUrls = readUrlList(urlListFile);
        if (inputUrls.isEmpty()) {
            System.err.println("ERROR: No URLs found in " + urlListFile);
            System.exit(1);
        }

        List<String> deduped = inputUrls.stream().distinct().collect(Collectors.toList());
        System.out.println("Got " + deduped.size() + " unique input URL(s).");

        List<Product> scraped = new ArrayList<>();

        try (BrowserPool pool = new BrowserPool()) {
            pool.start();
            ProductScraper scraper = new ProductScraper(pool);

            List<String> allLinks = new ArrayList<>();

            // Always scrape the URLs the user gave directly
            for (String u : deduped) {
                allLinks.add(u);

                String path;
                try {
                    path = URI.create(u).getPath();
                } catch (Exception e) {
                    continue;
                }

                // Discover products from listings/categories
                if (UrlUtils.looksLikeListingPath(path) &&
                    !UrlUtils.looksLikeProductPath(path)) {

                    System.out.println("Discovering product links from listing: " + u);
                    List<String> found = scraper.discoverLinksWithBrowser(u, 2500);
                    System.out.println("  Found " + found.size() + " product link(s).");
                    allLinks.addAll(found);
                }
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
                System.out.printf("(%d/%d) Scraping %s%n",
                        idx, allLinks.size(), url);
                try {
                    Product p = scraper.scrapeProduct(
                            url, idx, prefix, currencyCode, companyName);
                    scraped.add(p);
                    System.out.println("  -> OK: " + p.title + " | " + p.price);
                } catch (Exception e) {
                    System.err.println("  -> Error: " + e.getMessage());
                }
            }

            long elapsed = (System.currentTimeMillis() - start) / 1000;
            System.out.printf(
                    "Done. Scraped %d product(s) in %d seconds.%n",
                    scraped.size(), elapsed
            );

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        // -----------------------------
        // Export Excel
        // -----------------------------
        try {
            ExcelExporter exporter = new ExcelExporter();
            exporter.export(scraped, outputFile, false);
            System.out.println("Feed written to: " + outputFile +
                    " (rows: " + scraped.size() + ")");
        } catch (Exception e) {
            System.err.println("ERROR writing Excel: " + e.getMessage());
            System.exit(1);
        }
    }

    // -----------------------------
    // Helpers
    // -----------------------------

    private static String prompt(Scanner sc, String msg) {
        String val;
        do {
            System.out.print(msg + "\n> ");
            val = sc.nextLine().trim();
        } while (val.isEmpty());
        return val;
    }

    private static List<String> readUrlList(String path) {
        List<String> urls = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    urls.add(line);
                }
            }
        } catch (Exception e) {
            System.err.println("ERROR reading URL list: " + e.getMessage());
        }
        return urls;
    }
}
