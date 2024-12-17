/*
 * Copyright (c) 2024, Red Hat Inc. All rights reserved.
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
package jdkreflections;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class Main {

    /**
     * An intentionally elaborate way to do this to test the reflection support
     * on java.lang.Thread for native-image build configuration.
     * @param name
     * @return
     */
    static ExecutorService createVirtualThreadExecutor(String name) {
        try {
            final Method virtualThreadBuilderMethod = Arrays.stream(Thread.class.getMethods())
                    .filter(m -> m.getName().equals("ofVirtual"))
                    .findAny().orElseThrow();
            Object virtualThreadBuilder = virtualThreadBuilderMethod.invoke(null);
            final Method nameVirtualThreadBuilderMethod = Arrays.stream(virtualThreadBuilderMethod.getReturnType().getMethods())
                    .filter(m -> m.getName().equals("name") && m.getParameterCount() == 2)
                    .findAny().orElseThrow();
            virtualThreadBuilder = nameVirtualThreadBuilderMethod.invoke(virtualThreadBuilder, name, 10000L);
            final Method factoryVirtualThreadBuilderMethod = Arrays.stream(Class.forName("java.lang.Thread$Builder").getMethods())
                    .filter(m -> m.getName().equals("factory"))
                    .findAny().orElseThrow();
            final ThreadFactory factory = (ThreadFactory) factoryVirtualThreadBuilderMethod.invoke(virtualThreadBuilder);
            final Method newThreadPerTaskExecutorMethod = Arrays.stream(Executors.class.getMethods())
                    .filter(m -> m.getName().equals("newThreadPerTaskExecutor") && m.getGenericParameterTypes()[0].equals(ThreadFactory.class))
                    .findAny().orElseThrow();
            return (ExecutorService) newThreadPerTaskExecutorMethod.invoke(null, factory);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Fail :-)", e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        final ExecutorService executor = createVirtualThreadExecutor("meh-");
        executor.submit(() -> {
            try {
                final Method currentThreadMethod = Thread.class.getDeclaredMethod("currentThread");
                final Thread currentThread = (Thread) currentThreadMethod.invoke(null);
                final Method isVirtualMethod = Thread.class.getDeclaredMethod("isVirtual");
                final boolean isVirtual = (boolean) isVirtualMethod.invoke(currentThread);
                System.out.println("Hello from a " + (isVirtual ? "virtual" : "") + " thread called " + currentThread.getName());
                final Field interruptedField = Thread.class.getDeclaredField("interrupted");
                interruptedField.setAccessible(true);
                System.out.println("interrupted: " + interruptedField.getBoolean(currentThread));
                final Method holdsLockMethod = Arrays.stream(Thread.class.getMethods())
                        .filter(m -> m.getName().equals("holdsLock") && m.getGenericParameterTypes()[0].equals(Object.class))
                        .findAny().orElseThrow();
                System.out.println("holdsLock: " + ((boolean) holdsLockMethod.invoke(null, new Object())));
                final Method threadIdMethod = Thread.class.getDeclaredMethod("threadId");
                System.out.println("getId: " + ((long) threadIdMethod.invoke(currentThread)));
                final Method getNextThreadIdOffsetMethod = Thread.class.getDeclaredMethod("getNextThreadIdOffset");
                getNextThreadIdOffsetMethod.setAccessible(true);
                System.out.println("getNextThreadIdOffset: " + (long) getNextThreadIdOffsetMethod.invoke(null));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        executor.shutdown();
        executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS);
    }
}
