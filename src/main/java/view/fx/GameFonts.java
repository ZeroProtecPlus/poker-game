package view.fx;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Centralized font constants for the JavaFX game UI.
 *
 * Font families match the CSS variables in /fonts.css.
 * Change a family here AND in fonts.css to update the entire game.
 *
 * Loading Google Fonts: drop the .ttf in resources/fonts/,
 * call Font.loadFont(GameFonts.class.getResource("/fonts/YourFont.ttf").toExternalForm(), -1)
 * in the static initializer, then update the FAMILY_* strings below and in fonts.css.
 */
public final class GameFonts {

    private GameFonts() { /* constants only */ }

    // ── Font Families (keep in sync with fonts.css -fx-font-*) ────────
    public static final String FAMILY_HEADING = "Georgia";
    public static final String FAMILY_BODY    = "Georgia";
    public static final String FAMILY_MONO    = "Consolas";
    public static final String FAMILY_UI      = "Segoe UI";

    // ── Font Sizes (keep in sync with fonts.css -fx-size-*) ───────────
    public static final double SIZE_HERO      = 68;
    public static final double SIZE_DISPLAY    = 100;
    public static final double SIZE_TITLE      = 42;
    public static final double SIZE_HEADING    = 28;
    public static final double SIZE_BIG        = 32;
    public static final double SIZE_SUBHEAD    = 24;
    public static final double SIZE_BODY       = 18;
    public static final double SIZE_LABEL      = 16;
    public static final double SIZE_SMALL      = 14;
    public static final double SIZE_CAPTION    = 13;
    public static final double SIZE_MICRO      = 11;
    public static final double SIZE_CHIP       = 10;

    // ── Pre-built Font Instances (commonly used) ─────────────────────
    public static final Font HEADING_BOLD   = Font.font(FAMILY_HEADING, FontWeight.BOLD, SIZE_HEADING);
    public static final Font SUBHEAD_BOLD   = Font.font(FAMILY_HEADING, FontWeight.BOLD, SIZE_SUBHEAD);
    public static final Font BODY_BOLD      = Font.font(FAMILY_HEADING, FontWeight.BOLD, SIZE_BODY);
    public static final Font SMALL_BOLD     = Font.font(FAMILY_HEADING, FontWeight.BOLD, SIZE_SMALL);
    public static final Font CAPTION_ITALIC = Font.font(FAMILY_HEADING, FontWeight.NORMAL, SIZE_CAPTION);
    public static final Font CHIP_BOLD      = Font.font(FAMILY_MONO, FontWeight.BOLD, SIZE_CHIP);

    // ── Card Fonts (GameCard) ────────────────────────────────────────
    public static final Font CARD_RANK        = Font.font(FAMILY_HEADING, FontWeight.BOLD,    24);
    public static final Font CARD_SUIT_SMALL  = Font.font(FAMILY_HEADING, FontWeight.NORMAL,   SIZE_BODY);
    public static final Font CARD_SUIT_CENTER = Font.font(FAMILY_HEADING, FontWeight.NORMAL,   48);
}