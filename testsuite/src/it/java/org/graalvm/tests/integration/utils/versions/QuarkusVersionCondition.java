/*
 * Copyright (c) 2023, Red Hat Inc. All rights reserved.
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

import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.AnnotatedElement;

import static java.lang.String.format;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled;
import static org.junit.platform.commons.support.AnnotationSupport.findAnnotation;

public class QuarkusVersionCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED_BY_DEFAULT =
            enabled("@IfQuarkusVersion is not present");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(
            ExtensionContext context) {
        final AnnotatedElement element = context
                .getElement()
                .orElseThrow(IllegalStateException::new);
        return findAnnotation(element, IfQuarkusVersion.class)
                .map(annotation -> disableIfVersionMismatch(annotation, element))
                .orElse(ENABLED_BY_DEFAULT);
    }

    private ConditionEvaluationResult disableIfVersionMismatch(IfQuarkusVersion annotation, AnnotatedElement element) {
        // `new QuarkusVersion()` is used instead of `Commands.QUARKUS_VERSION` for the
        // sake of testability via `System.setProperty("QUARKUS_VERSION", "3.6.0")` at the cost
        // of re-evaluation the property with each IfQuarkusVersion evaluation.
        final QuarkusVersion usedVersion = new QuarkusVersion();
        if (quarkusConstraintSatisfied(usedVersion, annotation.min(), annotation.max())) {
            return enabled(format(
                    "%s is enabled as Quarkus version %s does satisfy constraints: min: %s, max: %s",
                    element, usedVersion, annotation.min(), annotation.max()));
        }
        return disabled(format(
                "%s is disabled as Quarkus version %s does not satisfy constraints: min: %s, max: %s",
                element, usedVersion, annotation.min(), annotation.max()));
    }

    public static boolean quarkusConstraintSatisfied(final QuarkusVersion usedVersion, final String min, final String max) {
        return (Strings.isBlank(min) || usedVersion.compareTo(new QuarkusVersion(min)) >= 0) &&
                (Strings.isBlank(max) || usedVersion.compareTo(new QuarkusVersion(max)) <= 0);
    }
}
