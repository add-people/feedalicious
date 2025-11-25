package com.example.apfeed;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

public class ProductScraper {
    private static final int MAX_DISCOVERED_PER_LISTING = 2500;
    private final BrowserPool pool;

    public ProductScraper(BrowserPool pool) {
        this.pool = pool;
    }

    private void dismissCookies(Page page) {
        String[] names = {"accept", "agree", "ok", "got it"};
        for (String n : names) {
            try {
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(Pattern.compile(n, Pattern.CASE_INSENSITIVE))
                ).click();
            } catch (Exception ignored) {}
        }
    }

    public List<String> discoverLinksWithBrowser(String startUrl, int maxLinks) {
        Page page = pool.newPage();
        List<String> links = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            // Simple navigate with timeout, no LoadState
            page.navigate(startUrl, new Page.NavigateOptions().setTimeout(45000));

            // small pause to let JS render
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}

            dismissCookies(page);

            // Scroll & load more-ish
            int lastLen = -1;
            for (int i = 0; i < 24; i++) {
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                try {
                    Thread.sleep(900);
                } catch (InterruptedException ignored) {}

                boolean clicked = clickLoadMoreOnce(page);
                Locator cards = page.locator("a[href*='/product/']");
                int curLen = cards.count();
                if (!clicked && curLen == lastLen) break;
                lastLen = curLen;
            }

            // Collect anchors
            Locator anchors = page.locator("a[href]");
            int count = Math.min(anchors.count(), 7000);
            String base = page.url();
            for (int i = 0; i < count; i++) {
                String href;
                try {
                    href = anchors.nth(i).getAttribute("href");
                } catch (Exception e) {
                    continue;
                }
                if (href == null || href.isEmpty() || href.startsWith("#")) continue;
                String abs = UrlUtils.normalizeUrl(base, href);
                if (!UrlUtils.isSameSite(abs, startUrl)) continue;
                String path = URI.create(abs).getPath();
                if (!UrlUtils.looksLikeProductPath(path)) continue;

                if (!seen.contains(abs)) {
                    seen.add(abs);
                    links.add(abs);
                    if (links.size() >= maxLinks) break;
                }
            }

            // Fallback: regex in HTML
            if (links.isEmpty()) {
                String html = page.content();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\"']+").matcher(html);
                while (m.find() && links.size() < maxLinks) {
                    String u = m.group();
                    if (!UrlUtils.isSameSite(u, startUrl)) continue;
                    String path = URI.create(u).getPath();
                    if (UrlUtils.looksLikeProductPath(path)) {
                        if (!seen.contains(u)) {
                            seen.add(u);
                            links.add(u);
                        }
                    }
                }
            }
        } finally {
            page.close();
        }
        return links;
    }

    private boolean clickLoadMoreOnce(Page page) {
        String[] sels = {
                "button:has-text(\"Load more\")",
                "button:has-text(\"Load More\")",
                "button:has-text(\"Show more\")",
                "button:has-text(\"Show More\")",
                "a:has-text(\"Load more\")",
                "a:has-text(\"Show more\")",
                "button[aria-label*='load']"
        };
        for (String sel : sels) {
            Locator el = page.locator(sel);
            if (el.count() > 0 && el.first().isVisible()) {
                try {
                    el.first().click();
                    return true;
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    public Product scrapeProduct(String url, int rowId, String mpnPrefix, String currencyCode, String brandName) {
        Page page = pool.newPage();
        try {
            // Simple navigate with timeout, no LoadState
            page.navigate(url, new Page.NavigateOptions().setTimeout(35000));

            // Small pause so JS can render price, images etc.
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {}

            dismissCookies(page);

            DomExtractor.ExtractResult er = DomExtractor.extractFromDom(page, url, currencyCode);
            String productType = ProductTypeDetector.detect(er.title, er.description, er.breadcrumbs);

            Product p = new Product();
            p.id = rowId;
            p.title = er.title;
            p.description = er.description;
            p.link = url;
            p.condition = "new";
            p.price = er.price;
            p.availability = "in stock";
            p.adult = "No";
            p.imageLink = er.imageUrl;
            p.mpn = mpnPrefix + rowId;
            p.brand = brandName;
            p.productTypes = productType;
            return p;
        } finally {
            page.close();
        }
    }
}
