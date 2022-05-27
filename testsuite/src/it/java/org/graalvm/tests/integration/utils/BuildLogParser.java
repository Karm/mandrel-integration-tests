package org.graalvm.tests.integration.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Quarkus specific due to version parsing
 */
public class BuildLogParser {

    public enum Attributes {
        NATIVE_IMAGE_VERSION(Pattern.compile(
                ".*Running Quarkus.*(?:GraalVM|native-image)(?: Version)? (?<mversion>[^ ]*).*Java Version (?<jversion>[^)]+).*")),
        CLASSES_REACHABLE(Pattern.compile(
                "\\s+(?<reachable>[\\d,]+)\\s+\\(.*%\\)\\s+of\\s+(?<classes>[\\d,]+)\\s+classes reachable.*")),
        FIELDS_REACHABLE(Pattern.compile(
                "\\s+(?<reachable>[\\d,]+)\\s+\\(.*%\\)\\s+of\\s+(?<fields>[\\d,]+)\\s+fields reachable.*")),
        METHODS_REACHABLE(Pattern.compile(
                "\\s+(?<reachable>[\\d,]+)\\s+\\(.*%\\)\\s+of\\s+(?<methods>[\\d,]+)\\s+methods reachable.*")),
        REFLECTION(Pattern.compile(
                "\\s+(?<classes>[\\d,]+)\\s+classes,\\s+(?<fields>[\\d,]+)\\s+fields, and\\s+(?<methods>[\\d,]+)\\s+methods registered for reflection.*")),
        JNI(Pattern.compile(
                "\\s+(?<classes>[\\d,]+)\\s+classes,\\s+(?<fields>[\\d,]+)\\s+fields, and\\s+(?<methods>[\\d,]+)\\s+methods registered for JNI access.*")),
        GC_RSS_STATS(Pattern.compile(
                "\\s+(?<time>[\\d\\.]+)(?<timeunit>[^ ]+)\\s+\\(.* of total time\\) in\\s+(?<gcs>\\d+) GCs \\| Peak RSS: (?<rss>[\\d\\.]+)(?<rssunit>[^ ]+) \\| CPU load:.*")),
        BUILD_TIME(Pattern.compile(
                ".*Finished generating .* in (?<minutes>\\d+)m (?<seconds>\\d+)s.*")),
        EXECUTABLE(Pattern.compile("\\s+(?<path>[^ ]+) \\(executable\\)$"));
        public final Pattern pattern;

        Attributes(Pattern pattern) {
            this.pattern = pattern;
        }
    }

    public static Map<String, String> parse(Path buildLog) throws IOException {
        final Map<String, String> data = new TreeMap<>();
        try (Scanner sc = new Scanner(buildLog, UTF_8)) {
            while (sc.hasNextLine()) {
                final String line = sc.nextLine();
                Matcher m = Attributes.NATIVE_IMAGE_VERSION.pattern.matcher(line);
                if (m.matches()) {
                    data.put("mandrelVersion", m.group("mversion"));
                    data.put("jdkVersion", m.group("jversion"));
                    continue;
                }
                m = Attributes.CLASSES_REACHABLE.pattern.matcher(line);
                if (m.matches()) {
                    data.put("classesReachable", m.group("reachable").replace(",", ""));
                    data.put("classes", m.group("classes").replace(",", ""));
                    continue;
                }
                m = Attributes.FIELDS_REACHABLE.pattern.matcher(line);
                if (m.matches()) {
                    data.put("fieldsReachable", m.group("reachable").replace(",", ""));
                    data.put("fields", m.group("fields").replace(",", ""));
                    continue;
                }
                m = Attributes.METHODS_REACHABLE.pattern.matcher(line);
                if (m.matches()) {
                    data.put("methodsReachable", m.group("reachable").replace(",", ""));
                    data.put("methods", m.group("methods").replace(",", ""));
                    continue;
                }
                m = Attributes.REFLECTION.pattern.matcher(line);
                if (m.matches()) {
                    data.put("methodsForReflection", m.group("methods").replace(",", ""));
                    data.put("fieldsForReflection", m.group("fields").replace(",", ""));
                    data.put("classesForReflection", m.group("classes").replace(",", ""));
                    continue;
                }
                m = Attributes.JNI.pattern.matcher(line);
                if (m.matches()) {
                    data.put("methodsForJNIAccess", m.group("methods").replace(",", ""));
                    data.put("fieldsForJNIAccess", m.group("fields").replace(",", ""));
                    data.put("classesForJNIAccess", m.group("classes").replace(",", ""));
                    continue;
                }
                m = Attributes.GC_RSS_STATS.pattern.matcher(line);
                if (m.matches()) {
                    if ("s".equalsIgnoreCase(m.group("timeunit"))) {
                        data.put("timeInGCS", Long.toString(Math.round(Double.parseDouble(m.group("time")))));
                    } else {
                        throw new IllegalArgumentException("Unexpected unit `" + m.group("timeunit") + "'. Fix the parser.");
                    }
                    data.put("numberOfGC", m.group("gcs"));
                    if ("GB".equalsIgnoreCase(m.group("rssunit"))) {
                        data.put("peakRSSMB", Long.toString(Math.round(Double.parseDouble(m.group("rss")) * 1024)));
                    } else {
                        throw new IllegalArgumentException("Unexpected unit `" + m.group("rssunit") + "'. Fix the parser.");
                    }
                    continue;
                }
                m = Attributes.BUILD_TIME.pattern.matcher(line);
                if (m.matches()) {
                    int minutes = Integer.parseInt(m.group("minutes"));
                    int seconds = Integer.parseInt(m.group("seconds"));
                    data.put("buildTimeS", Integer.toString(minutes * 60 + seconds));
                    continue;
                }
                m = Attributes.EXECUTABLE.pattern.matcher(line);
                if (m.matches()) {
                    data.put("executablePath", m.group("path"));
                }
            }
        }
        return data;
    }

    public static String mapToJSON(Map<String, String> map) {
        final Pattern num = Pattern.compile("\\d+");
        final StringBuilder sb = new StringBuilder();
        sb.append("{");
        map.forEach((k, v) -> {
            sb.append("\"");
            sb.append(k);
            sb.append("\":");
            if (num.matcher(v).matches()) {
                sb.append(v);
            } else {
                sb.append("\"");
                sb.append(v);
                sb.append("\"");
            }
            sb.append(",");
        });
        sb.setLength(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }

}
