/*
 * Copyright (c) 2025, Red Hat Inc. All rights reserved.
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
package vthread_props;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    public static void main(String[] args) {
        final String r = String.format("%s %s %s %s %s %s",
                runTest("System Properties Access", Main::testSystemPropertiesAccess),
                runTest("LocalDateTime.now()", Main::testLocalDateTimeNow),
                runTest("ZoneId Access", Main::testZoneIdAccess),
                runTest("Multiple Time Operations", Main::testMultipleTimeOperations),
                runTest("Virtual Thread Properties", Main::testVirtualThreadProperties),
                runTest("Process and System Operations", Main::testProcessAndSystemOperations));
        System.out.println("\n=== RESULT: " + r + " ===");
    }

    private static boolean testSystemPropertiesAccess() throws Exception {
        final AtomicReference<Properties> p = new AtomicReference<>();
        final AtomicReference<Exception> exception = new AtomicReference<>();
        final Thread vt = Thread.ofVirtual().start(() -> {
            try {
                if (!Thread.currentThread().isVirtual()) {
                    System.out.println("Error: Thread.currentThread().isVirtual() must be true for this test.");
                }
                final Properties props = System.getProperties();
                p.set(props);
                System.out.println("Accessed " + props.size() + " system properties");
            } catch (Exception e) {
                exception.set(e);
                e.printStackTrace();
            }
        });
        if (!vt.join(Duration.ofMillis(5))) {
            System.out.println("Error: Virtual thread did not join in time");
            return false;
        }
        if (exception.get() != null) {
            throw exception.get();
        }
        return p.get() != null && !p.get().isEmpty();
    }

    private static boolean testLocalDateTimeNow() throws Exception {
        final AtomicReference<LocalDateTime> dateTimeRef = new AtomicReference<>();
        final AtomicReference<Exception> exception = new AtomicReference<>();
        final Thread vt = Thread.ofVirtual().start(() -> {
            try {
                if (!Thread.currentThread().isVirtual()) {
                    System.out.println("Error: Thread.currentThread().isVirtual() must be true for this test.");
                }
                final LocalDateTime now = LocalDateTime.now();
                dateTimeRef.set(now);
                System.out.println("Time: " + now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } catch (Exception e) {
                exception.set(e);
                e.printStackTrace();
            }
        });
        if (!vt.join(Duration.ofMillis(20))) {
            System.out.println("Error: Virtual thread did not join in time");
            return false;
        }
        if (exception.get() != null) {
            throw exception.get();
        }
        return dateTimeRef.get() != null;
    }

    private static boolean testZoneIdAccess() throws Exception {
        final AtomicReference<ZoneId> zoneIdRef = new AtomicReference<>();
        final AtomicReference<Exception> exception = new AtomicReference<>();
        final Thread vt = Thread.ofVirtual().start(() -> {
            try {
                if (!Thread.currentThread().isVirtual()) {
                    System.out.println("Error: Thread.currentThread().isVirtual() must be true for this test.");
                }
                ZoneId zoneId = ZoneId.systemDefault();
                zoneIdRef.set(zoneId);
                System.out.println("System default zone: " + zoneId);
            } catch (Exception e) {
                exception.set(e);
                e.printStackTrace();
            }
        });
        if (!vt.join(Duration.ofMillis(15))) {
            System.out.println("Error: Virtual thread did not join in time");
            return false;
        }
        if (exception.get() != null) {
            throw exception.get();
        }
        return zoneIdRef.get() != null;
    }

    private static boolean testMultipleTimeOperations() throws Exception {
        final int threadCount = 10;
        final CountDownLatch l = new CountDownLatch(threadCount);
        final AtomicReference<Exception> exception = new AtomicReference<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                final int threadNum = i;
                executor.submit(() -> {
                    try {
                        System.out.println("Thread " + threadNum + " is virtual: " + (Thread.currentThread().isVirtual() ? "True" : "ERROR"));
                        System.out.println("Thread " + threadNum + ": " + LocalDateTime.now() + " in zone " + ZoneId.systemDefault());
                    } catch (Exception e) {
                        exception.set(e);
                        e.printStackTrace();
                    } finally {
                        l.countDown();
                    }
                });
            }
            if (!l.await(15, TimeUnit.MILLISECONDS)) {
                System.out.println("Error: Virtual threads did not join in time.");
                return false;
            }
        }
        if (exception.get() != null) {
            throw exception.get();
        }
        return true;
    }

    private static boolean testVirtualThreadProperties() throws Exception {
        final AtomicReference<Exception> exception = new AtomicReference<>();
        final Thread vt = Thread.ofVirtual().name("test-virtual-thread").start(() -> {
            try {
                final Thread currentThread = Thread.currentThread();
                if (!Thread.currentThread().isVirtual()) {
                    System.out.println("Error: Thread.currentThread().isVirtual() must be true for this test.");
                }
                System.out.println("Name: " + currentThread.getName());
                System.out.println("ID: " + currentThread.threadId());
                System.out.println("Priority: " + currentThread.getPriority());
                System.out.println("State: " + currentThread.getState());
                System.out.println("Thread group: " +
                        (currentThread.getThreadGroup() != null ? currentThread.getThreadGroup().getName() : "null"));
            } catch (Exception e) {
                exception.set(e);
                e.printStackTrace();
            }
        });
        if (!vt.join(Duration.ofMillis(5))) {
            System.out.println("Error: Virtual thread did not join in time");
            return false;
        }
        if (exception.get() != null) {
            throw exception.get();
        }
        return true;
    }

    private static boolean testProcessAndSystemOperations() throws Exception {
        final AtomicReference<Exception> exception = new AtomicReference<>();
        final Thread vt = Thread.ofVirtual().start(() -> {
            try {
                if (!Thread.currentThread().isVirtual()) {
                    System.out.println("Error: Thread.currentThread().isVirtual() must be true for this test.");
                }
                System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());
                System.out.println("Total memory: " + Runtime.getRuntime().totalMemory());
                System.out.println("Free memory: " + Runtime.getRuntime().freeMemory());
                System.out.println("Max memory: " + Runtime.getRuntime().maxMemory());
                System.out.println("Java version: " + System.getProperty("java.version"));
                final ProcessHandle current = ProcessHandle.current();
                System.out.println("Process ID: " + current.pid());
                System.out.println("Process is alive: " + current.isAlive());
                current.info().command().ifPresent(cmd -> System.out.println("Process command: " + cmd));
                current.info().startInstant().ifPresent(start -> System.out.println("Process start time: " + start));
                current.info().totalCpuDuration().ifPresent(duration -> System.out.println("Process CPU duration: " + duration));
            } catch (Exception e) {
                exception.set(e);
                e.printStackTrace();
            }
        });
        if (!vt.join(Duration.ofMillis(10))) {
            System.out.println("Error: Virtual thread did not join in time");
            return false;
        }
        if (exception.get() != null) {
            throw exception.get();
        }
        return true;
    }

    private static boolean runTest(String testName, TestRunnable test) {
        System.out.println("\n=== " + testName + " ===");
        try {
            final boolean r = test.run();
            System.out.println("Result: " + (r ? "SUCCESS" : "FAILURE"));
            return r;
        } catch (Exception e) {
            System.out.println("Result: EXCEPTION - " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @FunctionalInterface
    private interface TestRunnable {
        boolean run() throws Exception;
    }
}
