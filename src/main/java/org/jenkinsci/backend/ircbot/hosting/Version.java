package org.jenkinsci.backend.ircbot.hosting;

import java.util.NoSuchElementException;
import java.util.StringTokenizer;

public class Version implements Comparable {

    private final int major;
    private final int minor;
    private final int micro;
    private static final String SEPARATOR = ".";

    public static final Version EMPTY = new Version(0, 0, 0);

    public Version(int major, int minor, int micro) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        validate();
    }

    public Version(int major) {
        this(major, -1, -1);
    }

    public Version(int major, int minor) {
        this(major, minor, -1);
    }

    public Version(String version) {
        int major = -1;
        int minor = -1;
        int micro = -1;

        try {
            StringTokenizer st = new StringTokenizer(version, SEPARATOR, true);
            major = Integer.parseInt(st.nextToken());

            if (st.hasMoreTokens()) {
                st.nextToken(); // consume delimiter
                minor = Integer.parseInt(st.nextToken());

                if (st.hasMoreTokens()) {
                    st.nextToken(); // consume delimiter
                    micro = Integer.parseInt(st.nextToken());

                    if (st.hasMoreTokens()) {
                        throw new IllegalArgumentException("invalid format");
                    }
                }
            }
        } catch (NoSuchElementException e) {
            throw new IllegalArgumentException("invalid format"); 
        }

        this.major = major;
        this.minor = minor;
        this.micro = micro;
        validate();
    }

    private void validate() {
        if (major < 0) {
            throw new IllegalArgumentException("negative major");
        }

        if(major >= 0 && minor < 0 && micro >= 0) {
            throw new IllegalArgumentException("negative minor with micro provided");
        }
    }

    public static Version parse(String version) {
        if (version == null) {
            return EMPTY;
        }

        version = version.trim();
        if (version.length() == 0) {
            return EMPTY;
        }

        return new Version(version);
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor < 0 ? 0 : minor;
    }

    public int getMicro() {
        return micro < 0 ? 0 : micro;
    }

    public String toString() {
        if(major >= 0 && minor >= 0 && micro < 0) {
            return major + SEPARATOR + minor;
        } else if(major >= 0 && minor < 0) {
            return "" + major;
        }
        return major + SEPARATOR + minor + SEPARATOR + micro;
    }

    public int hashCode() {
        return (major << 24) + (minor << 16) + (micro << 8);
    }

    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (!(object instanceof Version)) {
            return false;
        }

        Version other = (Version) object;
        return (major == other.major) && (minor == other.minor)
                && (micro == other.micro);
    }

    public int compareTo(Object object) {
        if (object == this) {
            return 0;
        }

        int localMinor = minor < 0 ? 0 : minor;
        int localMicro = micro < 0 ? 0 : micro;

        Version other = (Version) object;

        int result = major - other.major;
        if (result != 0) {
            return result;
        }

        result = localMinor - other.getMinor();
        if (result != 0) {
            return result;
        }

        result = localMicro - other.getMicro();
        if (result != 0) {
            return result;
        }

        return 0;
    }
}