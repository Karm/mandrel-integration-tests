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

import org.graalvm.home.Version;
import org.graalvm.tests.integration.utils.versions.UsedVersion;

import java.util.regex.Pattern;

/**
 * Whitelists errors in log files.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public enum WhitelistLogLines {

    // This is appended to all undermentioned listings
    ALL {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{
                    // Harmless download, e.g.
                    // Downloaded from central: https://repo.maven.apache.org/maven2/org/apache/maven/maven-error-diagnostics...
                    Pattern.compile(".*maven-error-diagnostics.*"),
                    // Download https://repo.maven.apache.org/maven2/com/google/errorprone
                    Pattern.compile(".*com/google/errorprone/error_prone.*"),
                    Pattern.compile(".*com.google.errorprone:error_prone_annotations:jar:sources.*"),
                    // JDK:
                    Pattern.compile("WARNING.* reflective access.*"),
                    Pattern.compile("WARNING: All illegal access operations.*"),
                    Pattern.compile("WARNING: Please consider reporting this to the maintainers of com.google.inject.internal.cglib.*"),
                    Pattern.compile("WARNING: Please consider reporting this to the maintainers of com.fasterxml.jackson.databind.util.*"),
                    // JAVA_HOME (e.g. 17) != GRAALVM_HOME (e.g. 21)
                    Pattern.compile(".*system modules path not set in conjunction with -source .*")
            };
        }
    },
    NONE {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{};
        }
    },
    IMAGEIO {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{
                    // org.jfree.jfreesvg reflectively accesses com.orsoncharts.Chart3DHints which is not on the classpath
                    Pattern.compile("Warning: Could not resolve .*com.orsoncharts.Chart3DHints for reflection configuration. Reason: java.lang.ClassNotFoundException: com.orsoncharts.Chart3DHints."),
                    // The java agent erroneously produces a reflection config mentioning this constructor, which doesn't exist
                    Pattern.compile("Warning: Method sun\\.security\\.provider\\.NativePRNG\\.<init>\\(SecureRandomParameters\\) not found.")
            };
        }
    },
    IMAGEIO_BUILDER_IMAGE {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{
                    // Dnf warnings...
                    Pattern.compile(".*librhsm-WARNING.*"),
                    // Podman with cgroupv2 on RHEL 9 intermittently spits out this message to no apparent effect on our tests
                    Pattern.compile(".*time=.*level=warning.*msg=.*S.gpg-agent.*since it is a socket.*"),
                    // org.jfree.jfreesvg reflectively accesses com.orsoncharts.Chart3DHints which is not on the classpath
                    Pattern.compile("Warning: Could not resolve .*com.orsoncharts.Chart3DHints for reflection configuration. Reason: java.lang.ClassNotFoundException: com.orsoncharts.Chart3DHints."),
                    // The java agent erroneously produces a reflection config mentioning this constructor, which doesn't exist
                    Pattern.compile("Warning: Method sun\\.security\\.provider\\.NativePRNG\\.<init>\\(SecureRandomParameters\\) not found.")
            };
        }
    },
    QUARKUS_FULL_MICROPROFILE {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{
                    // Well, the RestClient demo probably should do some cleanup before shutdown...?
                    Pattern.compile(".*Closing a class org.jboss.resteasy.client.*"),
                    // Unused argument on new Graal; Quarkus uses it for backward compatibility.
                    Pattern.compile(".*Ignoring server-mode native-image argument --no-server.*"),
                    // Windows specific warning
                    Pattern.compile(".*oracle/graal/issues/2387.*"),
                    // Windows specific warning, O.K.
                    Pattern.compile(".*objcopy executable not found in PATH.*"),
                    Pattern.compile(".*That will result in a larger native image.*"),
                    Pattern.compile(".*That also means that resulting native executable is larger.*"),
                    Pattern.compile(".*contain duplicate files, e.g. javax/activation/ActivationDataFlavor.class.*"),
                    Pattern.compile(".*contain duplicate files, e.g. javax/servlet/http/HttpUtils.class.*"),
                    Pattern.compile(".*contain duplicate files, e.g. javax/annotation/ManagedBean.class.*"),
                    // Jaeger Opentracing initialization, Quarkus 2.x specific issue.
                    Pattern.compile(".*io.jaegertracing.internal.exceptions.SenderException:.*"),
                    // Jaeger Opentracing, Quarkus 2.x specific issue.
                    Pattern.compile(".*MpPublisherMessageBodyReader is already registered.*"),
                    // Params quirk, harmless
                    Pattern.compile(".*Unrecognized configuration key.*quarkus.home.*was provided.*"),
                    Pattern.compile(".*Unrecognized configuration key.*quarkus.version.*was provided.*"),
                    // GitHub workflow Windows executor flaw:
                    Pattern.compile(".*Unable to make the Vert.x cache directory.*"),
                    // Not sure, definitely not Mandrel related though
                    Pattern.compile(".*xml-apis:xml-apis:jar:.* has been relocated to xml-apis:xml-apis:jar:.*"),
                    Pattern.compile(".*io.quarkus:quarkus-vertx-web:jar:.* has been relocated to io.quarkus:quarkus-reactive-routes:jar:.*"),
                    // GC warning thrown in GraalVM >= 22.0 under constraint environment (e.g. CI) see https://github.com/Karm/mandrel-integration-tests/issues/68
                    Pattern.compile(".*GC warning: [0-9.]+s spent in [0-9]+ GCs during the last stage, taking up [0-9]+.[0-9]+% of the time.*"),
                    // https://github.com/quarkusio/quarkus/issues/30508#issuecomment-1402066131
                    Pattern.compile(".*Warning: Could not register io.netty.* queryAllPublicMethods for reflection.*"),
                    // netty 4 which doesn't have the relevant native config in the lib. See https://github.com/netty/netty/pull/13596
                    Pattern.compile(".*Warning: The option '-H:ReflectionConfigurationResources=META-INF/native-image/io\\.netty/netty-transport/reflection-config\\.json' is experimental and must be enabled via.*"),
                    Pattern.compile(".*Warning: Please re-evaluate whether any experimental option is required, and either remove or unlock it\\..*"),
                    // We don't run any OpenTracing collector point for simplicity, hence the exception. Q 3.6.0+ specific.
                    Pattern.compile(".*Failed to export spans. The request could not be executed. Full error message: Connection refused:.*")
            };
        }
    },
    DEBUG_QUARKUS_BUILDER_IMAGE_VERTX {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{
                    // Container image build
                    Pattern.compile(".*lib.*-WARNING .*"),
                    // Podman with cgroupv2 on RHEL 9 intermittently spits out this message to no apparent effect on our tests
                    Pattern.compile(".*level=error msg=\"Cannot get exit code: died not found: unable to find event\".*"),
                    Pattern.compile(".*time=.*level=warning.*msg=.*S.gpg-agent.*since it is a socket.*"),
                    // Params quirk, harmless
                    Pattern.compile(".*Unrecognized configuration key.*quarkus.home.*was provided.*"),
                    Pattern.compile(".*Unrecognized configuration key.*quarkus.version.*was provided.*"),
                    // Specific Podman version warning about the way we start gdb in an already running container; harmless.
                    Pattern.compile(".*The --tty and --interactive flags might not work properly.*"),
                    // Expected part of the app log
                    Pattern.compile(".*'table \"fruits\" does not exist, skipping'.*"),
                    // Not sure, definitely not Mandrel related though
                    Pattern.compile(".*xml-apis:xml-apis:jar:.* has been relocated to xml-apis:xml-apis:jar:.*"),
                    Pattern.compile(".*io.quarkus:quarkus-vertx-web:jar:.* has been relocated to io.quarkus:quarkus-reactive-routes:jar:.*"),
                    Pattern.compile(".*The quarkus-resteasy-mutiny extension is deprecated. Switch to RESTEasy Reactive instead."),
                    // https://github.com/quarkusio/quarkus/issues/30508#issuecomment-1402066131
                    Pattern.compile(".*Warning: Could not register io.netty.* queryAllPublicMethods for reflection.*"),
                    // https://github.com/quarkusio/quarkus/blob/2.13.7.Final/core/deployment/src/main/java/io/quarkus/deployment/OutputFilter.java#L27
                    Pattern.compile(".*io.quarkus.deployment.OutputFilter.*Stream is closed, ignoring and trying to continue.*"),
            };
        }
    },
    HELIDON_QUICKSTART_SE {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{
                    // Experimental options not being unlocked, produces warnings, yet it's driven by the helidon-maven-plugin
                    Pattern.compile(".*The option '.*' is experimental and must be enabled via '-H:\\+UnlockExperimentalVMOptions' in the future.*"),
                    Pattern.compile(".*Please re-evaluate whether any experimental option is required, and either remove or unlock it.*"),
                    // Unused argument on new Graal
                    Pattern.compile(".*Ignoring server-mode native-image argument --no-server.*"),
                    // --allow-incomplete-classpath not available in new GraalVM https://github.com/Karm/mandrel-integration-tests/issues/76
                    Pattern.compile(".*Using a deprecated option --allow-incomplete-classpath from" +
                            ".*helidon-webserver-2.2.2.jar.*" +
                            "Allowing an incomplete classpath is now the default. " +
                            "Use --link-at-build-time to report linking errors at image build time for a class or package.*")
            };
        }
    },
    QUARKUS_BUILDER_IMAGE_ENCODING {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{
                    // Podman with cgroupv2 on RHEL 9 intermittently spits out this message to no apparent effect on our tests
                    Pattern.compile(".*level=error msg=\"Cannot get exit code: died not found: unable to find event\".*"),
                    Pattern.compile(".*time=.*level=warning.*msg=.*S.gpg-agent.*since it is a socket.*"),
                    // Params quirk, harmless
                    Pattern.compile(".*Unrecognized configuration key.*quarkus.home.*was provided.*"),
                    Pattern.compile(".*Unrecognized configuration key.*quarkus.version.*was provided.*"),
                    // https://github.com/quarkusio/quarkus/issues/30508#issuecomment-1402066131
                    Pattern.compile(".*Warning: Could not register io.netty.* queryAllPublicMethods for reflection.*"),
                    // https://github.com/quarkusio/quarkus/blob/2.13.7.Final/core/deployment/src/main/java/io/quarkus/deployment/OutputFilter.java#L27
                    Pattern.compile(".*io.quarkus.deployment.OutputFilter.*Stream is closed, ignoring and trying to continue.*"),
            };
        }
    },
    JFR {
        @Override
        public Pattern[] get(boolean inContainer) {
            if (UsedVersion.getVersion(inContainer).compareTo(Version.create(22, 3, 0)) <= 0) {
                return new Pattern[]{
                        // https://github.com/oracle/graal/issues/3636
                        Pattern.compile(".*Unable to commit. Requested size [0-9]* too large.*"),
                        // https://github.com/oracle/graal/issues/4431
                        Pattern.compile(".*Exception occurred when setting value \"150/s\" for class jdk.jfr.internal.Control.*")};
            } else {
                return new Pattern[]{
                        // https://github.com/oracle/graal/issues/3636
                        Pattern.compile(".*Unable to commit. Requested size [0-9]* too large.*"),
                        // Hyperfoil spits this on GHA CI, cannot reproduce locally
                        Pattern.compile(".*ControllerVerticle] Uncaught error: java.lang.NullPointerException.*"),
                        // For some reason, Podman spits this when terminating Hyperfoil containers
                        Pattern.compile(".*Could not retrieve exit code from event: died not found: unable to find event.*"),
                        // Again Hyperfoil and Podman. There might be something odd with stopping those agents? Not a Quaruks/Mandrel issue.
                        Pattern.compile(".*Waiting for container .* getting exit code of container .* from DB: no such exit code \\(container in state running\\).*"),
                        // Quarkus 3.x intermittently with JDK 20 based build...
                        Pattern.compile(".*io.net.boo.ServerBootstrap.*Failed to register an accepted channel:.*"),
                        // Perf test uses netty 4 which doesn't have the relevant native config in the lib. See https://github.com/netty/netty/pull/13596
                        Pattern.compile(".*Warning: The option '-H:ReflectionConfigurationResources=META-INF/native-image/io\\.netty/netty-transport/reflection-config\\.json' is experimental and must be enabled via.*"),
                        Pattern.compile(".*Warning: Please re-evaluate whether any experimental option is required, and either remove or unlock it\\..*")
                };
            }
        }
    },
    RESLOCATIONS {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{
                    Pattern.compile(".*com\\.sun\\.imageio\\.plugins\\.common.*is internal proprietary API and may be removed in a future release.*")
            };
        }
    };

    public abstract Pattern[] get(boolean inContainer);

}
