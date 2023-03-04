package com.walmartlabs.concord.plugins.smb.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc., Concord Authors
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */


import org.immutables.value.Value;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface TaskParams {

    Path workDir();
    Action action();
    Auth auth();
    String serverBaseUrl();

    @Value.Default
    default boolean ignoreErrors() {
        return false;
    }

    interface CopyParams extends TaskParams {
        List<String> src();
        String dst();

        @Value.Default
        default boolean overwrite() {
            return false;
        }
    }

    interface ListParams extends TaskParams {
        String path();
    }

    enum Action {
        COPYTO,
        COPYFROM,
        LIST,
        LISTRECURSIVE
    }
}
