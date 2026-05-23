package com.citydisruptors.incidentservice.exception;

public class IncidentNotFoundException extends RuntimeException {
    public IncidentNotFoundException(String message) {
        super(message);
    }
}
