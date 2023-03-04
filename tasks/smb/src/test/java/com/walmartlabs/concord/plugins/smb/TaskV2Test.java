package com.walmartlabs.concord.plugins.smb;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc., Concord Authors
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


import com.walmartlabs.concord.plugins.smb.v2.SmbTaskV2;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.sdk.MapUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Testcontainers
class TaskV2Test {

    protected static final Network network = Network.newNetwork();

    private static final String TEST_SMB_USER = "testuser";
    private static final String TEST_SMB_PASS = "test";
    private static final String TEST_SMB_PATH = "test_share";

    @TempDir
    Path tempDir;

    @Container
    public final static GenericContainer<?> smbContainer =
            new GenericContainer<>(DockerImageName
                        // set env var to point to specific image/custom repo
                        .parse("dperson/samba:latest") // TODO env var
                        .asCompatibleSubstituteFor("samba"))
                    .withNetwork(network)
                    .withExposedPorts(139, 445)
                    .withCommand("-p",
                            "-u", String.format("%s;%s", TEST_SMB_USER, TEST_SMB_PASS),
                            "-s", "public;/share",
                            "-s", String.format("%s;/testpath;no;no;no;%s", TEST_SMB_PATH, TEST_SMB_USER))
                    .withNetworkAliases("smb");

    @BeforeEach
    void setup() {

    }

    protected static Path copyResource(String resourcePath, Path dest) {
        URL rUrl = TaskV2Test.class.getResource(resourcePath);
        if (rUrl == null) {
            throw new IllegalArgumentException("Test resource not found: " + resourcePath);
        }

        try (InputStream is = rUrl.openStream()) {
            if (!Files.exists(dest.getParent())) {
                Files.createDirectories(dest.getParent());
            }

            Files.copy(is, dest);
        } catch (Exception e) {
            throw new RuntimeException("Error copying resource: " + e.getMessage());
        }

        return dest;
    }

    @Test
    void testCopy() {
        Path fileToCopy = tempDir.resolve("hello.txt");
        copyResource("hello.txt", fileToCopy);

        List<String> copiedFiles = copyTo(Collections.singletonList(fileToCopy), "/");
        List<Path> finalFiles = copyFrom(copiedFiles);

        Assertions.assertTrue(finalFiles.stream().allMatch(Files::exists));
    }

    @Test
    void testList() {
        List<String> expectedFiles = Arrays.asList("file1.txt", "file2.txt");
        List<Path> filesToCopy = expectedFiles.stream()
                .map(e -> copyResource("hello.txt", tempDir.resolve(e)))
                .collect(Collectors.toList());


        // -- copy files to server
        List<String> copiedFiles = copyTo(filesToCopy, "/");
        Assertions.assertEquals(expectedFiles, copiedFiles);

        // -- validate files listed on the server
        List<String> foundFiles = listFiles("/", false);
        Assertions.assertEquals(copiedFiles, foundFiles);
    }

    @Test
    void testRecursiveList() {
        List<String> expectedFiles = Arrays.asList("file1.txt", "dir/file2.txt");
        List<Path> filesToCopy = expectedFiles.stream()
                .map(e -> copyResource("hello.txt", tempDir.resolve(e)))
                .collect(Collectors.toList());

        // -- copy files to server
        List<String> copiedFiles = copyTo(filesToCopy, "/");
        Assertions.assertEquals(expectedFiles, copiedFiles);

        // -- validate files listed on the server
        List<String> foundFiles = listFiles("/", true);
        Assertions.assertEquals(copiedFiles, foundFiles);
    }

    @Test
    void testDeepRecursiveList() {
        List<String> srcFiles = Arrays.asList("file1.txt", "dir/file2.txt");
        List<Path> filesToCopy = srcFiles.stream()
                .map(e -> copyResource("hello.txt", tempDir.resolve(e)))
                .collect(Collectors.toList());

        // -- copy files to server
        List<String> copiedFiles = copyTo(filesToCopy, "/subDir");
        Assertions.assertEquals(srcFiles, copiedFiles);

        // -- validate files listed on the server

        List<String> foundFiles = listFiles("/", true);
        List<String> expectedFiles = Arrays.asList("subDir/file1.txt", "subDir/dir/file2.txt");
        Assertions.assertEquals(expectedFiles, foundFiles);
    }

    private List<String> listFiles(String path, boolean recursive) {
        Task task = new SmbTaskV2(tempDir);

        Map<String, Object> inputCfg = new HashMap<>(defaultInput());
        inputCfg.put("path", path);
        inputCfg.put("action", recursive ? "listRecursive" : "list");

        Variables input = new MapBackedVariables(inputCfg);

        TaskResult.SimpleResult result =
                (TaskResult.SimpleResult) Assertions.assertDoesNotThrow(() -> task.execute(input));

        Assertions.assertTrue(result.ok());
        Map<String, Object> data = MapUtils.assertMap(result.values(), "data");
        return MapUtils.assertList(data, "paths");
    }

    private List<String> copyTo(List<Path> filesToCopy, String destRoot) {
        Task task = new SmbTaskV2(tempDir);

        Map<String, Object> inputCfg = new HashMap<>(defaultInput());

        List<String> src = filesToCopy.stream()
                .map(tempDir::relativize)
                .map(Objects::toString)
//                .map(e -> createCopyPath(e.toString(), e.toString()))
                .collect(Collectors.toList());

        inputCfg.put("src", src);
        inputCfg.put("dst", destRoot);
        inputCfg.put("action", "copyTo");

        Variables input = new MapBackedVariables(inputCfg);

        TaskResult.SimpleResult result =
                (TaskResult.SimpleResult) Assertions.assertDoesNotThrow(() -> task.execute(input));

        Assertions.assertTrue(result.ok());


        return src;  //TODO return actual calculated paths

//        return paths.stream()
//                .map(e -> e.get("dst"))
//                .collect(Collectors.toList());
    }

    private List<Path> copyFrom(List<String> remoteFiles) {
        Task task = new SmbTaskV2(tempDir);

        Map<String, Object> inputCfg = new HashMap<>(defaultInput());

        List<Map<String, String>> paths = remoteFiles.stream()
                .map(e -> createCopyPath(e, tempDir.relativize(tempDir.resolve(e)).toString()))
                .collect(Collectors.toList());

        inputCfg.put("paths", paths);
        inputCfg.put("action", "copyFrom");

        Variables input = new MapBackedVariables(inputCfg);

        TaskResult.SimpleResult result = (TaskResult.SimpleResult) Assertions.assertDoesNotThrow(() -> task.execute(input));

        Assertions.assertTrue(result.ok());

        return remoteFiles.stream()
                .map(tempDir::resolve)
                .collect(Collectors.toList());
    }

    private Map<String, Object> defaultInput() {
        Map<String, Object> inputCfg = new HashMap<>();

        inputCfg.put("serverBaseUrl", String.format("smb://localhost:%s/%s/",
                smbContainer.getMappedPort(445),
                TEST_SMB_PATH));
        Map<String, Object> auth = new HashMap<>();
        auth.put("username", TEST_SMB_USER);
        auth.put("password", TEST_SMB_PASS);
        inputCfg.put("auth", auth);

        return inputCfg;
    }

    private static Map<String, String> createCopyPath(String src, String dst) {
        Map<String, String> m = new HashMap<>();
        m.put("src", src);
        m.put("dst", dst);

        return m;
    }
}
