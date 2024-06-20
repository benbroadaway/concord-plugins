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

import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonTest {

    @TempDir
    Path workDir;

    @Mock
    JiraClient jiraClient;

    @Mock
    JiraSecretService jiraSecretService;

    private JiraTaskCommon delegate;
    Map<String, Object> input;

    @BeforeEach
    public void setup() {
//        mockContext.setVariable("txId", UUID.randomUUID());
//        mockContext.setVariable("workDir", workDir.toString());
        delegate = new MockDelegate(jiraSecretService, jiraClient);

        input = new HashMap<>();
        input.put("apiUrl", "https://localhost:1234/");
        input.put(
                "auth", Map.of(
                        "basic", Map.of(
                                "username", "user",
                                "password", "pass"
                            )
                )
        );

        when(jiraClient.jiraAuth(any())).thenReturn(jiraClient);
        when(jiraClient.url(anyString())).thenReturn(jiraClient);
        when(jiraClient.successCode(anyInt())).thenReturn(jiraClient);
    }

    @Test
    void testCreateIssueWithBasicAuth() throws Exception {
        input.put("action", "createIssue");
        input.put("projectKey", "mock-proj-key");
        input.put("summary", "mock-summary");
        input.put("description", "mock-description");
        input.put("requestorUid", "mock-uid");
        input.put("issueType", "bug");

        when(jiraClient.post(anyMap())).thenReturn(Map.of("key", "\"result-key\""));

        var result = delegate.execute(new TaskParams.CreateIssueParams(new MapBackedVariables(input)));
        assertNotNull(result);
        assertEquals("result-key", result.get("issueId"));

        verify(jiraSecretService, times(0))
                .exportCredentials("organization", "secret", null);
    }

    @Test
    void testCreateIssueWithSecret() throws Exception {
        input.put("action", "createIssue");
        input.put("projectKey", "mock-proj-key");
        input.put("summary", "mock-summary");
        input.put("description", "mock-description");
        input.put("requestorUid", "mock-uid");
        input.put("issueType", "bug");
        input.put("auth", Map.of(
                "secret", Map.of(
                        "org", "organization",
                        "name", "secret"
                )
        ));

        when(jiraSecretService.exportCredentials(any(), any(), any())).thenReturn(new JiraCredentials("user", "pwd"));
        when(jiraClient.post(anyMap())).thenReturn(Map.of("key", "\"result-key\""));

        var result = delegate.execute(new TaskParams.CreateIssueParams(new MapBackedVariables(input)));
        assertNotNull(result);
        assertEquals("result-key", result.get("issueId"));

        verify(jiraSecretService, times(1))
                .exportCredentials("organization", "secret", null);
    }

    @Test
    void testAddAttachment() throws IOException {
        input.put("action", "addAttachment");
        input.put("issueKey", "issueId");
        input.put("userId", "userId");
        input.put("password", "password");
        input.put("filePath", "src/test/resources/sample.txt");

        doNothing().when(jiraClient).post(Mockito.any(File.class));

        var result = delegate.execute(new TaskParams.AddAttachmentParams(new MapBackedVariables(input)));

        assertTrue(result.isEmpty());
    }

//    @Test
//    void testCurrentStatus() {
//        mockContext.setVariable("action", "currentStatus");
//        mockContext.setVariable("apiUrl", rule.getRuntimeInfo().getHttpBaseUrl() + "/");
//        mockContext.setVariable("issueKey", "issueId");
//        mockContext.setVariable("userId", "userId");
//        mockContext.setVariable("password", "password");
//
//        task.execute(mockContext);
//
//        var response = assertInstanceOf(String.class, mockContext.getVariable("issueStatus"));
//
//        assertNotNull(response);
//        assertEquals("Open", response);
//    }
//

    private static class MockDelegate extends JiraTaskCommon {
        private final JiraClient jiraClient;
        public MockDelegate(JiraSecretService jiraSecretService, JiraClient jiraClient) {
            super(jiraSecretService);
            this.jiraClient = jiraClient;
        }

        @Override
        JiraClient getClient(TaskParams in) {
            return jiraClient;
        }
    }
}
