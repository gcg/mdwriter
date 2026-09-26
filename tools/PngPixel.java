import java.io.File;
import javax.imageio.ImageIO;

/** Usage: java -Djava.awt.headless=true tools/PngPixel.java shot.png x y [x y ...] -> "WxH", then "x,y #RRGGBB". */
public class PngPixel {
    public static void main(String[] a) throws Exception {
        var img = ImageIO.read(new File(a[0]));
        System.out.println(img.getWidth() + "x" + img.getHeight());
        for (int i = 1; i + 1 < a.length; i += 2) {
            int x = Integer.parseInt(a[i]), y = Integer.parseInt(a[i + 1]);
            System.out.printf("%d,%d #%06X%n", x, y, img.getRGB(x, y) & 0xFFFFFF);
        }
    }
}
