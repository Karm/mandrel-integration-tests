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
package for_serialization;

import sun.reflect.ReflectionFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Michal Karm Babacek <karm@redhat.com>
 * Inspired by https://github.com/oracle/graal/issues/8509
 * Tests https://github.com/oracle/graal/issues/9581
 */
public class Main {

    static class Muhehehe implements Serializable {
        @Serial
        private static final long serialVersionUID = 5197858094838069415L;
    }

    class MuheheheNested implements Serializable {
        @Serial
        private static final long serialVersionUID = 6197858094838069415L;

        class MuheheheNestedNested implements Serializable {
            @Serial
            private static final long serialVersionUID = 7197858094838069415L;
        }
    }

    public static void main(String[] args) throws Exception {
        final Class<?>[] arrayTypes = new Class[] {
                boolean[].class,
                boolean[][].class,
                byte[].class,
                byte[][].class,
                char[].class,
                char[][].class,
                double[].class,
                double[][].class,
                float[].class,
                float[][].class,
                int[].class,
                int[][].class,
                long[].class,
                long[][].class,
                short[].class,
                short[][].class,
                Boolean[].class,
                Boolean[][].class,
                Byte[].class,
                Byte[][].class,
                Character[].class,
                Character[][].class,
                Double[].class,
                Double[][].class,
                Float[].class,
                Float[][].class,
                Integer[].class,
                Integer[][].class,
                Long[].class,
                Long[][].class,
                Short[].class,
                Short[][].class,
                // https://github.com/oracle/graal/issues/9581
                String[].class,
                String[][].class,
                Void[].class,
                Void[][].class,
                Class[].class,
                Class[][].class,
                Object[].class,
                Object[][].class,
                Number[].class,
                Number[][].class,
                Comparable[].class,
                Comparable[][].class,
                Serializable[].class,
                Serializable[][].class,
                Cloneable[].class,
                Cloneable[][].class,
                Readable[].class,
                Readable[][].class,
                AutoCloseable[].class,
                AutoCloseable[][].class,
                Closeable[].class,
                Closeable[][].class,
                Appendable[].class,
                Appendable[][].class,
                PrintStream[].class,
                PrintStream[][].class,
                PrintWriter[].class,
                PrintWriter[][].class,
                Record[].class,
                Record[][].class,
                Enum[].class,
                Enum[][].class,
                Enum[][][].class,
                Enum[][][][].class,
                Enum[][][][][].class,
                Muhehehe[].class,
                Muhehehe[][].class,
                Muhehehe[][][].class,
                MuheheheNested[].class,
                MuheheheNested[][].class,
                MuheheheNested[][][].class,
                MuheheheNested.MuheheheNestedNested[].class,
                MuheheheNested.MuheheheNestedNested[][].class,
                MuheheheNested.MuheheheNestedNested[][][].class
        };

        final File f = Files.createTempFile(Path.of("."), "tmp", ".ser").toFile();
        f.deleteOnExit();
        for (int i = 0; i < arrayTypes.length; i++) {
            final Constructor<?> cons = ReflectionFactory.getReflectionFactory()
                    .newConstructorForSerialization(arrayTypes[i],
                            java.lang.Object.class.getDeclaredConstructor((Class<?>[]) null));
            final Object o = Array.newInstance(arrayTypes[i].getComponentType(), 2);
            try (final ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
                    final ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                oos.writeObject(o);
                oos.flush();
                final Object o2 = ois.readObject();
                System.out.printf("%d %d %s %s %b\n",
                        i, cons.getParameterCount(), o.getClass().descriptorString(), o2.getClass().descriptorString(),
                        o2.getClass().equals(o.getClass()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("\nDone.");
    }
}

