package com.walmartlabs.concord.plugins.jira;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.walmartlabs.concord.client2.impl.MultipartBuilder;
import com.walmartlabs.concord.client2.impl.MultipartRequestBodyHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JiraClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new Jdk8Module());
    static final JavaType MAP_TYPE = MAPPER.getTypeFactory()
            .constructMapType(HashMap.class, String.class, Object.class);
    private static final JavaType LIST_OF_MAPS_TYPE = MAPPER.getTypeFactory()
            .constructCollectionType(List.class, MAP_TYPE);
    private static final String CONTENT_TYPE = "Content-Type";

    private final HttpClient client;
    private final JiraClientCfg cfg;
    private URI url;
    private int successCode;
    private String auth;

    public JiraClient(JiraClientCfg cfg) {
        this.cfg = cfg;

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(cfg.connectTimeout()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public JiraClient url(String url) {
        this.url = URI.create(url);
        return this;
    }

    public JiraClient successCode(int successCode) {
        this.successCode = successCode;
        return this;
    }

    public JiraClient jiraAuth(String auth) {
        this.auth = auth;
        return this;
    }

    public Map<String, Object> get() throws IOException {
        HttpRequest request = requestBuilder(auth)
                .uri(url)
                .GET()
                .build();

        return call(request, MAP_TYPE);
    }

    public Map<String, Object> post(Map<String, Object> data) throws IOException {
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(data));
        HttpRequest request = requestBuilder(auth)
                .uri(url)
                .POST(body)
                .header(CONTENT_TYPE, "application/json; charset=utf-8")
                .build();

        return call(request, MAP_TYPE);
    }

    public void post(File file) throws IOException {
        var requestBody = new MultipartBuilder()
                .addFormDataPart("file", file.getName(), new MultipartRequestBodyHandler.PathRequestBody(file.toPath()))
                .build();

        try (InputStream body = requestBody.getContent()) {
            var req = HttpRequest.newBuilder()
                    .uri(url)
                    .POST(HttpRequest.BodyPublishers.ofInputStream(() -> body))
                    .header(CONTENT_TYPE, requestBody.contentType().toString())
                    .header("X-Atlassian-Token", "nocheck")
                    .build();

            call(req, LIST_OF_MAPS_TYPE);
        }
    }

    public void put(Map<String, Object> data) throws IOException {
        HttpRequest request = requestBuilder(auth)
                .uri(url)
                .PUT(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(data)))
                .header(CONTENT_TYPE, "application/json; charset=utf-8")
                .build();

        call(request, MAP_TYPE);
    }

    public void delete() throws IOException {
        HttpRequest request = requestBuilder(auth)
                .uri(url)
                .DELETE()
                .build();

        call(request, MAP_TYPE);
    }

    private HttpRequest.Builder requestBuilder(String auth) {
        return HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(cfg.readTimeout()))
                .header("Authorization", auth)
                .header("Accept", "application/json");
    }

    private <T> T call(HttpRequest request, JavaType returnType) throws IOException {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String results = response.body();
            assertResponseCode(statusCode, results, successCode);

            return MAPPER.readValue(results, returnType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void assertResponseCode(int code, String result, int successCode) {
        if (code == successCode) {
            return;
        }

        if (code == 400) {
            throw new IllegalStateException("input is invalid (e.g. missing required fields, invalid values). Here are the full error details: " + result);
        } else if (code == 401) {
            throw new IllegalStateException("User is not authenticated. Here are the full error details: " + result);
        } else if (code == 403) {
            throw new IllegalStateException("User does not have permission to perform request. Here are the full error details: " + result);
        } else if (code == 404) {
            throw new IllegalStateException("Issue does not exist. Here are the full error details: " + result);
        } else if (code == 500) {
            throw new IllegalStateException("Internal Server Error. Here are the full error details" + result);
        } else {
            throw new IllegalStateException("Error: " + result);
        }
    }

}
