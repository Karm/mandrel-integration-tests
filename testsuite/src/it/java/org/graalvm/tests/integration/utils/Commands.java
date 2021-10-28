/*
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.graalvm.tests.integration.utils;

import com.sun.security.auth.module.UnixSystem;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.graalvm.tests.integration.RuntimesSmokeTest.BASE_DIR;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public class Commands {
    private static final Logger LOGGER = Logger.getLogger(Commands.class.getName());
    public static final String CONTAINER_RUNTIME = getProperty(
            new String[]{"QUARKUS_NATIVE_CONTAINER_RUNTIME", "quarkus.native.container-runtime"},
            "docker");
    public static final String BUILDER_IMAGE = getProperty(
            new String[]{"QUARKUS_NATIVE_BUILDER_IMAGE", "quarkus.native.builder-image"},
            "quay.io/quarkus/ubi-quarkus-mandrel:21.3-java11");
    // Podman: Error: stats is not supported in rootless mode without cgroups v2
    public static final boolean PODMAN_WITH_SUDO = Boolean.parseBoolean(
            getProperty(new String[]{"PODMAN_WITH_SUDO", "podman.with.sudo"}, "true"));
    public static final String QUARKUS_VERSION = getProperty(
            new String[]{"QUARKUS_VERSION", "quarkus.version"},
            "2.2.3.Final");
    public static final boolean IS_THIS_WINDOWS = System.getProperty("os.name").matches(".*[Ww]indows.*");
    private static final Pattern NUM_PATTERN = Pattern.compile("[ \t]*[0-9]+[ \t]*");
    private static final Pattern ALPHANUMERIC_FIRST = Pattern.compile("([a-z0-9]+).*");
    private static final Pattern CONTAINER_STATS_MEMORY = Pattern.compile("(?:table)?[ \t]*([0-9\\.]+)([a-zA-Z]+).*");

    public static String getProperty(String[] alternatives, String defaultValue) {
        String prop = null;
        for (String p : alternatives) {
            String env = System.getenv().get(p);
            if (StringUtils.isNotBlank(env)) {
                prop = env;
                break;
            }
            String sys = System.getProperty(p);
            if (StringUtils.isNotBlank(sys)) {
                prop = sys;
                break;
            }
        }
        if (prop == null) {
            LOGGER.info("Failed to detect any of " + String.join(",", alternatives) +
                    " as env or sys props, defaulting to " + defaultValue);
            return defaultValue;
        }
        return prop;
    }

    public static String getUnixUIDGID() {
        final UnixSystem s = new UnixSystem();
        return s.getUid() + ":" + s.getGid();
    }

    public static String getBaseDir() {
        final String env = System.getenv().get("basedir");
        final String sys = System.getProperty("basedir");
        if (StringUtils.isNotBlank(env)) {
            return new File(env).getParent();
        }
        if (StringUtils.isBlank(sys)) {
            throw new IllegalArgumentException("Unable to determine project.basedir.");
        }
        return new File(sys).getParent();
    }

    public static void cleanTarget(Apps app) {
        // Apps build
        final String target = BASE_DIR + File.separator + app.dir + File.separator + "target";
        // Apps logging
        final String logs = BASE_DIR + File.separator + app.dir + File.separator + "logs";
        // Dir generated by debug symbols build
        final String sources = BASE_DIR + File.separator + app.dir + File.separator + "sources";
        cleanDirOrFile(target, logs, sources);
    }

    public static void cleanDirOrFile(String... path) {
        for (String s : path) {
            try {
                final File f = new File(s);
                FileUtils.forceDelete(f);
                FileUtils.forceDeleteOnExit(f);
            } catch (IOException e) {
                //Silence is golden
            }
        }
    }

    /**
     * Adds prefix on Windows, deals with podman on Linux
     *
     * @param baseCommand
     * @return
     */
    public static List<String> getRunCommand(String... baseCommand) {
        final List<String> runCmd = new ArrayList<>();
        if (IS_THIS_WINDOWS) {
            runCmd.add("cmd");
            runCmd.add("/C");
        } else if ("podman".equals(baseCommand[0]) && PODMAN_WITH_SUDO) {
            runCmd.add("sudo");
        }
        runCmd.addAll(Arrays.asList(baseCommand));
        return runCmd;
    }

    public static boolean waitForTcpClosed(String host, int port, long loopTimeoutS) throws InterruptedException, UnknownHostException {
        final InetAddress address = InetAddress.getByName(host);
        long now = System.currentTimeMillis();
        final long startTime = now;
        final InetSocketAddress socketAddr = new InetSocketAddress(address, port);
        while (now - startTime < 1000 * loopTimeoutS) {
            try (Socket socket = new Socket()) {
                // If it let's you write something there, it is still ready.
                socket.connect(socketAddr, 1000);
                socket.setSendBufferSize(1);
                socket.getOutputStream().write(1);
                socket.shutdownInput();
                socket.shutdownOutput();
                LOGGER.info("Socket still available: " + host + ":" + port);
            } catch (IOException e) {
                // Exception thrown - socket is likely closed.
                return true;
            }
            Thread.sleep(1000);
            now = System.currentTimeMillis();
        }
        return false;
    }

    public static int parsePort(String url) {
        return Integer.parseInt(url.split(":")[2].split("/")[0]);
    }

    /**
     * There might be this weird glitch where native-image command completes
     * but the FS does not appear to have the resulting binary ready and executable for the
     * next process *immediately*. Hence this small wait that mitigates this glitch.
     *
     * Note that nothing happens at the end of the timeout and the TS hopes for the best.
     *
     * @param command
     * @param directory
     */
    public static void waitForExecutable(List<String> command, File directory) {
        long now = System.currentTimeMillis();
        final long startTime = now;
        while (now - startTime < 1000) {
            if (new File(directory.getAbsolutePath() + File.separator + command.get(command.size() - 1)).canExecute()) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            now = System.currentTimeMillis();
        }
    }

    public static Process runCommand(List<String> command, File directory, File logFile, Apps app, File input, Map<String, String> env) {
        // Skip the wait if the app runs as a container
        if (app.runtimeContainer == ContainerNames.NONE) {
            waitForExecutable(command, directory);
        }
        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        final Map<String, String> envA = processBuilder.environment();
        envA.put("PATH", System.getenv("PATH"));
        if (env != null) {
            envA.putAll(env);
        }
        processBuilder.directory(directory)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        if (input != null) {
            processBuilder.redirectInput(input);
        }
        Process pA = null;
        try {
            pA = processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return pA;
    }

    public static String runCommand(List<String> command, File directory) throws IOException {
        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        final Map<String, String> envA = processBuilder.environment();
        envA.put("PATH", System.getenv("PATH"));
        processBuilder.redirectErrorStream(true)
                .directory(directory);
        final Process p = processBuilder.start();
        try (InputStream is = p.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8); // note that UTF-8 would mingle glyphs on Windows
        }
    }

    public static String runCommand(List<String> command) throws IOException {
        return runCommand(command, new File("."));
    }

    public static Process runCommand(List<String> command, File directory, File logFile, Apps app, File input) {
        return runCommand(command, directory, logFile, app, input, null);
    }

    public static Process runCommand(List<String> command, File directory, File logFile, Apps app) {
        return runCommand(command, directory, logFile, app, null, null);
    }

    public static void pidKiller(long pid, boolean force) {
        try {
            if (IS_THIS_WINDOWS) {
                if (!force) {
                    final Process p = Runtime.getRuntime().exec(new String[]{
                            BASE_DIR + File.separator + "testsuite" + File.separator + "src" + File.separator + "it" + File.separator + "resources" + File.separator +
                                    "CtrlC.exe ", Long.toString(pid)});
                    p.waitFor(1, TimeUnit.MINUTES);
                }
                Runtime.getRuntime().exec(new String[]{"cmd", "/C", "taskkill", "/PID", Long.toString(pid), "/F", "/T"});
            } else {
                Runtime.getRuntime().exec(new String[]{"kill", force ? "-9" : "-15", Long.toString(pid)});
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public static boolean waitForContainerLogToMatch(String containerName, Pattern pattern, long timeout, long sleep, TimeUnit unit) throws IOException, InterruptedException {
        long timeoutMillis = unit.toMillis(timeout);
        long sleepMillis = unit.toMillis(sleep);
        long startMillis = System.currentTimeMillis();
        final ProcessBuilder processBuilder = new ProcessBuilder(getRunCommand(CONTAINER_RUNTIME, "logs", containerName));
        final Map<String, String> envA = processBuilder.environment();
        envA.put("PATH", System.getenv("PATH"));
        processBuilder.redirectErrorStream(true);
        while (System.currentTimeMillis() - startMillis < timeoutMillis) {
            Process p = processBuilder.start();
            try (BufferedReader processOutputReader =
                         new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = processOutputReader.readLine()) != null) {
                    if (pattern.matcher(l).matches()) {
                        return true;
                    }
                }
                p.waitFor();
            }
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
        return false;
    }

    public static List<String> getRunningContainersIDs() throws IOException, InterruptedException {
        final ProcessBuilder processBuilder = new ProcessBuilder(getRunCommand(CONTAINER_RUNTIME, "ps"));
        final Map<String, String> envA = processBuilder.environment();
        envA.put("PATH", System.getenv("PATH"));
        processBuilder.redirectErrorStream(true);
        final Process p = processBuilder.start();
        final List<String> ids = new ArrayList<>();
        try (BufferedReader processOutputReader =
                     new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String l = processOutputReader.readLine();
            // Skip the first line
            if (l == null || !l.startsWith("CONTAINER ID")) {
                throw new RuntimeException("Unexpected " + CONTAINER_RUNTIME + " command output. Check the daemon.");
            }
            while ((l = processOutputReader.readLine()) != null) {
                Matcher m = ALPHANUMERIC_FIRST.matcher(l);
                if (m.matches()) {
                    ids.add(m.group(1).trim());
                }
            }
            p.waitFor();
        }
        return Collections.unmodifiableList(ids);
    }

    public static void stopAllRunningContainers() throws InterruptedException, IOException {
        List<String> ids = getRunningContainersIDs();
        if (!ids.isEmpty()) {
            final List<String> cmd = new ArrayList<>(getRunCommand(CONTAINER_RUNTIME, "stop"));
            cmd.addAll(ids);
            final Process process = Runtime.getRuntime().exec(cmd.toArray(String[]::new));
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }

    public static void stopRunningContainers(String... containerNames) throws InterruptedException, IOException {
        for (String name : containerNames) {
            stopRunningContainer(name);
        }
    }

    public static void stopRunningContainer(String containerName) throws InterruptedException, IOException {
        final List<String> cmd = new ArrayList<>(getRunCommand(CONTAINER_RUNTIME, "stop", containerName));
        final Process process = Runtime.getRuntime().exec(cmd.toArray(String[]::new));
        process.waitFor(5, TimeUnit.SECONDS);
    }

    public static void removeContainers(String... containerNames) throws InterruptedException, IOException {
        for (String name : containerNames) {
            removeContainer(name);
        }
    }

    public static void removeContainer(String containerName) throws InterruptedException, IOException {
        final List<String> cmd = new ArrayList<>(getRunCommand(CONTAINER_RUNTIME, "rm", containerName, "--force"));
        final Process process = Runtime.getRuntime().exec(cmd.toArray(String[]::new));
        process.waitFor(5, TimeUnit.SECONDS);
    }

    /*
    No idea if Docker works with 1024 and Podman with 1000 :-)
    $ podman stats --no-stream --format "table {{.MemUsage}}" my-quarkus-mandrel-app-container
    table 18.06MB / 12.11GB
    $ docker stats --no-stream --format "table {{.MemUsage}}" my-quarkus-mandrel-app-container
    MEM USAGE / LIMIT
    13.43MiB / 11.28GiB
     */
    public static long getContainerMemoryKb(String containerName) throws IOException, InterruptedException {
        final ProcessBuilder pa = new ProcessBuilder(getRunCommand(
                CONTAINER_RUNTIME, "stats", "--no-stream", "--format", "table {{.MemUsage}}", containerName));
        final Map<String, String> envA = pa.environment();
        envA.put("PATH", System.getenv("PATH"));
        pa.redirectErrorStream(true);
        final Process p = pa.start();
        try (BufferedReader processOutputReader =
                     new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String l;
            while ((l = processOutputReader.readLine()) != null) {
                final Matcher m = CONTAINER_STATS_MEMORY.matcher(l);
                if (m.matches()) {
                    float value = Float.parseFloat(m.group(1));
                    String unit = m.group(2);
                    // Yes, precision is just fine here.
                    if (unit.startsWith("M")) {
                        return (long) value * 1024;
                    } else if (unit.startsWith("G")) {
                        return (long) value * 1024 * 1024;
                    } else if (unit.startsWith("k") || unit.startsWith("K")) {
                        return (long) value;
                    } else {
                        throw new IllegalArgumentException("We don't know how to work with memory unit " + unit);
                    }
                }
            }
            p.waitFor();
        }
        return -1L;
    }

    public static long getRSSkB(long pid) throws IOException, InterruptedException {
        ProcessBuilder pa;
        if (IS_THIS_WINDOWS) {
            // Note that PeakWorkingSetSize might be better, but we would need to change it on Linux too...
            // https://docs.microsoft.com/en-us/windows/win32/cimwin32prov/win32-process
            pa = new ProcessBuilder("wmic", "process", "where", "processid=" + pid, "get", "WorkingSetSize");
        } else {
            pa = new ProcessBuilder("ps", "-p", Long.toString(pid), "-o", "rss=");
        }
        final Map<String, String> envA = pa.environment();
        envA.put("PATH", System.getenv("PATH"));
        pa.redirectErrorStream(true);
        final Process p = pa.start();
        try (BufferedReader processOutputReader =
                     new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String l;
            while ((l = processOutputReader.readLine()) != null) {
                if (NUM_PATTERN.matcher(l).matches()) {
                    if (IS_THIS_WINDOWS) {
                        // Qualifiers: DisplayName ("Working Set Size"), Units ("bytes")
                        return Long.parseLong(l.trim()) / 1024L;
                    } else {
                        return Long.parseLong(l.trim());
                    }
                }
            }
            p.waitFor();
        }
        return -1L;
    }

    public static long getOpenedFDs(long pid) throws IOException, InterruptedException {
        ProcessBuilder pa;
        long count = 0;
        if (IS_THIS_WINDOWS) {
            pa = new ProcessBuilder("wmic", "process", "where", "processid=" + pid, "get", "HandleCount");
        } else {
            pa = new ProcessBuilder("lsof", "-F0n", "-p", Long.toString(pid));
        }
        final Map<String, String> envA = pa.environment();
        envA.put("PATH", System.getenv("PATH"));
        pa.redirectErrorStream(true);
        final Process p = pa.start();
        try (BufferedReader processOutputReader =
                     new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            if (IS_THIS_WINDOWS) {
                String l;
                // TODO: We just get a magical number with all FDs... Is it O.K.?
                while ((l = processOutputReader.readLine()) != null) {
                    if (NUM_PATTERN.matcher(l).matches()) {
                        return Long.parseLong(l.trim());
                    }
                }
            } else {
                // TODO: For the time being we count apples and oranges; we might want to distinguish .so and .jar ?
                while (processOutputReader.readLine() != null) {
                    count++;
                }
            }
            p.waitFor();
        }
        return count;
    }

    public static void processStopper(Process p, boolean force) throws InterruptedException {
        p.children().forEach(child -> {
            if (child.supportsNormalTermination()) {
                child.destroy();
            }
            pidKiller(child.pid(), force);
        });
        if (p.supportsNormalTermination()) {
            p.destroy();
            p.waitFor(3, TimeUnit.MINUTES);
        }
        pidKiller(p.pid(), force);
    }

    public static class ProcessRunner implements Runnable {
        final File directory;
        final File log;
        final List<String> command;
        final long timeoutMinutes;
        final Map<String, String> envProps;

        public ProcessRunner(File directory, File log, List<String> command, long timeoutMinutes) {
            this.directory = directory;
            this.log = log;
            this.command = command;
            this.timeoutMinutes = timeoutMinutes;
            this.envProps = null;
        }

        public ProcessRunner(File directory, File log, List<String> command, long timeoutMinutes, Map<String, String> envProps) {
            this.directory = directory;
            this.log = log;
            this.command = command;
            this.timeoutMinutes = timeoutMinutes;
            this.envProps = envProps;
        }

        @Override
        public void run() {
            final ProcessBuilder pb = new ProcessBuilder(command);
            final Map<String, String> env = pb.environment();
            env.put("PATH", System.getenv("PATH"));
            if (envProps != null) {
                env.putAll(envProps);
            }
            pb.directory(directory);
            pb.redirectErrorStream(true);
            Process p = null;
            try {
                if (!log.exists()) {
                    Files.createFile(log.toPath());
                }
                Files.write(log.toPath(),
                        ("Command: " + String.join(" ", command) + "\n").getBytes(), StandardOpenOption.APPEND);
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
                p = pb.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                Objects.requireNonNull(p, "command " + command + " not found/invalid")
                        .waitFor(timeoutMinutes, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static boolean searchBinaryFile(File binaryFile, byte[] match, long skipBytes) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(
                new FileInputStream(binaryFile))
        ) {
            final byte[] buffer = new byte[16384];
            boolean found = false;
            inputStream.skip(skipBytes);
            while (inputStream.read(buffer) != -1) {
                if (contains(buffer, match)) {
                    found = true;
                    break;
                }
            }
            return found;
        }
    }

    public static boolean contains(byte[] one, byte[] theOther) {
        if (one.length < theOther.length || theOther.length == 0) {
            return false;
        }
        for (int i = 0; i < one.length; i++) {
            int j = 0;
            for (; j < theOther.length; j++) {
                if (i + j >= one.length || one[i + j] != theOther[j]) {
                    break;
                }
            }
            if (j == theOther.length) {
                return true;
            }
        }
        return false;
    }

    public static boolean searchLogLines(Pattern p, File processLog, Charset charset) throws IOException {
        try (Scanner sc = new Scanner(processLog, charset)) {
            while (sc.hasNextLine()) {
                final Matcher m = p.matcher(sc.nextLine());
                if (m.matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean waitForBufferToMatch(StringBuffer stringBuffer, Pattern pattern, long timeout, long sleep, TimeUnit unit) {
        long timeoutMillis = unit.toMillis(timeout);
        long sleepMillis = unit.toMillis(sleep);
        long startMillis = System.currentTimeMillis();
        while (System.currentTimeMillis() - startMillis < timeoutMillis) {
            if (pattern.matcher(stringBuffer.toString()).matches()) {
                return true;
            }
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
        return false;
    }

    public static void builderRoutine(int steps, Apps app, StringBuilder report, String cn, String mn, File appDir, File processLog, Map<String, String> env) throws InterruptedException {
        // The last command is reserved for running it
        assertTrue(app.buildAndRunCmds.cmds.length > 1);
        Logs.appendln(report, "# " + cn + ", " + mn);
        for (int i = 0; i < steps; i++) {
            // We cannot run commands in parallel, we need them to follow one after another
            final ExecutorService buildService = Executors.newFixedThreadPool(1);
            final List<String> cmd = Commands.getRunCommand(app.buildAndRunCmds.cmds[i]);
            buildService.submit(new Commands.ProcessRunner(appDir, processLog, cmd, 10, env)); // might take a long time....
            Logs.appendln(report, (new Date()).toString());
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));
            shutdownAndAwaitTermination(buildService, 10, TimeUnit.MINUTES); // Native image build might take a long time....
        }
        assertTrue(processLog.exists());
    }

    // Copied from
    // https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/ExecutorService.html
    private static void shutdownAndAwaitTermination(ExecutorService pool, int timeout, TimeUnit unit) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait for existing tasks to terminate
            if (!pool.awaitTermination(timeout, unit)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(1, TimeUnit.MINUTES))
                    fail("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    public static void builderRoutine(Apps app, StringBuilder report, String cn, String mn, File appDir, File processLog) throws InterruptedException {
        builderRoutine(app.buildAndRunCmds.cmds.length - 1, app, report, cn, mn, appDir, processLog, null);
    }

    public static void builderRoutine(Apps app, StringBuilder report, String cn, String mn, File appDir, File processLog, Map<String, String> env) throws InterruptedException {
        builderRoutine(app.buildAndRunCmds.cmds.length - 1, app, report, cn, mn, appDir, processLog, env);
    }

    public static void builderRoutine(int steps, Apps app, StringBuilder report, String cn, String mn, File appDir, File processLog) throws InterruptedException {
        builderRoutine(steps, app, report, cn, mn, appDir, processLog, null);
    }

    public static void replaceInSmallTextFile(Pattern search, String replace, Path file, Charset charset) throws IOException {
        final String data = Files.readString(file, charset);
        Files.writeString(file, search.matcher(data).replaceAll(replace), charset, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static void replaceInSmallTextFile(Pattern search, String replace, Path file) throws IOException {
        replaceInSmallTextFile(search, replace, file, Charset.defaultCharset());
    }

    /**
     * Finds the first matching executable in a given dir,
     * *does not dive into the tree*, is not recursive...
     *
     * @param dir
     * @param regexp
     * @return null or the found file
     */
    public static File findExecutable(Path dir, Pattern regexp) {
        if (dir == null || Files.notExists(dir) || regexp == null) {
            throw new IllegalArgumentException("Path to " + dir + "must exist and regexp must nut be null.");
        }
        File[] f = dir.toFile().listFiles(pathname -> {
            if (pathname.isFile() && Files.isExecutable(pathname.toPath())) {
                return regexp.matcher(pathname.getName()).matches();
            }
            return false;
        });
        if (f == null || f.length < 1) {
            fail("Failed to find any executable in dir " + dir + ", matching regexp " + regexp.toString());
        }
        return f[0];
    }

    public static void cleanup(Process process, String cn, String mn, StringBuilder report, Apps app, File... log)
            throws InterruptedException, IOException {
        // Make sure processes are down even if there was an exception / failure
        if (process != null) {
            processStopper(process, true);
        }
        // Archive logs no matter what
        for (File f : log) {
            Logs.archiveLog(cn, mn, f);
        }
        Logs.writeReport(cn, mn, report.toString());
        cleanTarget(app);
    }
}
