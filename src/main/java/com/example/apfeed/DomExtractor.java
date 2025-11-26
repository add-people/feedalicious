package com.example.apfeed;

import com.microsoft.playwright.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts product data from a rendered Playwright Page.
 *
 * No extra libraries (only Playwright + Jsoup + core Java).
 * Strategy:
 *  1) Title from <h1> or <title>.
 *  2) Price from rendered text (using currencyCode) and common price containers.
 *  3) Description from main/article/product/description paragraphs.
 *  4) Image from main/product/gallery/img tags, avoiding logos.
 *  5) Breadcrumbs from common breadcrumb containers.
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

    // ---------- Public entry point ----------

    public static ExtractResult extractFromDom(Page page, String url, String currencyCode) {
        String html = page.content();
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

        // ----- PRICE -----
        String price = extractPriceRendered(page, doc, currencyCode);

        // ----- DESCRIPTION -----
        String description = "";
        List<String> paras = new ArrayList<>();
        String[] sels = {
                "main p",
                "article p",
                "[class*=product] p",
                "[class*=description] p",
                "section p",
                ".prose p"
        };
        for (String sel : sels) {
            Elements ps = doc.select(sel);
            for (Element p : ps) {
                String t = cleanText(p.text());
                if (t.length() >= 40 && !BAD_DESC.matcher(t).find()) {
                    paras.add(t);
                }
            }
        }
        if (!paras.isEmpty()) {
            description = paras.stream()
                    .max(Comparator.comparingInt(String::length))
                    .orElse("");
        }
        if (description.isEmpty()) {
            Element p = doc.selectFirst("p");
            if (p != null) {
                description = cleanText(p.text());
            }
        }
        if (description.length() > 900) {
            description = description.substring(0, 900);
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

    // ---------- Price extraction (no extra deps) ----------

    public static String extractPriceRendered(Page page, Document doc, String currencyCode) {
        List<Double> candidates = new ArrayList<>();

        String symbol = getCurrencySymbol(currencyCode);
        String basePattern = DECIMAL_RE.pattern();
        String priceRegex = !symbol.isEmpty()
                ? Pattern.quote(symbol) + "\\s*" + basePattern
                : basePattern;
        Pattern priceRe = Pattern.compile(priceRegex);

        try {
            // 1) Elements that visibly contain the currency symbol
            if (!symbol.isEmpty()) {
                Locator texts = page.locator("xpath=//*[contains(text(),'" + symbol + "')]");
                int count = Math.min(texts.count(), 250);
                for (int i = 0; i < count; i++) {
                    String t;
                    try {
                        t = texts.nth(i).innerText();
                    } catch (Exception e) {
                        continue;
                    }
                    Matcher m = priceRe.matcher(t);
                    while (m.find()) {
                        String val = normalizeNumberToken(m.group(1));
                        try {
                            candidates.add(Double.parseDouble(val));
                        } catch (Exception ignored) {}
                    }
                }
            }

            // 2) Fallback: entire body text
            if (candidates.isEmpty()) {
                String bodyText;
                try {
                    bodyText = (String) page.evaluate("() => document.body.innerText");
                } catch (Exception e) {
                    bodyText = doc.text();
                }
                Matcher m = priceRe.matcher(bodyText);
                while (m.find()) {
                    String val = normalizeNumberToken(m.group(1));
                    try {
                        candidates.add(Double.parseDouble(val));
                    } catch (Exception ignored) {}
                }
            }

            // 3) If still empty, try common "price-like" containers via CSS selectors
            if (candidates.isEmpty()) {
                String[] sels = {
                        "[itemprop='price']",
                        "[data-price]", "[data-price-amount]", "[data-price-value]",
                        ".productPrice", ".product__price", ".product-price",
                        ".summary .price", ".product .price", ".price"
                };
                for (String sel : sels) {
                    Locator loc = page.locator(sel).first();
                    String txt;
                    try {
                        txt = loc.textContent();
                    } catch (Exception e) {
                        continue;
                    }
                    if (txt == null || txt.isBlank()) continue;
                    Matcher m = priceRe.matcher(txt);
                    while (m.find()) {
                        String val = normalizeNumberToken(m.group(1));
                        try {
                            candidates.add(Double.parseDouble(val));
                        } catch (Exception ignored) {}
                    }
                    if (!candidates.isEmpty()) break;
                }
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

    // ---------- Breadcrumbs ----------

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

    // ---------- Image picking ----------

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

    // ---------- Utils ----------

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
