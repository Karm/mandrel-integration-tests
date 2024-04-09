package quarkus.pdfbox;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class PDFBoxResourceTest {

    @Test
    public void testWatermarkJPG() throws IOException {
        final byte[] imgBytes = given()
                .multiPart("pdf", new File(PDFBoxResourceTest.class.getResource("/test-pdf.pdf").getFile()))
                .when().post("/pdf2png").asByteArray();
        final BufferedImage image = ImageIO.read(new ByteArrayInputStream(imgBytes));
        assertNotNull(image, "The image returned is not a valid, known format, e.g. PNG");
        assertTrue(image.getWidth() == 595 && image.getHeight() == 841,
                String.format("Image's expected dimension is %d x %d, but was %d x %d.",
                        595, 841, image.getWidth(), image.getHeight()));
        final int[] pixel = new int[4]; //4BYTE RGBA
        image.getData().getPixel(297, 74, pixel);
        assertTrue(pixel[0] > 200, "There should have been more red. The image is probably not correct.");
        image.getData().getPixel(206, 23, pixel);
        assertTrue(pixel[2] > 200, "There should have been more blue. The image is probably not correct.");
    }
}
