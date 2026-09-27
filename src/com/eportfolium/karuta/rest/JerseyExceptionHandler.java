package com.eportfolium.karuta.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class JerseyExceptionHandler implements ExceptionMapper<RestWebApplicationException> {
    @Override
    public Response toResponse(RestWebApplicationException ex) {
        return Response.status(ex.getStatus()).entity(ex.getCustomMessage()).type(MediaType.TEXT_PLAIN).build();
    }
}
