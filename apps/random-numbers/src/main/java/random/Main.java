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
package random;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, UUID: " + UUID.randomUUID().toString());
        IntStream intStream = new SecureRandom().ints(10, 0, 100);
        System.out.println("Hello, secureRandom: " + intStream.boxed().collect(Collectors.toList()));
    }
}
