package com.izenkyt.searchindexmanager.event;

public class EventPublishAmbiguousException extends RuntimeException {

    public EventPublishAmbiguousException(String message, Throwable cause) {
        super(message, cause);
    }
}
