package com.impactgraph.controller.exception;

import com.impactgraph.controller.GraphController;
import com.impactgraph.dto.ErrorResponseDto;
import com.impactgraph.exception.ResourceNotFoundException;
import com.impactgraph.graph.exception.GraphValidationException;
import com.impactgraph.graph.exception.InvalidServiceIdException;
import com.impactgraph.graph.exception.UnknownServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Controller advice translating graph engine and demonstration API exceptions into
 * standard HTTP error responses.
 * <p>
 * Scoped specifically to {@link GraphController} to avoid conflicting with the application's
 * global data model exception handling.
 */
@RestControllerAdvice(assignableTypes = {GraphController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GraphExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GraphExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleResourceNotFound(ResourceNotFoundException ex) {
        log.info("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidServiceIdException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidServiceId(InvalidServiceIdException ex) {
        log.warn("Invalid service ID supplied: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(GraphValidationException.class)
    public ResponseEntity<ErrorResponseDto> handleGraphValidation(GraphValidationException ex) {
        log.warn("Graph validation error: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument error: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(UnknownServiceException.class)
    public ResponseEntity<ErrorResponseDto> handleUnknownService(UnknownServiceException ex) {
        log.info("Service not found in graph: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpected(Exception ex) {
        log.error("Unhandled server exception in graph controller: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred.");
    }

    private ResponseEntity<ErrorResponseDto> buildResponse(HttpStatus status, String message) {
        ErrorResponseDto errorDto = new ErrorResponseDto(
                status.value(),
                status.getReasonPhrase(),
                message,
                Instant.now().toString()
        );
        return ResponseEntity.status(status).body(errorDto);
    }
}
