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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.MockContext;
import com.walmartlabs.concord.sdk.SecretService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JiraTaskTest {

    @RegisterExtension
    static WireMockExtension rule = WireMockExtension.newInstance()
            .options(wireMockConfig()
                    .dynamicPort()
                    .notifier(new ConsoleNotifier(true)))
            .build();

    @TempDir
    Path workDir;

    private JiraTask task;
    private Context mockContext;
    private final SecretService secretService = Mockito.mock(SecretService.class);

    @BeforeEach
    public void setup() {
        mockContext = new MockContext(new HashMap<>());
        mockContext.setVariable("txId", UUID.randomUUID());
        mockContext.setVariable("workDir", workDir.toString());
        task = new JiraTask(secretService);
        stubForBasicAuth();
        stubForCurrentStatus();
        stubForAddAttachment();
    }

    @Test
    void testCreateIssueWithBasicAuth() throws Exception {
        Map<String, Object> auth = new HashMap<>();
        Map<String, Object> basic = new HashMap<>();
        basic.put("username", "user");
        basic.put("password", "pass");

        auth.put("basic", basic);
        String url = rule.getRuntimeInfo().getHttpBaseUrl() + "/";
        initCxtForRequest(mockContext, "CREATEISSUE", url, "projKey", "summary", "description",
                "requestorUid", "bug", auth);

        task.execute(mockContext);
    }

    @Test
    void testCreateIssueWithSecret() throws Exception {
        Map<String, Object> auth = new HashMap<>();
        Map<String, Object> secret = new HashMap<>();
        secret.put("name", "secret");
        secret.put("org", "organization");

        auth.put("secret", secret);

        String url = rule.getRuntimeInfo().getHttpBaseUrl() + "/";
        initCxtForRequest(mockContext, "CREATEISSUE", url, "projKey", "summary", "description",
                "requestorUid", "bug", auth);
        task.execute(mockContext);
    }

    @Test
    void testAddAttachment() {
        mockContext.setVariable("apiUrl", rule.getRuntimeInfo().getHttpBaseUrl() + "/");
        mockContext.setVariable("action", "addAttachment");
        mockContext.setVariable("issueKey", "issueId");
        mockContext.setVariable("userId", "userId");
        mockContext.setVariable("password", "password");
        mockContext.setVariable("filePath", "src/test/resources/sample.txt");

        task.execute(mockContext);
    }

    @Test
    void testCurrentStatus() {
        mockContext.setVariable("action", "currentStatus");
        mockContext.setVariable("apiUrl", rule.getRuntimeInfo().getHttpBaseUrl() + "/");
        mockContext.setVariable("issueKey", "issueId");
        mockContext.setVariable("userId", "userId");
        mockContext.setVariable("password", "password");

        task.execute(mockContext);

        var response = assertInstanceOf(String.class, mockContext.getVariable("issueStatus"));

        assertNotNull(response);
        assertEquals("Open", response);
    }


    private void initCxtForRequest(Context ctx, Object action, Object apiUrl, Object projectKey, Object summary, Object description,
                                   Object requestorUid, Object issueType, Object auth) throws Exception {

        ctx.setVariable("action", action);
        ctx.setVariable("apiUrl", apiUrl);
        ctx.setVariable("projectKey", projectKey);
        ctx.setVariable("summary", summary);
        ctx.setVariable("description", description);
        ctx.setVariable("requestorUid", requestorUid);
        ctx.setVariable("issueType", issueType);
        ctx.setVariable("auth", auth);

        doReturn(getCredentials()).when(secretService)
                .exportCredentials(any(), anyString(), anyString(), anyString(), anyString(), anyString());

    }

    private Map<String, String> getCredentials() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "user");
        credentials.put("password", "pwd");
        return credentials;
    }

    private void stubForBasicAuth() {
        rule.stubFor(post(urlEqualTo("/issue/"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                  "  \"id\": \"123\",\n" +
                                  "  \"key\": \"key1\",\n" +
                                  "  \"self\": \"2\"\n" +
                                  "}\n"))
        );
    }

    private void stubForAddAttachment() {
        rule.stubFor(post(urlEqualTo("/issue/issueId/attachments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\n" +
                                  "  \"id\": \"123\",\n" +
                                  "  \"key\": \"key1\",\n" +
                                  "  \"self\": \"2\"\n" +
                                  "}]"))
        );
    }

    private void stubForCurrentStatus() {
        var body = Map.of(
                "fields", Map.of(
                        "status", Map.of(
                                "name", "Open"
                        )
                )
        );

        var mapper = new ObjectMapper();

        try {
            rule.stubFor(get(urlEqualTo("/issue/issueId?fields=status"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(mapper.writeValueAsString(body)))
            );
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid json: " + e.getMessage());
        }
    }
}
