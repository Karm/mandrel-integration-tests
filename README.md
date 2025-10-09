# Native image integration tests
Builds Native image executables for small applications based on some Native image capable
runtimes such as Quarkus and Helidon. It also provides a convenient way of building small test
apps for Native image blackbox testing.

## Prerequisites

The test suite (TS) expects you run Java 11+ and have ```ps``` program available on your
Linux/Mac and ```wmic``` (by default present) on your Windows system.

Some tests also require ```lsof``` and ```perf``` commands to be available on Linux.

Depending on the test suite configuration, you might need to have Podman or Docker installed.
For gdb tests, you need to have ```gdb``` installed. Attaching gdb in a container scenario might require sudo privileges.

Some JFR or performance sensitive tests might ask for sudo to e.g. clear OS cache or
to switch on/off Intel Turbo boost.

Native image builds also require you have the following packages installed:
* glibc-devel
* zlib-devel
* gcc

On Fedora/CentOS/RHEL they can be installed with:
```bash
dnf install glibc-devel zlib-devel gcc libstdc++-static
```
Note the package might be called `glibc-static` instead of `libstdc++-static`.

On Ubuntu-like systems with:
```bash
apt install gcc zlib1g-dev build-essential
```

## Usage

The basic usage that runs all tests:
```
mvn clean verify -Ptestsuite
```

One can fine-tune excluded test cases or tests with `excludeTags`, e.g. `-DexcludeTags=runtimes`
to exclude all `runtimes` tests or `-DexcludeTags=helidon` to exclude just one of them. 
You can also exclude everything and include just `reproducers` suite: `-DexcludeTags=all -DincludeTags=reproducers`

## Downloading a lot of data

While the testsuite itself doesn't have many dependencies, it downloads all that is needed
to build enclosed Quarkus and Helidon projects.

## RuntimesSmokeTest

The goal is to build and start applications with some real source code that actually
exercises some rudimentary business logic of selected libraries.

### Collect results:

There are several log files archived in `testsuite/target/archived-logs/` after runtimes test
execution. e.g.:

```
org.graalvm.tests.integration.RuntimesSmokeTest/quarkusFullMicroProfile/report.md
org.graalvm.tests.integration.RuntimesSmokeTest/quarkusFullMicroProfile/build-and-run.log
org.graalvm.tests.integration.RuntimesSmokeTest/quarkusFullMicroProfile/measurements.csv
```

`report.md` is human-readable description of what commands were executed and how much time and
memory was spent to get the expected output form the runtime's web server.
`measurements.csv` contains the same data in a machine friendly form.
Last but not least, `build-and-run.log` journals the whole history of all commands
and their output.

There is also an aggregated `testsuite/target/archived-logs/aggregated-report.md` describing all
the testsuite did.

## AppReproducersTest

This part of the test suite runs smaller apps that are not expected to offer a web server,
so just their stdout/stderr is checked.
e.g. a current example:

```
org.graalvm.tests.integration.AppReproducersTest/randomNumbersReinit/report.md
org.graalvm.tests.integration.AppReproducersTest/randomNumbersReinit/build-and-run.log
``` 

### Examples
#### Fails with GraalVM 19.3.1
```
λ set JAVA_HOME=C:\Program Files\graalvm-ce-java11-19.3.1
λ set GRAALVM_HOME=%JAVA_HOME%
λ set PATH=%JAVA_HOME%\bin\;C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build;%PATH%
λ vcvars64
λ mvn clean verify -Ptestsuite -Dtest=AppReproducersTest
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 56.521 s <<< FAILURE! - in org.graalvm.tests.integration.AppReproducersTest
[ERROR] randomNumbersReinit{TestInfo}  Time elapsed: 55.334 s  <<< FAILURE!
org.opentest4j.AssertionFailedError: There should have been 4 distinct lines in the log,showing 2 different pseudorandom sequences. The fact that there are less than 4 means the native imagewas not properly re-seeded. See https://github.com/oracle/graal/issues/2265. ==> expected: <4> but was: <2>
        at org.graalvm.tests.integration.AppReproducersTest.randomNumbersReinit(AppReproducersTest.java:122)
[ERROR] Failures:
[ERROR]   AppReproducersTest.randomNumbersReinit:122 There should have been 4 distinct lines in the log,showing 2 different pseudorandom sequences. The fact that there are less than 4 means the native imagewas not properly re-seeded. See https://github.com/oracle/graal/issues/2265. ==> expected: <4> but was: <2>
[INFO]
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
```

#### Passes with GraalVM 20.0+
```
λ set JAVA_HOME=C:\Program Files\graalvm-ce-java11-20.1.0
λ set GRAALVM_HOME=%JAVA_HOME%
λ set PATH=%JAVA_HOME%\bin\;C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build;%PATH%
λ vcvars64
λ mvn clean verify -Ptestsuite -Dtest=AppReproducersTest
[INFO] Running org.graalvm.tests.integration.AppReproducersTest
INFO  [o.g.t.i.AppReproducersTest] (randomNumbersReinit) Testing app: RANDOM_NUMBERS
INFO  [o.g.t.i.AppReproducersTest] (randomNumbersReinit) Running...#1
INFO  [o.g.t.i.AppReproducersTest] (randomNumbersReinit) Running...#2
INFO  [o.g.t.i.AppReproducersTest] (randomNumbersReinit) [UUID: 323a8f36-e196-4f45-a022-b3c42f39f1de, secureRandom: [0, 25, 41, 6, 43, 7, 41, 75, 66, 19], UUID: 778dc083-4513-4292-bf4d-e652f011f882, secureRandom: [93, 73, 23, 65, 53, 11, 44, 46, 13, 79]]
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 56.112 s - in org.graalvm.tests.integration.AppReproducersTest
```

