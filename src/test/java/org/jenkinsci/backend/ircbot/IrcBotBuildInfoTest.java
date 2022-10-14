package org.jenkinsci.backend.ircbot;

import java.io.IOException;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumingThat;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;


/**
 *
 * @author Oleg Nenashev <o.v.nenashev@gmail.com>
 */
public class IrcBotBuildInfoTest {

    @Test
    @Issue("INFRA-135")
    public void testVersionInfoStub() throws IOException {
        IrcBotBuildInfo info = IrcBotBuildInfo.readResourceFile("/versionInfo_test.properties");
        assertEquals("a", info.getBuildNumber());
        assertEquals("b", info.getBuildDate());
        assertEquals("c", info.getBuildID());
        assertEquals("d", info.getBuildURL());
        assertEquals("e", info.getGitCommit());
    }

    /**
     * Reads the real version info.
     * This test is expected to work in https://ci.jenkins-ci.org/job/infra_ircbot/ job only.
     * @throws IOException
     */
    @Test
    @Issue("INFRA-135")
    public void testVersionInfoReal() throws IOException {
        InputStream istream = IrcBotBuildInfo.class.getResourceAsStream("/versionInfo.properties");
        assumingThat(istream != null, () -> {
            System.out.println("This test is expected to work in https://ci.jenkins.io/job/Infra/job/ircbot/ job only");
            IrcBotBuildInfo info = IrcBotBuildInfo.readResourceFile("/versionInfo.properties");
            System.out.println(info);
        });
    }
}
