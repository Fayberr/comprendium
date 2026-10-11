package dev.fayber.comprendium.client;

/**
 * Guide IDs are stable UI destinations, not trade-data modes. Additional guides
 * will get their own canvas implementations without replacing the atlas.
 */
public enum GuideSection {
    TRADING("Trading"),
    NEXT_GUIDE("Next guide +");

    private final String title;

    GuideSection(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
