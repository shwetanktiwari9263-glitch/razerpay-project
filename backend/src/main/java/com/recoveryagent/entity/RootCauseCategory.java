package com.recoveryagent.entity;

public enum RootCauseCategory {
    // Customer-related issues
    INSUFFICIENT_FUNDS,
    INVALID_CARD,
    CARD_EXPIRED,
    AUTHENTICATION_FAILURE,
    CUSTOMER_ISSUE,
    REPEATED_FAILURE,
    // Payment method specific
    UPI_FAILURE,
    // Bank issues
    BANK_ISSUE,
    // Infrastructure issues
    GATEWAY_ISSUE,
    NETWORK_ISSUE,
    TEMPORARY_NETWORK,
    TIMEOUT,
    RATE_LIMIT,
    // Unknown/Other
    UNKNOWN
}
