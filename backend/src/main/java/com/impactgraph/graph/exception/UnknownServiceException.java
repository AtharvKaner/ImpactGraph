package com.impactgraph.graph.exception;

/**
 * Thrown when an operation references a service ID that is not registered in the graph.
 */
public class UnknownServiceException extends GraphException {

    public UnknownServiceException(String message) {
        super(message);
    }
}
