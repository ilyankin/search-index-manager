package com.izenkyt.searchindexmanager.indexload;

public class LoadInProgressException extends RuntimeException {

    public LoadInProgressException(String message) {
        super(message);
    }
}
