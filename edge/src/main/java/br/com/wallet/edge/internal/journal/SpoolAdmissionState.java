package br.com.wallet.edge.internal.journal;

/**
 * Spool journal admission state reflecting disk capacity watermarks.
 * Specified in PLAN-000.9 Section 5.
 */
public enum SpoolAdmissionState {
    NORMAL,
    WARNING,
    PRESSURE,
    SATURATED
}
