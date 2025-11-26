package quarkus.pdfbox;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.RestForm;

import javax.imageio.ImageIO;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.File;

@Path("/pdf2png")
public class PDFBoxResource {

    public static class FormData {
        @RestForm("pdf")
        public File pdf;
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response uploadFile(@MultipartForm FormData data) throws Exception {
        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                final PDDocument doc = Loader.loadPDF(data.pdf)) {
            final PDFRenderer renderer = new PDFRenderer(doc);
            ImageIO.write(renderer.renderImage(0), "PNG", bos);
            return Response.accepted().type(MediaType.APPLICATION_OCTET_STREAM_TYPE)
                    .entity(bos.toByteArray()).build();
        }
    }
}
