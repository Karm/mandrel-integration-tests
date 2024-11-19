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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.graalvm.tests.integration.utils.Commands.QUARKUS_VERSION;

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
                    Pattern.compile(".*com.google.errorprone.*"),
                    // JDK:
                    Pattern.compile(".*location of system modules is not set in conjunction with -source 11.*"),
                    Pattern.compile("WARNING.* reflective access.*"),
                    Pattern.compile("WARNING: All illegal access operations.*"),
                    Pattern.compile("WARNING: Please consider reporting this to the maintainers of com.google.inject.internal.cglib.*"),
                    Pattern.compile("WARNING: Please consider reporting this to the maintainers of com.fasterxml.jackson.databind.util.*"),
                    // JAVA_HOME (e.g. 17) != GRAALVM_HOME (e.g. 21)
                    Pattern.compile(".*system modules path not set in conjunction with -source .*"),
                    // It's accompanied by a list of all warnings, so this particular one can be white-listed globally
                    Pattern.compile(".*Warning: Please re-evaluate whether any experimental option is required, and either remove or unlock it\\..*"),
                    // Quarkus main 2024-03-21 deprecates this maven plugin directive
                    Pattern.compile(".*Configuration property 'quarkus.package.type' has been deprecated.*"),
                    // Microdnf complaining, benign
                    Pattern.compile(".*microdnf.*Found 0 entitlement certificates.*"),
                    // Podman, container image build
                    Pattern.compile(".*microdnf.*lib.*WARNING.*"),
                    // Podman with cgroupv2 on RHEL 9 intermittently spits out this message to no apparent effect on our tests
                    Pattern.compile(".*level=error msg=\"Cannot get exit code: died not found: unable to find event\".*"),
                    Pattern.compile(".*time=.*level=warning.*msg=.*S.gpg-agent.*since it is a socket.*"),
                    // Testcontainers, depends on local setup. Not our test issue.
                    Pattern.compile(".*Please ignore if you don't have images in an authenticated registry.*"),
                    // Common new Q versions
                    Pattern.compile(".*io.quarkus.narayana.jta.runtime.graal.DisableLoggingFeature.*"),
                    // Podman / Docker extension incompatibilities with Podman versions
                    Pattern.compile(".*Database JDBC URL \\[undefined/unknown\\].*"),
                    Pattern.compile(".*Database driver: undefined/unknown.*"),
                    Pattern.compile(".*Autocommit mode: undefined/unknown.*"),
                    Pattern.compile(".*Minimum pool size: undefined/unknown.*"),
                    Pattern.compile(".*Isolation level: <unknown>.*"),
                    Pattern.compile(".*Maximum pool size: undefined/unknown.*"),
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
                    Pattern.compile("Warning: Method sun\\.security\\.provider\\.NativePRNG\\.<init>\\(SecureRandomParameters\\) not found."),
                    // https://github.com/graalvm/mandrel/issues/760
                    Pattern.compile(".*Warning: Option 'DynamicProxyConfigurationResources' is deprecated.*"),
            };
        }
    },
    IMAGEIO_BUILDER_IMAGE {
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
    QUARKUS_FULL_MICROPROFILE {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{
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
                    // GC warning thrown in GraalVM >= 22.0 under constraint environment (e.g. CI) see https://github.com/Karm/mandrel-integration-tests/issues/68
                    Pattern.compile(".*GC warning: [0-9.]+s spent in [0-9]+ GCs during the last stage, taking up [0-9]+.[0-9]+% of the time.*"),
                    // https://github.com/quarkusio/quarkus/issues/30508#issuecomment-1402066131
                    Pattern.compile(".*Warning: Could not register io.netty.* queryAllPublicMethods for reflection.*"),
                    // netty 4 which doesn't have the relevant native config in the lib. See https://github.com/netty/netty/pull/13596
                    Pattern.compile(".*Warning: The option '-H:ReflectionConfigurationResources=META-INF/native-image/io\\.netty/netty-transport/reflection-config\\.json' is experimental and must be enabled via.*"),
                    // We don't run any OpenTracing collector point for simplicity, hence the exception. Q 3.6.0+ specific.
                    Pattern.compile(".*Failed to export spans. The request could not be executed. Full error message: Connection refused:.*"),
                    // https://github.com/quarkusio/quarkus/issues/39667
                    Pattern.compile(".*io.quarkus.security.runtime.SecurityIdentity.*"),
                    // OpenTelemetry
                    Pattern.compile(".*No BatchSpanProcessor delegate specified.*"),
                    Pattern.compile(".*Connection refused: .*:4317.*"),
                    Pattern.compile(".*The request could not be executed.*:4317.*"),
                    // MacOS https://github.com/quarkusio/quarkus/issues/40938
                    Pattern.compile(".*Can not find io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider.*"),
                    // Allow the quarkus main warning of older Mandrel releases
                    Pattern.compile(".*\\[WARNING\\] \\[io.quarkus.deployment.pkg.steps.NativeImageBuildStep\\] You are using an older version of GraalVM or Mandrel : 23\\.0.* Quarkus currently supports 23.1.* Please upgrade to this version\\..*"),
                    // Upstream GraalVM issue due to changed metadata format. See https://github.com/oracle/graal/issues/9057
                    // and https://github.com/oracle/graal/commit/5fc14c42fd8bbad0c8e661b4ebd8f96255f86e6b
                    Pattern.compile(".*Warning: Option 'DynamicProxyConfigurationResources' is deprecated and might be removed in a future release\\. Please refer to the GraalVM release notes.*"),
                    Pattern.compile(".*Warning: Option 'DynamicProxyConfigurationResources' is deprecated and might be removed in a future release: This can be caused by a proxy-config.json file in your META-INF directory.*"),
                    // Dependency sources plugin may produce this warning on some systems. See https://issues.apache.org/jira/browse/MNG-7706
                    Pattern.compile(".*\\[WARNING\\] Parameter 'local' is deprecated core expression; Avoid use of ArtifactRepository type\\. If you need access to local repository, switch to .* expression and get LRM from it instead\\..*"),
                    // quarkus-netty has brotli as a dependency and native image builds with JDK 24+ produce these warnings
                    Pattern.compile(".*WARNING: A restricted method in java.lang.System has been called.*"),
                    Pattern.compile(".*WARNING: java\\.lang\\.System::loadLibrary has been called by com\\.aayushatharva\\.brotli4j\\.Brotli4jLoader.*"),
                    Pattern.compile(".*WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module.*"),
                    Pattern.compile(".*WARNING: Restricted methods will be blocked in a future release unless native access is enabled.*"),
            };
        }
    },
    QUARKUS_MP_ORM_DBS_AWT {
        @Override
        public Pattern[] get(boolean inContainer) {
            final List<Pattern> p = new ArrayList<>();
            // Our config
            p.add(Pattern.compile(".*Unrecognized configuration key \"quarkus.version\".*"));
            // Testcontainers might not need it, depends on your system.
            p.add(Pattern.compile(".*Attempted to read Testcontainers configuration file.*"));
            p.add(Pattern.compile(".*does not support the reuse of containers.*"));
            // Ryuk can spit warning that is on its own line.
            p.add(Pattern.compile("^\\[WARNING\\][\\s\\t]*$"));
            // GC warning thrown in GraalVM >= 22.0 under constraint environment (e.g. CI)
            // see https://github.com/Karm/mandrel-integration-tests/issues/68
            p.add(Pattern.compile(".*GC warning: [0-9.]+s spent in [0-9]+ GCs during the last stage, taking up [0-9]+.[0-9]+% of the time.*"));
            // JUnit output
            p.add(Pattern.compile(".* Failures: 0, Errors: 0,.*"));
            // Docker image extension being mean
            p.add(Pattern.compile(".*Using executable podman within the quarkus-container-image-docker.*"));
            if (QUARKUS_VERSION.majorIs(3) || QUARKUS_VERSION.isSnapshot()) {
                // Testcontainers
                p.add(Pattern.compile(".*org.tes.uti.ResourceReaper.*"));
                // Benign JPA
                p.add(Pattern.compile(".*Unknown SEQUENCE: 'db[0-9].db[0-9]entity_SEQ'.*"));
                p.add(Pattern.compile(".*does not need to be specified explicitly using 'hibernate.dialect'.*"));
                p.add(Pattern.compile(".*DDL \"drop sequence db[0-9]entity_SEQ\".*"));
                p.add(Pattern.compile(".*sequence \"db[0-9]entity_seq\" does not exist, skipping.*"));
                p.add(Pattern.compile(".*table \"db[0-9]entity\" does not exist, skipping.*"));
                p.add(Pattern.compile(".*Unable to determine a database type for default datasource.*"));
                p.add(Pattern.compile(".*Warning Code: 0, SQLState: 00000.*"));
                // OpenTelemetry
                p.add(Pattern.compile(".*No BatchSpanProcessor delegate specified, no action taken.*"));
                p.add(Pattern.compile(".*Connection refused: .*:4317.*"));
                p.add(Pattern.compile(".*The request could not be executed.*:4317.*"));
                // Warnings about experimental options, caused by Quarkus
                p.add(Pattern.compile(".*The option '-H:ReflectionConfigurationResources=.*netty-transport/reflection-config.json' is experimental.*"));
                p.add(Pattern.compile(".*The option '-H:IncludeResourceBundles=yasson-messages' is experimental.*"));
                //p.add(Pattern.compile(".*The option '-H:ResourceConfigurationFiles=resource-config.json' is experimental.*"));
            } else if (QUARKUS_VERSION.majorIs(2)) {
                // Jaeger talks to nobody
                p.add(Pattern.compile(".*io.jaegertracing.internal.exceptions.SenderException.*"));
                // Benign JPA
                p.add(Pattern.compile(".*Warning Code: 0, SQLState: 00000.*"));
                p.add(Pattern.compile(".*table \"db[0-9]entity\" does not exist.*"));
                p.add(Pattern.compile(".*Unknown SEQUENCE: 'db[0-9].hibernate_sequence'.*"));
                p.add(Pattern.compile(".*Unable to determine a database type for default datasource.*"));
                p.add(Pattern.compile(".* sequence \"hibernate_sequence\" does not exist.*"));
                p.add(Pattern.compile(".*DDL \"drop sequence hibernate_sequence\" .*"));
            }
            return p.toArray(new Pattern[0]);
        }
    },
    DEBUG_QUARKUS_BUILDER_IMAGE_VERTX {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{
                    // Params quirk, harmless
                    Pattern.compile(".*Unrecognized configuration key.*quarkus.home.*was provided.*"),
                    Pattern.compile(".*Unrecognized configuration key.*quarkus.version.*was provided.*"),
                    // Specific Podman version warning about the way we start gdb in an already running container; harmless.
                    Pattern.compile(".*The --tty and --interactive flags might not work properly.*"),
                    // Expected part of the app log
                    Pattern.compile(".*'table \"fruits\" does not exist, skipping'.*"),
                    // Not sure, definitely not Mandrel related though
                    Pattern.compile(".*xml-apis:xml-apis:jar:.* has been relocated to xml-apis:xml-apis:jar:.*"),
                    // https://github.com/quarkusio/quarkus/issues/30508#issuecomment-1402066131
                    Pattern.compile(".*Warning: Could not register io.netty.* queryAllPublicMethods for reflection.*"),
                    // https://github.com/quarkusio/quarkus/blob/2.13.7.Final/core/deployment/src/main/java/io/quarkus/deployment/OutputFilter.java#L27
                    Pattern.compile(".*io.quarkus.deployment.OutputFilter.*Stream is closed, ignoring and trying to continue.*"),
                    // Deprecated/to be updated with Rest Easy Reactive
                    Pattern.compile(".*The option '-H:ReflectionConfigurationResources=META-INF/native-image/io.netty/netty-transport/reflection-config.json' is experimental.*"),
                    Pattern.compile(".*The option '-H:IncludeResourceBundles=yasson-messages' is experimental.*"),
            };
        }
    },
    HELIDON_QUICKSTART_SE {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{
                    // Experimental options not being unlocked, produces warnings, yet it's driven by the helidon-maven-plugin
                    Pattern.compile(".*The option '.*' is experimental and must be enabled via '-H:\\+UnlockExperimentalVMOptions' in the future.*"),
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
                    // Params quirk, harmless
                    Pattern.compile(".*Unrecognized configuration key.*quarkus.home.*was provided.*"),
                    Pattern.compile(".*Unrecognized configuration key.*quarkus.version.*was provided.*"),
                    // https://github.com/quarkusio/quarkus/issues/30508#issuecomment-1402066131
                    Pattern.compile(".*Warning: Could not register io.netty.* queryAllPublicMethods for reflection.*"),
                    // https://github.com/quarkusio/quarkus/blob/2.13.7.Final/core/deployment/src/main/java/io/quarkus/deployment/OutputFilter.java#L27
                    Pattern.compile(".*io.quarkus.deployment.OutputFilter.*Stream is closed, ignoring and trying to continue.*"),
                    // Perf test uses netty 4 which doesn't have the relevant native config in the lib. See https://github.com/netty/netty/pull/13596
                    Pattern.compile(".*Warning: The option '-H:ReflectionConfigurationResources=META-INF/native-image/io\\.netty/netty-transport/reflection-config\\.json' is experimental and must be enabled via.*"),
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
                        /* We don't support the OldObjectSample event or the JFR Deprecated events annotation yet.
                         * https://github.com/oracle/graal/pull/8057 intercepts calls to adjust settings related to
                         * such events and instead logs a warning specific to SubstrateVM.
                         * Allow list those log lines until they are supported.
                         */
                        Pattern.compile(".*@Deprecated JFR events, and leak profiling are not yet supported.*"),
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
                        // MacOS https://github.com/quarkusio/quarkus/issues/40938
                        Pattern.compile(".*Can not find io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider.*"),
                        // Upstream GraalVM issue due to changed metadata format. See https://github.com/oracle/graal/issues/9057
                        // and https://github.com/oracle/graal/commit/5fc14c42fd8bbad0c8e661b4ebd8f96255f86e6b
                        Pattern.compile(".*Warning: Option 'DynamicProxyConfigurationResources' is deprecated and might be removed in a future release\\. Please refer to the GraalVM release notes.*"),
                        Pattern.compile(".*Warning: Option 'DynamicProxyConfigurationResources' is deprecated and might be removed in a future release: This can be caused by a proxy-config.json file in your META-INF directory.*"),
                        // Allow the quarkus main warning of older Mandrel releases
                        Pattern.compile(".*\\[WARNING\\] \\[io.quarkus.deployment.pkg.steps.NativeImageBuildStep\\] You are using an older version of GraalVM or Mandrel : 23\\.0.* Quarkus currently supports 23.1.* Please upgrade to this version\\..*"),
                        // quarkus-netty has brotli as a dependency and native image builds with JDK 24+ produce these warnings
                        Pattern.compile(".*WARNING: A restricted method in java.lang.System has been called.*"),
                        Pattern.compile(".*WARNING: java\\.lang\\.System::loadLibrary has been called by com\\.aayushatharva\\.brotli4j\\.Brotli4jLoader.*"),
                        Pattern.compile(".*WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module.*"),
                        Pattern.compile(".*WARNING: Restricted methods will be blocked in a future release unless native access is enabled.*"),
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
    },
    MONITOR_OFFSET {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{
                    Pattern.compile(".*Failed generating.*"),
                    Pattern.compile(".*The build process encountered an unexpected error.*"),
                    Pattern.compile(".*monitor_field_offset.Main480 has an invalid monitor field offset.*"),
                    Pattern.compile(".*error report at:.*"),
            };
        }
    },
    FOR_SERIALIZATION {
        @Override
        public Pattern[] get(boolean inContainer) {
            return new Pattern[]{
                    Pattern.compile(".*sun.reflect.ReflectionFactory is internal proprietary API.*")
            };
        }
    };

    public abstract Pattern[] get(boolean inContainer);

}
