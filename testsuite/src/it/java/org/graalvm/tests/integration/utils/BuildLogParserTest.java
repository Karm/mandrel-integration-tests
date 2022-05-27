package org.graalvm.tests.integration.utils;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import static org.graalvm.tests.integration.utils.BuildLogParser.mapToJSON;
import static org.graalvm.tests.integration.utils.BuildLogParser.parse;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("testing-testsuite")
public class BuildLogParserTest {

    @Test
    public void parserTest() throws IOException {

        final String log = ""
                + "[INFO] [io.quarkus.deployment.pkg.steps.NativeImageBuildStep] Running Quarkus native-image plugin on native-image 22.1.0.0-Final Mandrel Distribution (Java Version 17.0.3+7)\n"
                + "[INFO] [io.quarkus.deployment.pkg.steps.NativeImageBuildRunner] /home/karm/workspaceRH/mandrel-release/CPU/mandrel-java17-22.1.0.0-Final/bin/native-image -J-DCoordinatorEnvironmentBean.transactionStatusManagerEnable=false -J-Dcom.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize=true -J-Dsun.nio.ch.maxUpdateArraySize=100 -J-Djava.util.logging.manager=org.jboss.logmanager.LogManager -J-Dvertx.logger-delegate-factory-class-name=io.quarkus.vertx.core.runtime.VertxLogDelegateFactory -J-Dvertx.disableDnsResolver=true -J-Dio.netty.leakDetection.level=DISABLED -J-Dio.netty.allocator.maxOrder=3 -J-Duser.language=en -J-Duser.country=US -J-Dfile.encoding=UTF-8 -H:-ParseOnce -J--add-exports=java.security.jgss/sun.security.krb5=ALL-UNNAMED -J--add-opens=java.base/java.text=ALL-UNNAMED -H:InitialCollectionPolicy=com.oracle.svm.core.genscavenge.CollectionPolicy\\$BySpaceAndTime -H:+JNI -H:+AllowFoldMethods -J-Djava.awt.headless=true -H:FallbackThreshold=0 --link-at-build-time -H:+ReportExceptionStackTraces -J-Xmx7g -H:-AddAllCharsets -H:EnableURLProtocols=http,https -H:NativeLinkerOption=-no-pie -H:-UseServiceLoaderFeature -H:+StackTrace qu-queue-service-1.0.0-SNAPSHOT-runner -jar qu-queue-service-1.0.0-SNAPSHOT-runner.jar\n"
                + "========================================================================================================================\n"
                + "GraalVM Native Image: Generating 'qu-queue-service-1.0.0-SNAPSHOT-runner' (executable)...\n"
                + "========================================================================================================================\n"
                + "[1/7] Initializing...                                                                                    (7.3s @ 0.23GB)\n"
                + " Version info: 'GraalVM 22.1.0.0-Final Java 17 Mandrel Distribution'\n"
                + " C compiler: gcc (redhat, x86_64, 8.5.0)\n"
                + " Garbage collector: Serial GC\n"
                + " 8 user-provided feature(s)\n"
                + "  - io.quarkus.caffeine.runtime.graal.CacheConstructorsAutofeature\n"
                + "  - io.quarkus.hibernate.orm.runtime.graal.DisableLoggingAutoFeature\n"
                + "  - io.quarkus.jdbc.postgresql.runtime.graal.SQLXMLFeature\n"
                + "  - io.quarkus.runner.AutoFeature\n"
                + "  - io.quarkus.runtime.graal.DisableLoggingAutoFeature\n"
                + "  - io.quarkus.runtime.graal.ResourcesFeature\n"
                + "  - org.hibernate.graalvm.internal.GraalVMStaticAutofeature\n"
                + "  - org.hibernate.graalvm.internal.QueryParsingSupport\n"
                + "[2/7] Performing analysis...  [**********]                                                              (73.3s @ 3.12GB)\n"
                + "  25,020 (92.12%) of 27,161 classes reachable\n"
                + "  42,138 (64.32%) of 65,518 fields reachable\n"
                + " 137,756 (60.29%) of 228,498 methods reachable\n"
                + "   1,496 classes, 1,665 fields, and 11,499 methods registered for reflection\n"
                + "      65 classes,    77 fields, and    55 methods registered for JNI access\n"
                + "[3/7] Building universe...                                                                               (9.8s @ 2.53GB)\n"
                + "[4/7] Parsing methods...      [****]                                                                    (14.8s @ 2.46GB)\n"
                + "[5/7] Inlining methods...     [*****]                                                                   (16.1s @ 2.71GB)\n"
                + "[6/7] Compiling methods...    [*******]                                                                 (54.7s @ 3.93GB)\n"
                + "[7/7] Creating image...                                                                                 (11.3s @ 4.15GB)\n"
                + "  54.88MB (42.32%) for code area:   92,869 compilation units\n"
                + "  60.61MB (46.74%) for image heap:  17,698 classes and 641,980 objects\n"
                + "  14.18MB (10.94%) for other data\n"
                + " 129.68MB in total\n"
                + "------------------------------------------------------------------------------------------------------------------------\n"
                + "Top 10 packages in code area:                               Top 10 object types in image heap:\n"
                + "   2.92MB jdk.proxy3                                          11.90MB byte[] for code metadata\n"
                + "   2.59MB com.oracle.svm.core.reflect                         11.16MB byte[] for general heap data\n"
                + "   2.04MB liquibase.pro.packaged                               6.27MB java.lang.Class\n"
                + "   1.82MB sun.security.ssl                                     5.50MB byte[] for java.lang.String\n"
                + "   1.17MB java.util                                            5.37MB java.lang.String\n"
                + " 763.31KB com.sun.crypto.provider                              3.79MB java.lang.reflect.Method\n"
                + " 746.55KB io.quarkus.runtime.generated                         2.10MB com.oracle.svm.core.hub.DynamicHubCompanion\n"
                + " 622.12KB com.oracle.svm.core.code                             1.38MB byte[] for reflection metadata\n"
                + " 614.87KB liquibase.command.core                               1.08MB java.lang.String[]\n"
                + " 576.33KB org.hibernate.hql.internal.antlr                   851.44KB java.util.HashMap$Node\n"
                + "      ... 1135 additional packages                                ... 5451 additional object types\n"
                + "                                           (use GraalVM Dashboard to see all)\n"
                + "------------------------------------------------------------------------------------------------------------------------\n"
                + "                       24.7s (12.4% of total time) in 112 24.7s (12.4% of total time) in 112 GCs | Peak RSS: 6.44GB | CPU load: 6.61GCs | Peak RSS: 6.44GB | CPU load: 6.61\n"
                + "------------------------------------------------------------------------------------------------------------------------\n"
                + "Produced artifacts:\n"
                + " /some/path/to/TEST-MARKER/qu-queue-service-1.0.0-SNAPSHOT-runner (executable)\n"
                + " /some/path/to/somewhere/qu-queue-service-1.0.0-SNAPSHOT-runner.build_artifacts.txt\n"
                + "========================================================================================================================\n"
                + "Finished generating 'qu-queue-service-1.0.0-SNAPSHOT-runner' in 3m 17s.\n"
                + "";
        final File f = File.createTempFile("build-log-test", "txt");
        Files.writeString(f.toPath(), log, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        final String expected = ("{\n"
                + "  \"buildTimeS\": 197,\n"
                + "  \"classes\": 27161,\n"
                + "  \"classesForJNIAccess\": 65,\n"
                + "  \"classesForReflection\": 1496,\n"
                + "  \"classesReachable\": 25020,\n"
                + "  \"executablePath\": \"/some/path/to/somewhere/qu-queue-service-1.0.0-SNAPSHOT-runner\",\n"
                + "  \"fields\": 65518,\n"
                + "  \"fieldsForJNIAccess\": 77,\n"
                + "  \"fieldsForReflection\": 1665,\n"
                + "  \"fieldsReachable\": 42138,\n"
                + "  \"jdkVersion\": \"17.0.3+7\",\n"
                + "  \"mandrelVersion\": \"22.1.0.0-Final\",\n"
                + "  \"methods\": 228498,\n"
                + "  \"methodsForJNIAccess\": 55,\n"
                + "  \"methodsForReflection\": 11499,\n"
                + "  \"methodsReachable\": 137756,\n"
                + "  \"numberOfGC\": 112,\n"
                + "  \"peakRSSMB\": 6595,\n"
                + "  \"timeInGCS\": 25\n"
                + "}"
        ).replaceAll("\\s*", "");
        final String j = mapToJSON(parse(f.toPath()));
        //System.out.println(j);
        assertEquals(expected, j, "Parser is off.");
    }

}
