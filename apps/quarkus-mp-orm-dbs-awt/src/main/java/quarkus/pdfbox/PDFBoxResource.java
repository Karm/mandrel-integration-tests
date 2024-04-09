package quarkus.pdfbox;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.RestForm;

import javax.imageio.ImageIO;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
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
