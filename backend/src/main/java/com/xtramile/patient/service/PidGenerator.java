package com.xtramile.patient.service;

/** Allocates the next business Patient Identity. */
public interface PidGenerator {

    /**
     * @return a fresh, never previously issued PID such as {@code PAT-000042}
     */
    String next();
}
