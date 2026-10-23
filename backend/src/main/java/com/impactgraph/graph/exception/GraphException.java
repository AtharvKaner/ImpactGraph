package com.impactgraph.graph.exception;

/**
 * Base unchecked exception for all graph engine operations.
 */
public class GraphException extends RuntimeException {

    public GraphException(String message) {
        super(message);
    }

    public GraphException(String message, Throwable cause) {
        super(message, cause);
    }
}
