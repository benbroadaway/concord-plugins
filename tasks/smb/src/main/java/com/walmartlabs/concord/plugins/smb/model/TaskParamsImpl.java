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

import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class TaskParamsImpl implements TaskParams {
    final Variables input;
    final Path workDir;


    public static final String ACTION_KEY = "action";
    private static final String IGNORE_ERRORS_KEY = "ignoreErrors";
    private static final String AUTH_KEY = "auth";
    private static final String SERVER_URL_KEY = "serverBaseUrl";

    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";
    private static final String DOMAIN_KEY = "domain";

    public static TaskParams of(Map<String, Object> input,
                                Map<String, Object> defaults,
                                Map<String, Object> policyDefaults,
                                Path workDir) {

        Map<String, Object> mergedVars = new HashMap<>(policyDefaults != null ? policyDefaults : Collections.emptyMap());
        mergedVars.putAll(defaults);
        mergedVars.putAll(input);
        MapBackedVariables vars = new MapBackedVariables(mergedVars);

        Action action = action(vars);

        switch (action) {
            case COPYTO:
            case COPYFROM:
                return new CopyParamsImpl(vars, workDir);
            case LIST:
            case LISTRECURSIVE:
                return new ListParamsImpl(vars, workDir);
            default:
                throw new IllegalStateException("Cannot handle action: '" + action + "'.");
        }
    }

    protected TaskParamsImpl(Variables input, Path workDir) {
        this.input = input;
        this.workDir = workDir;
    }

    @Override
    public Path workDir() {
        return workDir;
    }

    @Override
    public Action action() {
        return action(input);
    }

    @Override
    public boolean ignoreErrors() {
        return input.getBoolean(IGNORE_ERRORS_KEY, TaskParams.super.ignoreErrors());
    }

    @Override
    public Auth auth() {
        Variables authVars = new MapBackedVariables(input.assertMap(AUTH_KEY));
        return new Auth(
                authVars.assertString(USERNAME_KEY),
                authVars.assertString(PASSWORD_KEY),
                authVars.getString(DOMAIN_KEY)
        );
    }

    @Override
    public String serverBaseUrl() {
        return input.assertString(SERVER_URL_KEY);
    }

    public static class CopyParamsImpl extends TaskParamsImpl implements TaskParams.CopyParams {
        private static final String PATHS_KEY = "paths";
        private static final String SRC_KEY = "src";
        private static final String DST_KEY = "dst";
        private static final String OVERWRITE_KEY = "overwrite";

        public CopyParamsImpl(Variables input, Path workDir) {
            super(input, workDir);
        }


        @Override
        public List<String> src() {
            Object src = input.get(SRC_KEY);

            if (src instanceof String) {
                return Collections.singletonList((String) src);
            }

            if (src instanceof List) {
                return ((List<?>) src).stream()
                        .map(Object::toString)
                        .collect(Collectors.toList());
            }

            throw new IllegalArgumentException("Invalid 'src' type. Must be string or list of strings.");
        }

        @Override
        public String dst() {
            return input.assertString(DST_KEY);
        }

        @Override
        public boolean overwrite() {
            return input.getBoolean(OVERWRITE_KEY, CopyParams.super.overwrite());
        }
    }

    private static class ListParamsImpl extends TaskParamsImpl implements TaskParams.ListParams {
        private static final String PATH_KEY = "path";

        public ListParamsImpl(Variables input, Path workDir) {
            super(input, workDir);
        }

        @Override
        public String path() {
            return input.assertString(PATH_KEY);
        }
    }

    private static Action action(Variables variables) {
        String action = variables.assertString(ACTION_KEY);
        try {
            return Action.valueOf(action.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown action: '" + action + "'. Available actions: " + Arrays.toString(Action.values()));
        }
    }
}
