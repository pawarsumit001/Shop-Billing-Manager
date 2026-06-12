package com.shopbilling.config;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@ControllerAdvice
public class ClientAbortExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ClientAbortExceptionHandler.class);

    @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
    public void handleClientAbort(Exception ex) {
        log.debug("Client disconnected before the response finished: {}", ex.getMessage());
    }
}
