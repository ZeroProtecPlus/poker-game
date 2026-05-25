package view.fx;

/**
 * Layout constants for the Start Menu (1672×941 canvas).
 *
 * <p>Hit-zone coordinates are derived from the opaque pixel bounds of
 * the full-canvas overlay PNGs in {@code src/main/resources/sprites/}.
 */
public final class StartMenuLayoutConstants {

    public static final double CANVAS_WIDTH = 1672;
    public static final double CANVAS_HEIGHT = 941;

    // Single player button — Star_Game_singleplayer.png opaque bounds
    public static final double SINGLE_X = 632;
    public static final double SINGLE_Y = 345;
    public static final double SINGLE_W = 478;
    public static final double SINGLE_H = 104;

    // Multiplayer button — Star_Game_multiplayer.png opaque bounds
    public static final double MULTI_X = 630;
    public static final double MULTI_Y = 472;
    public static final double MULTI_W = 483;
    public static final double MULTI_H = 108;

    // Settings button — Star_Game_settings.png opaque bounds
    public static final double SETTINGS_X = 632;
    public static final double SETTINGS_Y = 604;
    public static final double SETTINGS_W = 478;
    public static final double SETTINGS_H = 104;

    private StartMenuLayoutConstants() {}
}
