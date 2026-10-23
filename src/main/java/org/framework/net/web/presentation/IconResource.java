package org.framework.net.web.presentation;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;

@Path("/icone.png")
public class IconResource {

    @GET
    @Produces("image/png")
    public Response icon() {
        InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/resources/icone.png");
        if (stream == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(stream).build();
    }
}
