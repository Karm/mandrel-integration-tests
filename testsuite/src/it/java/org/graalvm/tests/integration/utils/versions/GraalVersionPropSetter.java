/*
 * Copyright (c) 2022, Red Hat Inc. All rights reserved.
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

import org.graalvm.home.Version;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class GraalVersionPropSetter implements AfterEachCallback, BeforeEachCallback {

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        final GraalVersionProperty annotation = extensionContext.getTestMethod().get().getAnnotation(GraalVersionProperty.class);
        System.clearProperty(GraalVersionProperty.NAME);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        final GraalVersionProperty annotation = extensionContext.getTestMethod().get().getAnnotation(GraalVersionProperty.class);
        final Version usedVersion = UsedVersion.getVersion(annotation.inContainer());
        System.setProperty(GraalVersionProperty.NAME, usedVersion.toString());
    }
}
