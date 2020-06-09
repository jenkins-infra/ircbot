package org.jenkinsci.backend.ircbot.hosting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class VersionTest {
    @Test
    public void basics() throws Exception {
        Version ver = new Version(1);
        assertEquals(1, ver.getMajor());
        assertEquals(0, ver.getMinor());
        assertEquals(0, ver.getMicro());

        ver = new Version(1, 2);
        assertEquals(1, ver.getMajor());
        assertEquals(2, ver.getMinor());
        assertEquals(0, ver.getMicro());

        ver = new Version(1,2,3);
        assertEquals(1, ver.getMajor());
        assertEquals(2, ver.getMinor());
        assertEquals(3, ver.getMicro());

        try {
            ver = new Version(1, -1, 3);
            fail("Should have thrown an exception");
        } catch(IllegalArgumentException e) {
            // do nothing
        }

        ver = new Version("1");
        assertEquals(1, ver.getMajor());
        assertEquals(0, ver.getMinor());
        assertEquals(0, ver.getMicro());

        ver = new Version("1.2");
        assertEquals(1, ver.getMajor());
        assertEquals(2, ver.getMinor());
        assertEquals(0, ver.getMicro());

        ver = new Version("1.2.3");
        assertEquals(1, ver.getMajor());
        assertEquals(2, ver.getMinor());
        assertEquals(3, ver.getMicro());

        try {
            ver = new Version("1.2.3.4");
            fail("Should have thrown an exception");
        } catch(IllegalArgumentException e) {
            // do nothing
        }
    }

    @Test
    public void comparisons() throws Exception {
        Version ver1 = new Version("1.2.3");
        Version ver2 = new Version("1.2.4");
        assertEquals(-1, ver1.compareTo(ver2));
        assertEquals(1, ver2.compareTo(ver1));

        ver1 = new Version(1);
        ver2 = new Version(0, 9);
        assertEquals(1, ver1.compareTo(ver2));
        assertEquals(-1, ver2.compareTo(ver1));

        ver1 = new Version(1);
        ver2 = new Version(1, 0,0);
        assertEquals(0, ver1.compareTo(ver2));
    }
}
