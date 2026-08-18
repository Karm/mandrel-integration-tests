package org.graalvm.tests.integration.utils;

public enum AuxiliaryOptions {
    ForeignAPISupport_24_2("<FFAPI>", "-H:+ForeignAPISupport");

    public final String token;
    public final String replacement;

    AuxiliaryOptions(String token, String replacement) {
        this.token = token;
        this.replacement = replacement;
    }
}
