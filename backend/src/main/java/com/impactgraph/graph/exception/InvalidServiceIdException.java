package com.impactgraph.graph.exception;

/**
 * Thrown when a service ID is invalid, such as null, empty, or consisting entirely of whitespace.
 */
public class InvalidServiceIdException extends GraphException {

    public InvalidServiceIdException(String message) {
        super(message);
    }
}
