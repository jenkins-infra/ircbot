package org.jenkinsci.backend.ircbot;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nonnull;

/**
 * Stores configurations of {@link IrcListener}.
 * This class has been created to achieve the better IRC Bot flexibility according to INFRA-146.
 * @author Oleg Nenashev <o.v.nenashev@gmail.com>
 */
public class IrcBotConfig {

    private static final String varPrefix = "ircbot.";

    // General
    /**
     * Name of the bot (up to 16 symbols).
     */
    private static final String DEFAULT_IRCBOT_NAME = ("ircbot-"+System.getProperty("user.name"));
    static String NAME = System.getProperty(varPrefix+"name", DEFAULT_IRCBOT_NAME);
    static String SERVER = System.getProperty(varPrefix+"server", "irc.libera.chat");
    static final Set<String> DEFAULT_CHANNELS = new HashSet<String>(Arrays.asList("#jenkins", "#jenkins-infra", "#jenkins-release", "#jenkins-hosting"));
    static final String CHANNELS_LIST = System.getProperty(varPrefix+"channels", "#jenkins,#jenkins-infra,#jenkins-release,#jenkins-hosting");

    // Testing
    /**
     * Name of the user, for which security checks should be skipped.
     * @since 2.0-SNAPSHOT
     */
    static String TEST_SUPERUSER = System.getProperty(varPrefix+"testSuperUser", null);

    // JIRAs
    /**
     * Specifies target JIRA URL.
     * @since 2.0-SNAPSHOT
     */
    static final String JIRA_URL = System.getProperty(varPrefix+"jira.url", "https://issues.jenkins.io");
    static final URI JIRA_URI;
    static String JIRA_DEFAULT_PROJECT = System.getProperty(varPrefix+"jira.defaultProject", "JENKINS");
    /**
     * Specifies timeout for JIRA requests (in seconds).
     * @since 2.0-SNAPSHOT
     */
    static final int JIRA_TIMEOUT_SEC = Integer.getInteger(varPrefix+"jira.requestTimeout", 30);

    // Github
    static String GITHUB_ORGANIZATION = System.getProperty(varPrefix+"github.organization", "jenkinsci");
    static String GITHUB_POST_COMMIT_HOOK_EMAIL = System.getProperty(varPrefix+"github.postCommitHookEmail", "jenkinsci-commits@googlegroups.com");

    static {
        try {
            JIRA_URI = new URL(JIRA_URL).toURI();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot create URI for JIRA URL " + JIRA_URL, ex);
        }
    }

    public static @Nonnull Set<String> getChannels() {
        HashSet<String> res = new HashSet<String>();
        if (CHANNELS_LIST != null) {
            String[] channels = CHANNELS_LIST.split(",");
            for (String channel : channels) {
                if (channel.startsWith("#")) {
                    res.add(channel);
                }
            }
        }
        return res.isEmpty() ? DEFAULT_CHANNELS : res;
    }
}
