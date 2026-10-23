package com.impactgraph.graph.exception;

/**
 * Thrown when malformed inputs or structural violations are encountered in graph construction.
 */
public class GraphValidationException extends GraphException {

    public GraphValidationException(String message) {
        super(message);
    }
}
