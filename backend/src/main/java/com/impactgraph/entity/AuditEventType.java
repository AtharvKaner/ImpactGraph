package com.impactgraph.entity;

/**
 * Classification of audit events recorded by the system.
 */
public enum AuditEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    USER_REGISTERED,
    ROLE_CHANGED,
    PROJECT_CREATED,
    PROJECT_UPDATED,
    PROJECT_DELETED,
    MEMBERSHIP_GRANTED,
    MEMBERSHIP_REVOKED,
    DEPENDENCY_CREATED,
    DEPENDENCY_UPDATED,
    DEPENDENCY_DELETED,
    ACCESS_DENIED
}
