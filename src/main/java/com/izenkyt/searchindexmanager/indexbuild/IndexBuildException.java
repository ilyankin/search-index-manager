package com.izenkyt.searchindexmanager.indexbuild;

public class IndexBuildException extends RuntimeException {

    public IndexBuildException(String message) {
        super(message);
    }

    public IndexBuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
