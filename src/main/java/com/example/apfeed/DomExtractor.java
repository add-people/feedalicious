package com.example.apfeed;

import com.microsoft.playwright.Page;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts product data from either:
 *  - A rendered Playwright Page (JS fully executed), or
 *  - Raw HTML (for fallback / non-JS sites).
 *
 * No extra libs beyond Playwright + Jsoup + core Java.
 */
public class DomExtractor {

    private static final Pattern BAD_IMG_TERMS = Pattern.compile(
            "(logo|placeholder|sprite|icon|avatar|brand)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BAD_DESC = Pattern.compile(
            "(cookies?|(accept|close).{0,15}cookies?)",
            Pattern.CASE_INSENSITIVE
    );

    // 123,456.78  or  123.456,78  or  1234.56 etc.
    private static final Pattern DECIMAL_RE = Pattern.compile(
            "([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{2})|[0-9]{2,})"
    );

    /** Result passed back to ProductScraper. */
    public static class ExtractResult {
        public String title;
        public String price;
        public String imageUrl;
        public String description;
        public List<String> breadcrumbs;
    }

    // ----------------------------------------------------
    // Public entry points
    // ----------------------------------------------------

    /**
     * Main path: use Playwright's rendered DOM.
     */
    public static ExtractResult extractFromDom(Page page, String url, String currencyCode) {
        String html = page.content();
        String bodyText = null;
        try {
            bodyText = (String) page.evaluate("() => document.body.innerText");
        } catch (Exception ignored) {}

        return extractFromHtmlInternal(html, bodyText, url, currencyCode);
    }

    /**
     * Fallback: work from plain HTML only (e.g. when we fetch via Jsoup.connect()).
     */
    public static ExtractResult extractFromHtml(String html, String url, String currencyCode) {
        return extractFromHtmlInternal(html, null, url, currencyCode);
    }

    /**
     * Public helper for SKU: can be called from anywhere.
     */
    public static String extractSkuFromHtml(String html, String url) {
        if (html == null) return "";
        Document doc = Jsoup.parse(html, url);
        return extractSkuFromDocument(doc);
    }

    // ----------------------------------------------------
    // Core extractor (HTML + optional body text)
    // ----------------------------------------------------

    private static ExtractResult extractFromHtmlInternal(String html,
                                                         String bodyText,
                                                         String url,
                                                         String currencyCode) {
        if (html == null) html = "";
        Document doc = Jsoup.parse(html, url);

        ExtractResult result = new ExtractResult();

        // ----- TITLE -----
        String title = "";
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) {
            title = cleanText(h1.text());
        }
        if (title.isEmpty()) {
            String dt = doc.title();
            if (dt != null) title = cleanText(dt);
        }
        if (title.isEmpty()) {
            title = "";
        }

        // ----- PRICE -----
        String textForPrice;
        if (bodyText != null && !bodyText.isBlank()) {
            textForPrice = bodyText;
        } else {
            textForPrice = doc.text();
        }
        String price = extractPriceFromText(textForPrice, currencyCode);

        // ----- DESCRIPTION (improved heuristic) -----
        String description = "";
        List<String> paras = new ArrayList<>();

        // Prefer product-ish containers first, then general content
        String[] sels = {
                "[class*=product-description] p",
                ".product__description p",
                ".product-single__description p",
                "[data-product-description] p",
                "[class*=product] p",
                "[class*=description] p",
                "main p",
                "article p",
                "section p",
                ".prose p"
        };

        for (String sel : sels) {
            Elements ps = doc.select(sel);
            for (Element p : ps) {
                String t = cleanText(p.text());
                if (t.length() < 40) continue;              // too short
                if (t.length() > 600) continue;             // probably long blog/help text
                if (BAD_DESC.matcher(t).find()) continue;   // cookie banners etc.
                paras.add(t);
            }
            if (!paras.isEmpty()) break; // we found some in a good container
        }

        // If we still have nothing, fall back to "longest reasonable" paragraph anywhere
        if (paras.isEmpty()) {
            Elements ps = doc.select("p");
            for (Element p : ps) {
                String t = cleanText(p.text());
                if (t.length() >= 40 && t.length() <= 600 && !BAD_DESC.matcher(t).find()) {
                    paras.add(t);
                }
            }
        }

        if (!paras.isEmpty()) {
            // Pick the one closest to ~250 chars (nice concise description)
            description = paras.stream()
                    .min(Comparator.comparingInt(s -> Math.abs(s.length() - 250)))
                    .orElse("");
        }

