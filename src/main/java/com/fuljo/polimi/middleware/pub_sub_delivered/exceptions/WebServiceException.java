package com.fuljo.polimi.middleware.pub_sub_delivered.exceptions;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
public class WebServiceException extends WebApplicationException {

    private static final long serialVersionUID = 1L;

    public WebServiceException(String message, Throwable cause, int status) {
        super(message, cause, Response.status(status).entity(message).build());
    }

    public WebServiceException(String message, Throwable cause, Response.Status status) throws IllegalArgumentException {
        super(message, cause, Response.status(status).entity(message).build());
    }

    public WebServiceException() {
        super();
    }

    public WebServiceException(String message) {
        super(message);
    }

    public WebServiceException(Response response) {
        super(response);
    }

    public WebServiceException(String message, Response response) {
        super(message, response);
    }

    public WebServiceException(int status) {
        super(status);
    }

    public WebServiceException(String message, int status) {
        this((Throwable) null, status);
    }

    public WebServiceException(Response.Status status) {
        super(status);
    }

    public WebServiceException(String message, Response.Status status) {
        this(message, null, status);
    }

    public WebServiceException(Throwable cause) {
        super(cause);
    }

    public WebServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public WebServiceException(Throwable cause, Response response) {
        super(cause, response);
    }

    public WebServiceException(String message, Throwable cause, Response response) {
        super(message, cause, response);
    }

    public WebServiceException(Throwable cause, int status) {
        super(cause, status);
    }

    public WebServiceException(Throwable cause, Response.Status status) throws IllegalArgumentException {
        super(cause, status);
    }
}
