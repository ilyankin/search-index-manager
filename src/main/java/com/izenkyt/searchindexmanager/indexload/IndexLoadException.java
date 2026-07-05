package com.izenkyt.searchindexmanager.indexload;

public class IndexLoadException extends RuntimeException {

    public IndexLoadException(String message) {
        super(message);
    }

    public IndexLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
