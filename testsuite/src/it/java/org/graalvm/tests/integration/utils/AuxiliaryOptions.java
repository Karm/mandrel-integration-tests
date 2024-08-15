package org.graalvm.tests.integration.utils;

public enum AuxiliaryOptions {
    UnlockExperimentalVMOptions_23_1("<DEBUG_FLAGS_23_1_a>", "-H:+UnlockExperimentalVMOptions"),
    LockExperimentalVMOptions_23_1("<DEBUG_FLAGS_23_1_b>", "-H:-UnlockExperimentalVMOptions"),
    TrackNodeSourcePosition_23_0("<DEBUG_FLAGS_23_0_a>", "-H:+TrackNodeSourcePosition"),
    DebugCodeInfoUseSourceMappings_23_0("<DEBUG_FLAGS_23_0_b>", "-H:+DebugCodeInfoUseSourceMappings"),
    OmitInlinedMethodDebugLineInfo_23_0("<DEBUG_FLAGS_23_0_c>", "-H:+OmitInlinedMethodDebugLineInfo"),
    ForeignAPISupport_24_2("<FFAPI>", "-H:+ForeignAPISupport");

    public final String token;
    public final String replacement;

    AuxiliaryOptions(String token, String replacement) {
        this.token = token;
        this.replacement = replacement;
    }
}
