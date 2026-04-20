package ru.sibgatulinanton.os;

public class OSUtils {
    private static String osName;

    static {
        osName = System.getProperty("os.name").toLowerCase();
    }

    public static OSType getOperatingSystemType() {

        if (osName.contains("win")) {
            return OSType.WINDOWS;
        } else if (osName.contains("mac")) {
            return OSType.MACOS;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return OSType.LINUX;
        } else {
            return OSType.OTHER;
        }
    }

}
