/*
 * Copyright (c) 2021, Red Hat Inc. All rights reserved.
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
package org.graalvm.tests.integration.utils.versions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Closed interval from min to max.
 *
 * e.g. closed interval [20.1, 20.3.2], as in 20.1 <= GRAALVM_VERSION <= 20.3.2
 * translates to @IfMandrelVersion(min = "20.1", max="20.3.2").
 * //@formatter:off
 * Examples:
 *
 *     IfMandrelVersion(min = "20.1", max="20.3.2")
 *     i.e. [20.1, 20.3.2]
 *
 *     IfMandrelVersion(min = "21.1", inContainer = true)
 *     i.e. [21.1, +∞)
 * //@formatter:on
 * Note that versions 21.1.0.0-final and 21.1.0.0-snapshot and 21.1.0.0 are all considered equal.
 *
 * The actual comparator comes from Graal's own org.graalvm.home.Version.
 *
 * inContainer: Whether the version should be pulled from a builder image container.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MandrelVersionCondition.class)
@Test
public @interface IfMandrelVersion {
    String min() default "";

    String max() default "";

    boolean inContainer() default false;
}
