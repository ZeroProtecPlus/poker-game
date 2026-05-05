package view;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import javax.swing.JPanel;

public class GameViewCasinoDialogTest {

    public static void main(String[] args) throws Exception {
        bgImageMenuShouldBeStatic();
        casinoDialogShouldNotPaintGreenFillWhenBgImageMenuIsPresent();
        casinoDialogShouldPaintGreenFillWhenBgImageMenuIsNull();
        casinoDialogShouldAlwaysPaintGoldBorder();
        System.out.println("GameViewCasinoDialogTest: all tests passed");
    }

    // -------------------------------------------------------------------------
    // Task 1.1 — bgImageMenu must be static so CasinoDialog can reference it
    // -------------------------------------------------------------------------
    private static void bgImageMenuShouldBeStatic() throws Exception {
        Field field = GameView.TablePanel.class.getDeclaredField("bgImageMenu");
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new AssertionError("bgImageMenu should be static");
        }
    }

    // -------------------------------------------------------------------------
    // Task 1.2 — green fill is conditional on bgImageMenu == null
    // -------------------------------------------------------------------------
    private static void casinoDialogShouldNotPaintGreenFillWhenBgImageMenuIsPresent()
            throws Exception {
        setBgImageMenu(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        try {
            int centerPixel = paintCasinoDialogAndProbeCenter();
            // Transparent means fillRoundRect was NOT executed
            if (centerPixel != 0x00000000) {
                throw new AssertionError(
                    "Expected no green fill when bgImageMenu is present, got: "
                        + Integer.toHexString(centerPixel)
                );
            }
        } finally {
            setBgImageMenu(null);
        }
    }

    private static void casinoDialogShouldPaintGreenFillWhenBgImageMenuIsNull()
            throws Exception {
        setBgImageMenu(null);
        int centerPixel = paintCasinoDialogAndProbeCenter();
        // Opaque dark green means fillRoundRect WAS executed
        if (centerPixel != 0xFF0D2B1A) {
            throw new AssertionError(
                "Expected green fill when bgImageMenu is null, got: "
                    + Integer.toHexString(centerPixel)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Task 1.3 — gold border must always be rendered
    // -------------------------------------------------------------------------
    private static void casinoDialogShouldAlwaysPaintGoldBorder() throws Exception {
        setBgImageMenu(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        try {
            BufferedImage img = paintCasinoDialogToImage();
            // Probe a pixel on the top edge (center of the 2f gold stroke)
            int borderPixel = img.getRGB(50, 1);
            // GOLD = 0xC9A84C → center of 2f stroke should be fully opaque
            if ((borderPixel & 0xFF000000) != 0xFF000000) {
                throw new AssertionError(
                    "Expected gold border to be opaque, got alpha: "
                        + Integer.toHexString(borderPixel)
                );
            }
            if ((borderPixel & 0x00FFFFFF) != 0x00C9A84C) {
                throw new AssertionError(
                    "Expected gold border color 0xC9A84C, got: "
                        + Integer.toHexString(borderPixel & 0x00FFFFFF)
                );
            }
        } finally {
            setBgImageMenu(null);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private static void setBgImageMenu(BufferedImage image) throws Exception {
        Field field = GameView.TablePanel.class.getDeclaredField("bgImageMenu");
        field.setAccessible(true);
        field.set(null, image);
    }

    private static int paintCasinoDialogAndProbeCenter() throws Exception {
        BufferedImage img = paintCasinoDialogToImage();
        return img.getRGB(50, 50);
    }

    private static BufferedImage paintCasinoDialogToImage() throws Exception {
        GameView.CasinoDialog dialog =
            new GameView.CasinoDialog(null, "Test", "Prompt", null);
        JPanel root = (JPanel) dialog.getContentPane();
        root.setSize(100, 100);

        BufferedImage img =
            new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        Method paintComponent =
            root.getClass().getDeclaredMethod("paintComponent", java.awt.Graphics.class);
        paintComponent.setAccessible(true);
        paintComponent.invoke(root, g2);
        g2.dispose();

        return img;
    }
}
