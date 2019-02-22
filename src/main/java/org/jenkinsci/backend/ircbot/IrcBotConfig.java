package org.jenkinsci.backend.ircbot;

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
    static String SERVER = System.getProperty(varPrefix+"server", "irc.freenode.net");
    static final Set<String> DEFAULT_CHANNELS = new HashSet<String>(Arrays.asList("#jenkins", "#jenkins-infra", "#jenkins-community"));
    static final String CHANNELS_LIST = System.getProperty(varPrefix+"channels", "#jenkins,#jenkins-infra,#jenkins-community");

    // Testing
    /**
     * Name of the user, for which security checks should be skipped.
     * @since 2.0-SNAPSHOT
     */
    static String TEST_SUPERUSER = System.getProperty(varPrefix+"testSuperUser", null);
    
    // IRC Hook
    static String IRC_HOOK_NAME = System.getProperty(varPrefix+"ircHook.name", "irc");
    static String IRC_HOOK_PORT = System.getProperty(varPrefix+"ircHook.port", "6667");
    static String IRC_HOOK_NICK = System.getProperty(varPrefix+"ircHook.nick", "github-jenkins");
    static String IRC_HOOK_PASSWORD = System.getProperty(varPrefix+"ircHook.password", "");
    static String IRC_HOOK_ROOM = System.getProperty(varPrefix+"ircHook.room", "#jenkins-commits");
    static String IRC_HOOK_LONG_URL= System.getProperty(varPrefix+"ircHook.longUrl", "1");

    // JIRAs
    /**
     * Specifies target JIRA URL.
     * @since 2.0-SNAPSHOT
     */
    static final String JIRA_URL = System.getProperty(varPrefix+"jira.url", "https://issues.jenkins-ci.org");
    static final URI JIRA_URI;
    static String JIRA_DEFAULT_PROJECT = System.getProperty(varPrefix+"jira.defaultProject", "JENKINS");
    /**
     * Specifies timeout for JIRA requests (in seconds).
     * @since 2.0-SNAPSHOT
     */
    static final int JIRA_TIMEOUT_SEC = Integer.getInteger(varPrefix+"jira.requestTimeout", 30);

    // Github
    static String GITHUB_ORGANIZATION = System.getProperty(varPrefix+"github.organization", "jenkinsci");
    static String GITHUB_INFRA_ORGANIZATION = System.getProperty(varPrefix+"github.infraOrganization", "jenkins-infra");
    static String GITHUB_UPLOAD_PERMISSIONS_REPO = System.getProperty(varPrefix+".github.uploadPermissionRepo", "repository-permissions-updater");
    static String GITHUB_POST_COMMIT_HOOK_EMAIL = System.getProperty(varPrefix+"github.postCommitHookEmail", "jenkinsci-commits@googlegroups.com");

    static {
        try {
            JIRA_URI = new URL(JIRA_URL).toURI();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot create URI for JIRA URL " + JIRA_URL, ex);
        }
    }
    
    public static Map<String, String> getIRCHookConfig() {

        final Map<String, String> ircHookConfig = new TreeMap<String, String>();
        ircHookConfig.put("server", SERVER);
        ircHookConfig.put("port", IRC_HOOK_PORT);
        ircHookConfig.put("nick", IRC_HOOK_NICK);
        ircHookConfig.put("password", IRC_HOOK_PASSWORD);
        ircHookConfig.put("room", IRC_HOOK_ROOM);
        ircHookConfig.put("long_url", IRC_HOOK_LONG_URL);

        return Collections.unmodifiableMap(ircHookConfig);
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
