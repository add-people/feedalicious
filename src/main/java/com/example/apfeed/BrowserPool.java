package com.example.apfeed;

import com.microsoft.playwright.*;

public class BrowserPool implements AutoCloseable {
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;

    public synchronized void start() {
        if (browser != null) return;
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
        );
        context = browser.newContext(
                new Browser.NewContextOptions()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                      "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                      "Chrome/124.0.0.0 Safari/537.36")
                        .setIgnoreHTTPSErrors(true)
        );
    }

    public synchronized Page newPage() {
        if (browser == null) start();
        return context.newPage();
    }

    @Override
    public synchronized void close() {
        try {
            if (context != null) context.close();
        } catch (Exception ignored) {}
        try {
            if (browser != null) browser.close();
        } catch (Exception ignored) {}
        try {
            if (playwright != null) playwright.close();
        } catch (Exception ignored) {}
    }
}

