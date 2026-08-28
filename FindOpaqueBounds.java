import javax.imageio.ImageIO;
import java.io.InputStream;
import java.awt.image.BufferedImage;

public class FindOpaqueBounds {
    public static void main(String[] args) throws Exception {
        String[] paths = {"/menu_btn_entrar.png", "/menu_input.png"};
        for (String p : paths) {
            InputStream s = FindOpaqueBounds.class.getResourceAsStream(p);
            if (s == null) {
                System.out.println(p + ": NOT FOUND");
                continue;
            }
            BufferedImage img = ImageIO.read(s);
            int w = img.getWidth(), h = img.getHeight();
            
            int minX = w, minY = h, maxX = 0, maxY = 0;
            boolean found = false;
            
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int alpha = (img.getRGB(x, y) >> 24) & 0xFF;
                    if (alpha > 50) {
                        found = true;
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            
            if (found) {
                System.out.println(p + ": bounds=(" + minX + "," + minY + ") to (" + maxX + "," + maxY + ") size=" + (maxX-minX+1) + "x" + (maxY-minY+1));
            } else {
                System.out.println(p + ": NO opaque pixels found!");
            }
        }
    }
}
