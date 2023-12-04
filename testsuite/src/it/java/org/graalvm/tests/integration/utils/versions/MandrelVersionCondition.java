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

import org.apache.logging.log4j.util.Strings;
import org.graalvm.home.Version;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.AnnotatedElement;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.graalvm.tests.integration.utils.versions.UsedVersion.compareJDKVersion;
import static org.graalvm.tests.integration.utils.versions.UsedVersion.featureInterimUpdate;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled;
import static org.junit.platform.commons.support.AnnotationSupport.findAnnotation;

/**
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public class MandrelVersionCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED_BY_DEFAULT =
            enabled("@IfMandrelVersion is not present");

    private static final Pattern jdkPattern = Pattern.compile("(?<jfeature>[0-9]+)(\\.(?<jinterim>[0-9]*)\\.(?<jupdate>[0-9]*))?");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(
            ExtensionContext context) {
        AnnotatedElement element = context
                .getElement()
                .orElseThrow(IllegalStateException::new);
        return findAnnotation(element, IfMandrelVersion.class)
                .map(annotation -> disableIfVersionMismatch(annotation, element))
                .orElse(ENABLED_BY_DEFAULT);
    }

    private ConditionEvaluationResult disableIfVersionMismatch(IfMandrelVersion annotation, AnnotatedElement element) {
        final boolean jdkConstraintSatisfied = jdkConstraintSatisfied(annotation.minJDK(), annotation.maxJDK(), annotation.inContainer());
        final Version usedVersion = UsedVersion.getVersion(annotation.inContainer());
        final boolean mandrelConstraintSatisfied = mandrelConstraintSatisfied(usedVersion, annotation.min(), annotation.max());
        if (mandrelConstraintSatisfied && jdkConstraintSatisfied) {
            return enabled(format(
                    "%s is enabled as Mandrel version %s does satisfy constraints: min: %s, max: %s, minJDK: %s, maxJDK: %s",
                    element, usedVersion.toString(), annotation.min(), annotation.max(), annotation.minJDK(), annotation.maxJDK()));
        }
        return disabled(format(
                "%s is disabled as Mandrel version %s does not satisfy constraints: min: %s, max: %s, minJDK: %s, maxJDK: %s",
                element, usedVersion, annotation.min(), annotation.max(), annotation.minJDK(), annotation.maxJDK()));
    }

    public static boolean jdkConstraintSatisfied(final String minJDK, final String maxJDK, final boolean inContainer) {
        if (!Strings.isBlank(minJDK) || !Strings.isBlank(maxJDK)) {
            final int[] jdkVersion = new int[] {
                    UsedVersion.jdkFeature(inContainer),
                    UsedVersion.jdkInterim(inContainer),
                    UsedVersion.jdkUpdate(inContainer)
            };
            final int[] min = featureInterimUpdate(jdkPattern, minJDK, Integer.MIN_VALUE);
            final int[] max = featureInterimUpdate(jdkPattern, maxJDK, Integer.MAX_VALUE);
            return compareJDKVersion(jdkVersion, min) >= 0 && compareJDKVersion(jdkVersion, max) <= 0;
        } else {
            return true;
        }
    }

    public static boolean mandrelConstraintSatisfied(final Version usedVersion, final String min, final String max) {
        return (Strings.isBlank(min) || usedVersion.compareTo(Version.parse(min)) >= 0) &&
                (Strings.isBlank(max) || usedVersion.compareTo(Version.parse(max)) <= 0);
    }
}
