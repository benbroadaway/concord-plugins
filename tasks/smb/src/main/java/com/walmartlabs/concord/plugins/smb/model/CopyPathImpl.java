package com.walmartlabs.concord.plugins.smb.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;

public class CopyPathImpl implements CopyPath {
    private final Path src;
    private final Path dst;

    public CopyPathImpl(Path src, Path dst) {
        this.src = src;
        this.dst = dst;
    }

    @Override
    public Path src() {
        return src;
    }

    @Override
    public Path dst() {
        return dst;
    }
}
