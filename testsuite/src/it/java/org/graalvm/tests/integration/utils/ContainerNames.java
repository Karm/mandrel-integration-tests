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

/**
 * Convenient enum for collecting container names for apps
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public enum ContainerNames {
    QUARKUS_BUILDER_IMAGE_ENCODING("my-quarkus-mandrel-app-container"),
    DEBUG_QUARKUS_BUILDER_IMAGE_VERTX("my-quarkus-mandrel-app-container"), // Probably no reason to call them differently?
    IMAGEIO_BUILDER_IMAGE("my-imageio-runner"),
    JFR_SMOKE_BUILDER_IMAGE("my-jfr-smoke-runner"),
    JFR_PERFORMANCE_BUILDER_IMAGE("my-jfr-performance-runner"),
    JFR_PLAINTEXT_BUILDER_IMAGE("my-jfr-plaintext-runner"),
    HYPERFOIL("hyperfoil-container"),
    MONITOR_OFFSET_BUILDER_IMAGE("my-monitor-offset-runner"),
    FOR_SERIALIZATION_BUILDER_IMAGE("my-for-serialization-runner"),
    JDK_REFLECTIONS_BUILDER_IMAGE("my-jdkreflections-runner"),
    QUARKUS_BUILDER_IMAGE_MP_ORM_DBS_AWT("my-quarkus-mp-orm-dbs-awt-container"),
    NONE("NO_CONTAINER");

    public final String name;

    ContainerNames(String name) {
        this.name = name;
    }
}
