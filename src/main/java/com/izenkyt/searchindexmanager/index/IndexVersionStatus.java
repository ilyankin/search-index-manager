package com.izenkyt.searchindexmanager.index;

public enum IndexVersionStatus {
    CREATED,
    BUILDING,
    BUILT,
    UPLOADED,
    READY,
    FAILED;

    public boolean hasArtifact() {
        return this == UPLOADED || this == READY;
    }
}