# Aux scripts
A collection of bash scripts that demonstrate how Runner Info and Image Stats of https://github.com/Karm/collector could be used, being it independently with Quarkus Integration Testsuite or with this Mandrel Integration Testsuite.

One can copy-paste relevant chunks into GitHub Actions or Jenkins jobs' DSL declarations as needed.

## One app, many variants

For instance, [run_host](./run_host.sh) script iterates over a matrix of Quarkus versions and Mandrel versions. In each iteration, it builds one particular Quarkus app, minimalistically patched so as it works with both old and new Quarkus. It reports build stats from all these iterations under the same runner info id.

## Many apps, one variant

[run_qts_host.sh](./run_qts_host.sh) runs Quarkus Integration Testsuite with one Quarkus version, using one local installation of native-image. When completed, particular build stats for each test application built during the test run are reported under the same runner info id.

## Many apps, many variants

Trivially the [run_qts_host.sh](./run_qts_host.sh) could be extended with an iteration over many Quarkus versions and Mandrel versions. Whether all those apps from all those runs should be reported under the same runner info id depends on the user's design.
