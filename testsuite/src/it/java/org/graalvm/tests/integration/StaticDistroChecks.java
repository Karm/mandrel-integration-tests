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
package org.graalvm.tests.integration;

import org.apache.commons.io.FileUtils;
import org.graalvm.tests.integration.utils.versions.IfMandrelVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.graalvm.tests.integration.utils.Commands.IS_THIS_WINDOWS;
import static org.graalvm.tests.integration.utils.Commands.runCommand;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Examines the Mandrel distro itself.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Tag("reproducers")
public class StaticDistroChecks {

    @Test
    @IfMandrelVersion(min = "22.2")
    public void spaceInPath_22(TestInfo testInfo) throws IOException {
        spaceInPath(testInfo);
    }

    @Test
    @IfMandrelVersion(min = "21.3.3", max = "21.3.999")
    public void spaceInPath_21(TestInfo testInfo) throws IOException {
        spaceInPath(testInfo);
    }

    public void spaceInPath(TestInfo testInfo) throws IOException {
        final Path graalHomeSpace = Path.of(System.getProperty("java.io.tmpdir"), "there are spaces");
        try {
            final String home = System.getenv().get("GRAALVM_HOME");
            assertNotNull(home, "GRAALVM_HOME must be set.");
            final Path graalHome = Path.of(home.replaceAll(File.separator + "+$", ""));
            assertTrue(Files.exists(Path.of(graalHome.toString(), "bin")), "There is something wrong with GRAALVM_HOME.");
            final File graalHomeSpaceBin = new File(graalHomeSpace.toFile(), "bin");
            runCommand(IS_THIS_WINDOWS ?
                    List.of("xcopy", graalHome.toString(), graalHomeSpace.toString(), "/E", "/H", "/B", "/Q", "/I") :
                    List.of("cp", graalHome.toString(), graalHomeSpace.toString(), "-Rpd")
            );
            final String result = runCommand(IS_THIS_WINDOWS ?
                            List.of("cmd", "/C", "native-image", "--version") :
                            List.of("sh", "native-image", "--version")
                    , graalHomeSpaceBin,
                    Map.of("GRAALVM_HOME", graalHomeSpace.toString(),
                            "PATH", graalHomeSpaceBin.getAbsolutePath() + File.pathSeparator + System.getenv("PATH")));
            final Pattern p = Pattern.compile("(?:GraalVM|native-image).*Java Version.*", Pattern.DOTALL);
            assertTrue(p.matcher(result).matches(), "Correct --version output expected. Got: `" + result + "', " +
                    "possibly https://github.com/oracle/graal/pull/4635");
        } finally {
            if (Files.exists(graalHomeSpace)) {
                FileUtils.deleteDirectory(graalHomeSpace.toFile());
            }
        }
    }
}
