package com.walmartlabs.concord.plugins.smb.model;

import java.nio.file.Path;

public interface CopyPath {
    Path src();

    Path dst();
}
