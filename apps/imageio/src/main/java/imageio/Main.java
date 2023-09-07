/*
 * Copyright (c) 2021, Red Hat Inc. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package imageio;

import org.jfree.svg.SVGGraphics2D;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_BYTE_BINARY;

/**
 * This is a small toy app to produce several formats of images,
 * play a bit with color spaces...
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */

// $ rm -rf src/main/resources/META-INF/* mytest* target
// $ mvn clean package
// $ java -Djava.awt.headless=true -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image -jar target/imageio.jar
// $ jar uf target/imageio.jar -C src/main/resources/ META-INF
// $ native-image -J-Djava.awt.headless=true --no-fallback -jar target/imageio.jar target/imageio
// $ rm -rf mytest*
// $ ./target/imageio -Djava.awt.headless=true -Djava.home=$(pwd)
public class Main {

    /**
     * Takes JPEG2000 file Grace_M._Hopper.jp2, converts colour spaces and spits out PNG files.
     *
     * @throws IOException
     */
    public static void paintGrace() throws IOException {
        // Let's paint Grace
        final BufferedImage img = ImageIO.read(Main.class.getResourceAsStream("/Grace_M._Hopper.jp2"));
        final Map<String, ColorConvertOp> conversions = Map.of(
                "toG", new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null),
                "toC", new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_CIEXYZ), null),
                "toL", new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), null),
                "toP", new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_PYCC), null),
                "toS", new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_sRGB), null));
        conversions.forEach((name, conversion) -> {
            try {
                ImageIO.write(conversion.filter(img, null), "png", new File("mytest_" + name + ".png"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Paints a groovy picture, exercising some draw methods
     *
     * @throws IOException
     * @throws FontFormatException
     */
    public static void paintRectangles() throws IOException, FontFormatException {
        loadFonts();
        final BufferedImage img = createABGRTestImage(new Color[]{
                        Color.WHITE, Color.RED, Color.GREEN, Color.BLUE, Color.BLACK},
                100, 500);

        // Handles transparency
        ImageIO.write(img, "TIFF", new File("mytest.tiff"));
        ImageIO.write(img, "GIF", new File("mytest.gif"));
        ImageIO.write(img, "PNG", new File("mytest.png"));
        Files.writeString(Path.of("mytest.svg"), toSVG(img));

        // Doesn't handle transparency
        final BufferedImage imgBGR = new BufferedImage(img.getWidth(), img.getHeight(), TYPE_3BYTE_BGR);
        imgBGR.getGraphics().drawImage(img, 0, 0, null);
        ImageIO.write(imgBGR, "JPG", new File("mytest.jpg"));
        ImageIO.write(imgBGR, "BMP", new File("mytest.bmp"));

        // Doesn't handle colours, it's monochrome
        final BufferedImage imgBINARY = new BufferedImage(img.getWidth(), img.getHeight(), TYPE_BYTE_BINARY);
        imgBINARY.getGraphics().drawImage(img, 0, 0, null);
        ImageIO.write(imgBINARY, "WBMP", new File("mytest.wbmp"));
    }

    private static void resizeImage() throws IOException {
        final BufferedImage img = ImageIO.read(Main.class.getResourceAsStream("/Grace_M._Hopper.jp2"));
        int height = 50;
        int currentW = img.getWidth();
        int currentH = img.getHeight();
        int width = currentW * height / currentH;
        if (currentH < height) {
            width = currentW;
            height = currentH;
        }
        final Image originalImage = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        final BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        resizedImage.getGraphics().drawImage(originalImage, 0, 0, null);
        ImageIO.write(resizedImage, "PNG", new File("mytest_Resized_Grace_M._Hopper.png"));
    }

    private static void loadFonts() throws IOException, FontFormatException {
        final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        // Font source: https://ftp.gnu.org/gnu/freefont/
        ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, Main.class.getResourceAsStream("/MyFreeMono.ttf")));
        ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, Main.class.getResourceAsStream("/MyFreeSerif.ttf")));
    }

    private static BufferedImage createABGRTestImage(final Color[] colors, final int dx, final int h) {
        final BufferedImage img = new BufferedImage(dx * colors.length, h, TYPE_4BYTE_ABGR);
        final Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Main rectangle
        g.setColor(Color.PINK);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        // Small rectangles rotated
        final int wCenter = img.getWidth() / 2;
        final int hCenter = img.getHeight() / 2;
        final AffineTransform originalMatrix = g.getTransform();
        final AffineTransform af = AffineTransform.getRotateInstance(Math.toRadians(5), wCenter, hCenter);
        for (int i = 0; i < colors.length; i++) {
            g.setColor(colors[i]);
            g.fillRect(i * dx, 0, dx, h);
            g.transform(af);
        }
        g.setTransform(originalMatrix);

        // Transparent circle
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g.setColor(Color.MAGENTA);
        g.fillOval(0, 0, img.getWidth(), img.getHeight());
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

        // Label, text
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        // Name of the font is not just the name of the file. It is baked in it.
        g.setFont(new Font("MyFreeMono", Font.PLAIN, 15));
        g.drawString("Mandrel", 20, 20);
        g.setFont(new Font("MyFreeSerif", Font.PLAIN, 15));
        g.drawString("Mandrel", 20, 60);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        g.drawString("Mandrel", 20, 100);

        g.dispose();
        return img;
    }

    public static String toSVG(BufferedImage img) {
        final SVGGraphics2D g2 = new SVGGraphics2D(img.getWidth(), img.getHeight());
        g2.drawImage(img, 0, 0, img.getWidth(), img.getHeight(), null);
        return g2.getSVGElement(null, true, null, null, null);
    }

    public static void main(String[] args) throws IOException, FontFormatException {
        removeFontsCache();
        paintGrace();
        paintRectangles();
        resizeImage();
    }

    // Remove the fonts related cache, so that the agent will take the right
    // code paths which will match the native run.
    //
    // See https://github.com/openjdk/jdk17u/blob/908cab4123812b6b206b966a6ce92398cdf42c08/src/java.desktop/unix/classes/sun/font/FcFontConfiguration.java#L371
    private static void removeFontsCache() {
        String userDir = System.getProperty("user.home");
        String version = System.getProperty("java.version");
        String fs = File.separator;
        String dir = userDir + fs + ".java" + fs + "fonts" + fs + version;
        Path fontsCacheDir = Paths.get(dir);
        if (Files.exists(fontsCacheDir)) { // May not exist for container builds
            try {
                Files.walkFileTree(fontsCacheDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to remove fonts config cache file", e);
            }
        }
    }
}
