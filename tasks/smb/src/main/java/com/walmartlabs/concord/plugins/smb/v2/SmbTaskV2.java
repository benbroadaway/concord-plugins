package com.walmartlabs.concord.plugins.smb.v2;

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

import com.walmartlabs.concord.plugins.smb.model.*;
import com.walmartlabs.concord.plugins.smb.model.exception.SmbException;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.Configuration;
import jcifs.SmbResource;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.walmartlabs.concord.plugins.smb.model.TaskParams.*;

@Named("smb")
public class SmbTaskV2 implements Task {
    private static final Logger log = LoggerFactory.getLogger(SmbTaskV2.class);

    private final Path workDir;

    @Inject
    public SmbTaskV2(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public TaskResult.SimpleResult execute(Variables input) {
        TaskParams params = TaskParamsImpl.of(input.toMap(), Collections.emptyMap(), Collections.emptyMap(), workDir);

        try {
            Map<String, Object> data = executeAction(params);
            return TaskResult.success().value("data", data);
        } catch (Exception e) {
            if (params.ignoreErrors()) {
                return TaskResult.fail(e.getMessage());
            }
            throw e;
        }
    }

    private static Map<String, Object> executeAction(TaskParams params) {
        CIFSContext cifsCtx = initCifs(params);
        Map<String, Object> result = new HashMap<>();

        switch (params.action()) {
            case COPYTO:
                copy((CopyParams) params, (p, path) -> copyTo(p, cifsCtx, path));
                break;
            case COPYFROM:
                copy((CopyParams) params, (p, path) -> copyFrom(p, cifsCtx, path));
                break;
            case LIST:
                List<String> paths = listFiles((ListParams) params, cifsCtx);
                result.put("paths", paths);
                break;
            case LISTRECURSIVE:
                List<String> recursivePaths = recursiveListFiles((ListParams) params, cifsCtx);
                result.put("paths", recursivePaths);
                break;
            default:
                throw new IllegalStateException("Unknown action: " + params.action());
        }

        return result;
    }

    private static CIFSContext initCifs(TaskParams params) {
        try {
            Auth auth = params.auth();
            NtlmPasswordAuthenticator authenticator =
                    new NtlmPasswordAuthenticator(auth.domain(), auth.username(), auth.password());
            Configuration cfg = new PropertyConfiguration(initProperties(params));

            return new BaseContext(cfg).withCredentials(authenticator);
        } catch (CIFSException e) {
            throw new SmbException("Error initializing cifs context: " + e.getMessage());
        }
    }

    private static void copy(CopyParams params, BiConsumer<CopyParams, CopyPath> c) {
        String rawDest = params.dst().replaceAll("^/", "");

        Path destRoot = Paths.get(rawDest);
        List<CopyPath> copyPaths = params.src().stream()
                .map(Paths::get)
                .flatMap(src -> generateCopyPaths(src, destRoot, params.workDir()).stream())
                .collect(Collectors.toList());


        copyPaths.forEach(copyPath -> c.accept(params, copyPath));
    }

    private static List<CopyPath> generateCopyPaths(Path src, Path destRoot, Path workDir) {
        Path safePath = assertInWorkDir(src, workDir);

        try {
            if (Files.isDirectory(safePath)) {
                try (Stream<Path> stream = Files.walk(safePath)) {
                    return stream
                            .map(e -> new CopyPathImpl(e, destRoot.resolve(src.relativize(e))))
                            .collect(Collectors.toList());
                }
            }

            return Collections.singletonList(new CopyPathImpl(safePath, destRoot.resolve(workDir.relativize(safePath))));
        } catch (IOException e) {
            throw new SmbException("Error resolving files to copy", e);
        }
    }

    private static void copyTo(CopyParams params, CIFSContext cifsCtx, CopyPath copyPath) {
        try (SmbFile smbFile = new SmbFile(params.serverBaseUrl(), cifsCtx)) {

            Path dst = copyPath.dst();

            if (dst.getParent() != null && !dst.getParent().toString().equals("/")) {
                SmbResource parent = smbFile.resolve(dst.getParent().toString());
                parent.mkdirs();

//                creatParents(params.serverBaseUrl(), cifsCtx, dst.getParent());
            }

    // TODO handle copying files in subdir
    // TODO also handle copying dirs/recursive

            SmbResource res = smbFile.resolve(dst.toString());
            copyTo(res, copyPath.src());
        } catch (Exception e) {
            throw new SmbException("Error writing file to smb: " + e.getMessage(), e);
        }
    }

    private static void creatParents(String root, CIFSContext cifsCtx, Path remoteParents) {
        try (SmbFile smbFile = new SmbFile(root + remoteParents, cifsCtx)) {
            smbFile.mkdirs();
        } catch (Exception e) {
            throw new SmbException("Error writing file to smb: " + e.getMessage(), e);
        }
    }

    /**
     * Asserts given path <code>p</code> is located within <code>workDir</code>
     * @param p path to check
     * @param workDir path indicating where <code>p</code> must be located
     * @return relative path to given path <code>p</code> in <code>workDir</code>
     */
    private static Path assertInWorkDir(Path p, Path workDir) {
        if (p.isAbsolute()) {
            // easy enough, make sure it's inside workDir
            if (p.startsWith(workDir)) {
                return p;
            }
        } else {
            // resolve to an absolute path
            Path abs = workDir.resolve(p).toAbsolutePath();
            // now make sure it's inside workDir
            if (abs.startsWith(workDir)) {
                return abs;
            }
        }

        log.warn("Invalid path: {}", p);
        throw new IllegalArgumentException("Source path must be within working directory!");
    }

    private static void copyTo(SmbResource res, Path src) {
        try (BufferedOutputStream out = new BufferedOutputStream(res.openOutputStream());
             BufferedInputStream in = new BufferedInputStream(Files.newInputStream(src))) {

            byte[] buf = new byte[2048];
            int bytesRead;

            while ((bytesRead = in.read(buf)) != -1) {
                out.write(buf, 0, bytesRead);
            }

            out.flush();
        } catch (Exception e) {
            throw new SmbException("Error writing file to smb: " + e.getMessage(), e);
        }
    }

    private static void copyFrom(TaskParams params, CIFSContext cifsCtx, CopyPath copyPath) {
        try (SmbFile smbFile = new SmbFile(params.serverBaseUrl(), cifsCtx)) {
            Path dst = copyPath.dst();
            if (!dst.startsWith(params.workDir())) {
                throw new IllegalArgumentException("Destination path must be within working directory!");
            }

            if (!Files.exists(dst.getParent())) {
                Files.createDirectories(dst.getParent());
            }

            SmbResource res = smbFile.resolve(copyPath.src().toString());
            copyFrom(res, dst);
        } catch (Exception e) {
            throw new SmbException("Error reading file from smb source: " + e.getMessage(), e);
        }
    }

    private static void copyFrom(SmbResource res, Path dst) {
        try (BufferedInputStream in = new BufferedInputStream(res.openInputStream())) {
            Files.copy(in, dst);
        } catch (Exception e) {
            throw new SmbException("Error reading file from smb source: " + e.getMessage(), e);
        }
    }

//    private static List<CopyPath> convertPaths(CopyParams params) {
//        ObjectMapper mapper = new ObjectMapper();
//        JavaType t = mapper.getTypeFactory().constructParametricType(List.class, CopyPathImpl.class);
//
//        return mapper.convertValue(params.paths(), t);
//    }

    private static Properties initProperties(TaskParams params) {
        Properties props = new Properties();


        return props;
    }

    private static List<String> listFiles(ListParams params, CIFSContext cifsCtx) {
        // server and share path needs to end with slash
//        String toRemove = params.serverBaseUrl().endsWith("/")
//                ? params.serverBaseUrl()
//                : params.serverBaseUrl() + "/";

        try (SmbFile smbFile = new SmbFile(params.serverBaseUrl(), cifsCtx)) {
            return Arrays.stream(smbFile.listFiles())
                    .map(SmbFile::getPath)
                    .map(p -> p.replace(params.serverBaseUrl(), ""))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new SmbException("Error copying from smb source: " + e.getMessage(), e);
        }
    }

    private static List<String> recursiveListFiles(ListParams params, CIFSContext cifsCtx) {
        try (SmbFile smbFile = new SmbFile(params.serverBaseUrl(), cifsCtx)) {
            return recurse(smbFile).stream()
                    .map(p -> p.replace(params.serverBaseUrl(), ""))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new SmbException("Error copying from smb source: " + e.getMessage(), e);
        }
    }

    public static List<String> recurse(SmbFile smb) throws Exception {
        List<String> paths = new LinkedList<>();

        if (smb.isDirectory()) {
            for (SmbFile f : smb.listFiles()) {
                if (f.isDirectory()) {
                    paths.addAll(recurse(f));
                } else {
                    paths.add(f.getPath());
                }
            }
        }

        return paths;
    }
}
