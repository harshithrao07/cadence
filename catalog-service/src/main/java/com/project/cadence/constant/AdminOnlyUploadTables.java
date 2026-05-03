package com.project.cadence.constant;

import java.util.Set;

public final class AdminOnlyUploadTables {

    private AdminOnlyUploadTables() {} // prevent instantiation

    public static final Set<String> TABLES =
            Set.of("song", "artist", "record");
}