## Logs and Whitelist

Logs are checked for error and warning messages. Expected error messages can be whitelisted
in [WhitelistLogLines.java](./testsuite/src/it/java/org/graalvm/tests/integration/utils/WhitelistLogLines.java).

## Thresholds properties

We need to switch on and off certain tests depending on native-image versions used,
on host vs. container and most importantly, based on Quarkus version.

We use method annotations for that, e.g. `@IfQuarkusVersion(min = "3.6.0")`,
or `@IfMandrelVersion(min = "23.0.0", inContainer = true)`.

We also use properties in text files to validate whether particular thresholds were crossed, e.g. whether the app used more RAM than expected or whether the measured time delta between JVM HotSpot mode and native-image mode was bigger than expected.

e.g.

```
linux.jvm.time.to.finish.threshold.ms=6924
linux.native.time.to.finish.threshold.ms=14525
```
Most thresholds are defined as absolute values. However, there are some thresholds that are percentages. These types of thresholds are used for tests where two versions are compared against eachother. This means the % difference between the versions must not exceed the defined threshold. Such cases look like the example below.

```
linux.diff_native.RSS.threshold.percent=35
linux.diff_native.executable.size.threshold.percent=20
```

The current `.conf` format enhances `.properties` format with the power of using the
annotation strings, see:

```
# Comments and empty lines are ignored
linux.jvm.time.to.finish.threshold.ms=6000
linux.native.time.to.finish.threshold.ms=14000
linux.executable.size.threshold.kB=79000

@IfQuarkusVersion(min ="2.7.0", max="3.0.0")
linux.jvm.time.to.finish.threshold.ms=5000
linux.native.time.to.finish.threshold.ms=13000
linux.executable.size.threshold.kB=75000

@IfQuarkusVersion(min ="3.5.0", max="3.5.999")
@IfMandrelVersion(min = "23.1.2", minJDK = "21.0.1" )
linux.jvm.time.to.finish.threshold.ms=6924
linux.native.time.to.finish.threshold.ms=14525
linux.executable.size.threshold.kB=79000

@IfQuarkusVersion(min ="3.6.0")
linux.jvm.time.to.finish.threshold.ms=6924
linux.native.time.to.finish.threshold.ms=14525
linux.executable.size.threshold.kB=79000

@IfMandrelVersion(min = "24", minJDK = "21.0.1" )
linux.executable.size.threshold.kB=90000
```

Properties are being added to a map top to bottom, overwriting their previous values
unless an `@If` constraint fails. If a condition fails, the following properties are
not added to the map until the next `@If` constraint is met.

If two `@If` constraints follow immediately one after the other, they both MUST be true
to process the following properties.

Take a look at [ThresholdsTest.java](./ThresholdsTest.java) and its `threshold-*.conf` test [files](../../../../../../../../test/resources/) for a comprehensive overview.

The parsing logic is compatible with plain `.properties` files as we have been using before,
i.e. any key-value pair where the value is interpreted as the long type.


**THIS IS NOT A PERFORMANCE TEST** The thresholds are in place only as a sanity check to make
sure an update to Native image did not make the application runtime to run way over the
expected benevolent values.

The measured values are simply compared to be less or equal to the set threshold.
One can overwrite the `threshold.conf` by using env variables or system properties
(in this order). All letter are capitalized and dot is replaced with underscore, e.g.

```
APPS_QUARKUS_FULL_MICROPROFILE_LINUX_TIME_TO_FIRST_OK_REQUEST_THRESHOLD_MS=35 mvn clean verify -Ptestsuite 
```

### Example failures

With a rather harsh threshold of 5ms:

```
org.opentest4j.AssertionFailedError: 
Application QUARKUS_FULL_MICROPROFILE took 27 ms to get the first OK request, 
which is over 5 ms threshold. ==> expected: <true> but was: <false>
        at org.graalvm.tests.integration.RuntimesSmokeTest.testRuntime(RuntimesSmokeTest.java:131)
        at org.graalvm.tests.integration.RuntimesSmokeTest.quarkusFullMicroProfile(RuntimesSmokeTest.java:147)
```

For logs checking, see an example failure before we whitelisted the particular warning:

```
[ERROR] Failures: 
[ERROR]   RuntimesSmokeTest.helidonHelloWorld:154->testRuntime:91 
  build-and-run.log log should not contain error or warning lines that are not whitelisted.
  See testsuite/target/archived-logs/org.graalvm.tests.integration.RuntimesSmokeTest/helidonHelloWorld/build-and-run.log
  and check these offending lines: 
    [WARNING] Discovered module-info.class. Shading will break its strong encapsulation.
```

**Happy testing!**
