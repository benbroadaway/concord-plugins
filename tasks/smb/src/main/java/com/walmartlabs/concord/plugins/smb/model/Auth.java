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

import javax.annotation.Nonnull;

public class Auth {
    private final String username;
    private final String password;
    private final String domain;

    public Auth(@Nonnull String username, @Nonnull String password, String domain) {
        this.username = username;
        this.password = password;
        this.domain = domain;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String domain() {
        return domain;
    }
}