        if (description.isEmpty()) {
            Element p = doc.selectFirst("p");
            if (p != null) {
                description = cleanText(p.text());
            }
        }

        // Strip naked URLs from the description
        description = description.replaceAll("https?://\\S+", "").replaceAll("\\s+", " ").trim();

        // Final clamp
        if (description.length() > 500) {
            description = description.substring(0, 500);
        }
        if (description.isEmpty()) {
            description = title != null ? title : "";
        }

        // ----- BREADCRUMBS -----
        List<String> crumbs = collectBreadcrumbs(doc);

        // ----- IMAGE -----
        String image = pickBestImage(url, doc, title);

        result.title = title;
        result.price = price;
        result.imageUrl = image;
        result.description = description;
        result.breadcrumbs = crumbs;

        return result;
    }

    // ----------------------------------------------------
    // SKU extraction
    // ----------------------------------------------------

    private static String extractSkuFromDocument(Document doc) {
        // 1) Microdata / meta (itemprop="sku")
        Element skuEl = doc.selectFirst("[itemprop=sku], meta[itemprop=sku]");
        if (skuEl != null) {
            String val = skuEl.hasAttr("content") ? skuEl.attr("content") : skuEl.text();
            val = cleanText(val);
            if (!val.isEmpty()) return val;
        }

        // 2) Common data-* attributes
        Element ds = doc.selectFirst("[data-product-sku], [data-sku]");
        if (ds != null) {
            String val = ds.hasAttr("data-product-sku")
                    ? ds.attr("data-product-sku")
                    : ds.attr("data-sku");
            val = cleanText(val);
            if (!val.isEmpty()) return val;
        }

        // 3) Common class names (sku, product-sku, etc.)
        Elements skuCandidates = doc.select(".product-sku, .sku, [class*=sku]");
        for (Element el : skuCandidates) {
            String text = cleanText(el.text());
            if (text.toLowerCase(Locale.ROOT).startsWith("sku")) {
                text = text.replaceFirst("(?i)^sku[:#\\s]*", "");
            }
            if (text.length() >= 2 && text.length() <= 64) {
                return text;
            }
        }

        // 4) Raw text “SKU: XXX” pattern in body
        String body = doc.text();
        Matcher m = Pattern.compile("(?i)SKU[:#\\s]+([A-Z0-9\\-_/]+)").matcher(body);
        if (m.find()) {
            String val = m.group(1);
            val = cleanText(val);
            if (!val.isEmpty()) return val;
        }

        return "";
    }

    // ----------------------------------------------------
    // Price extraction from plain text
    // ----------------------------------------------------

    private static String extractPriceFromText(String text, String currencyCode) {
        if (text == null) text = "";
        List<Double> candidates = new ArrayList<>();

        String symbol = getCurrencySymbol(currencyCode);
        String basePattern = DECIMAL_RE.pattern();
        String priceRegex = !symbol.isEmpty()
                ? Pattern.quote(symbol) + "\\s*" + basePattern
                : basePattern;
        Pattern priceRe = Pattern.compile(priceRegex);

        try {
            Matcher m = priceRe.matcher(text);
            while (m.find()) {
                String val = normalizeNumberToken(m.group(1));
                try {
                    candidates.add(Double.parseDouble(val));
                } catch (Exception ignored) {}
            }

            if (!candidates.isEmpty()) {
                double best = candidates.stream()
                        .filter(v -> v > 0)
                        .mapToDouble(v -> v)
                        .max()
                        .orElse(0.0);
                if (best > 0) {
                    return String.format(Locale.US, "%.2f %s", best, currencyCode);
                }
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    private static String getCurrencySymbol(String currencyCode) {
        if (currencyCode == null) return "";
        String c = currencyCode.toUpperCase(Locale.ROOT);
        return switch (c) {
            case "GBP" -> "£";
            case "USD" -> "$";
            case "EUR" -> "€";
            case "AUD", "CAD", "NZD" -> "$";
            default -> "";
        };
    }

    private static String normalizeNumberToken(String s) {
        if (s == null) return "";
        s = s.trim();
        // If both . and , exist, assume the last separator is the decimal mark
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        if (lastDot >= 0 && lastComma >= 0) {
            int last = Math.max(lastDot, lastComma);
            String intPart = s.substring(0, last).replaceAll("[^0-9]", "");
            String fracPart = s.substring(last + 1).replaceAll("[^0-9]", "");
            if (fracPart.length() == 0) {
                return intPart;
            }
            return intPart + "." + fracPart;
        }
        // Otherwise, just strip all non-digits except dot/comma, then normalise
        s = s.replaceAll("[^0-9.,]", "");
        if (s.chars().filter(ch -> ch == ',').count() > 0 &&
            s.chars().filter(ch -> ch == '.').count() == 0) {
            // European style: 123,45
            s = s.replace(',', '.');
        } else {
            // 1,234.56 -> 1234.56
            s = s.replace(",", "");
        }
        return s;
    }

    // ----------------------------------------------------
    // Breadcrumbs
    // ----------------------------------------------------

    private static List<String> collectBreadcrumbs(Document doc) {
        List<String> crumbs = new ArrayList<>();

        // aria-label based breadcrumbs
        Elements navs = doc.select("nav[aria-label*=crumb], nav[aria-label*=Crumb], nav.breadcrumb");
        for (Element nav : navs) {
            Elements links = nav.select("a, span");
            for (Element e : links) {
                String t = cleanText(e.text());
                if (!t.isEmpty() && t.length() > 2 && !crumbs.contains(t)) {
                    crumbs.add(t);
                }
            }
        }

        // common classes
        if (crumbs.isEmpty()) {
            Elements bcs = doc.select(".breadcrumb, .breadcrumbs, [class*=breadcrumb]");
            for (Element bc : bcs) {
                Elements links = bc.select("a, span, li");
                for (Element e : links) {
                    String t = cleanText(e.text());
                    if (!t.isEmpty() && t.length() > 2 && !crumbs.contains(t)) {
                        crumbs.add(t);
                    }
                }
            }
        }

        return crumbs;
    }

    // ----------------------------------------------------
    // Image picking
    // ----------------------------------------------------

    public static String pickBestImage(String url, Document doc, String titleText) {
        List<String> imgUrls = new ArrayList<>();

        String[] sels = {
                "main img", "article img",
                "[class*=product] img",
                "[class*=gallery] img",
                "[class*=image] img"
        };

        for (String sel : sels) {
            Elements imgs = doc.select(sel);
            for (Element img : imgs) {
                String src = img.hasAttr("srcset")
                        ? img.attr("srcset").split("\\s+")[0]
                        : img.attr("src");
                if (src == null || src.isBlank()) continue;
                src = absolutizeUrl(url, src.trim());
                if (src.isEmpty()) continue;
                if (BAD_IMG_TERMS.matcher(src).find()) continue;
                if (!imgUrls.contains(src)) {
                    imgUrls.add(src);
                }
            }
        }

        if (imgUrls.isEmpty()) {
            Elements imgs = doc.select("img");
            for (Element img : imgs) {
                String src = img.hasAttr("srcset")
                        ? img.attr("srcset").split("\\s+")[0]
                        : img.attr("src");
                if (src == null || src.isBlank()) continue;
                src = absolutizeUrl(url, src.trim());
                if (src.isEmpty()) continue;
                if (BAD_IMG_TERMS.matcher(src).find()) continue;
                if (!imgUrls.contains(src)) {
                    imgUrls.add(src);
                }
            }
        }

        if (imgUrls.isEmpty()) return "";

        // Prefer images whose URL contains words from the title
        if (titleText != null && !titleText.isBlank()) {
            String[] words = titleText.toLowerCase(Locale.ROOT).split("\\W+");
            Set<String> good = new HashSet<>();
            for (String w : words) {
                if (w.length() < 4) continue;
                good.add(w);
            }
            String best = null;
            int bestScore = -1;
            for (String u : imgUrls) {
                String lu = u.toLowerCase(Locale.ROOT);
                int score = 0;
                for (String w : good) {
                    if (lu.contains(w)) score++;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = u;
                }
            }
            if (best != null) return best;
        }

        // Fallback: just return the first URL
        return imgUrls.get(0);
    }

    // ----------------------------------------------------
    // Utils
    // ----------------------------------------------------

    private static String cleanText(String t) {
        if (t == null) return "";
        return t.replaceAll("\\s+", " ").trim();
    }

    private static String absolutizeUrl(String pageUrl, String candidate) {
        if (candidate == null || candidate.isBlank()) return "";
        try {
            URI base = new URI(pageUrl);
            URI abs = base.resolve(candidate);
            return abs.toString();
        } catch (Exception e) {
            return candidate;
        }
    }
}
