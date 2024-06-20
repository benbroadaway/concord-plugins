package com.walmartlabs.concord.plugins.jira;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc., Concord Authors
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JiraClientTest {

    @RegisterExtension
    static WireMockExtension rule = WireMockExtension.newInstance()
            .options(wireMockConfig()
                    .dynamicPort()
                    .notifier(new ConsoleNotifier(true)))
            .build();

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module());

    JiraClientCfg jiraClientCfg;

    @BeforeEach
    void setUp() {
        jiraClientCfg = new JiraClientCfg() {};
        stubForBasicAuth();
    }

    @Test
    void testPost() throws IOException {
        Map<String, Object> resp = new JiraClient(jiraClientCfg)
                .url(rule.baseUrl() + "/issue/")
                .jiraAuth(new JiraCredentials("mock-user", "mock-pass").authHeaderValue())
                .successCode(201)
                .post(Map.of("field1", "value1"));

        assertNotNull(resp);
        assertEquals("123", resp.get("id"));

        ServeEvent event = rule.getAllServeEvents().get(0);
        assertNotNull(event);

        Map<String, Object> requestBody = objectMapper.readValue(event.getRequest().getBody(), JiraClient.MAP_TYPE);
        assertEquals("value1", requestBody.get("field1"));

        String auth = event.getRequest().header("Authorization").firstValue();
        assertEquals("Basic " + Base64.getEncoder().encodeToString("mock-user:mock-pass".getBytes(StandardCharsets.UTF_8)), auth);
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

}
