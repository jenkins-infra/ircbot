package org.jenkinsci.backend.ircbot;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stores configurations of {@link IrcBotImpl}.
 * This class has been created to achieve the better IRC Bot flexibility according to INFRA-146.
 * @author Oleg Nenashev <o.v.nenashev@gmail.com>
 */
public class IrcBotConfig {
    
    private static final String varPrefix = "ircbot.";
    /**package*/ static int CACHE_REFRESH_PERIOD = Integer.getInteger(IrcBotConfig.class.getName() + ".cacheRefreshPeriod", 1000);
    
    // General
    /**
     * Name of the bot (up to 16 symbols).
     */
    private static final String DEFAULT_IRCBOT_NAME = ("ircbot-"+System.getProperty("user.name")); 
    static String NAME = System.getProperty(varPrefix+"name", DEFAULT_IRCBOT_NAME);
    
    // IRC Hook
    static String IRC_HOOK_NAME = System.getProperty(varPrefix+"ircHook.name", "irc");
    static String IRC_HOOK_SERVER = System.getProperty(varPrefix+"ircHook.server", "irc.freenode.net");
    static String IRC_HOOK_PORT = System.getProperty(varPrefix+"ircHook.port", "6667");
    static String IRC_HOOK_NICK = System.getProperty(varPrefix+"ircHook.nick", "github-jenkins");
    static String IRC_HOOK_PASSWORD = System.getProperty(varPrefix+"ircHook.password", "");
    static String IRC_HOOK_ROOM = System.getProperty(varPrefix+"ircHook.room", "#jenkins-commits");
    static String IRC_HOOK_LONG_URL= System.getProperty(varPrefix+"ircHook.longUrl", "1");
    
    // JIRAs
    static String JIRA_DEFAULT_PROJECT = System.getProperty(varPrefix+"jira.defaultProject", "JENKINS");
    
    // Github
    static String GITHUB_ORGANIZATION = System.getProperty(varPrefix+"github.organization", "jenkinsci");
    static String GITHUB_DEFAULT_TEAM = System.getProperty(varPrefix+"github.defaultTeam", "Everyone");
    
    
    public static Map<String, String> getIRCHookConfig() {
        
        final Map<String, String> ircHookConfig = new TreeMap<String, String>();
        ircHookConfig.put("server", IRC_HOOK_SERVER);
        ircHookConfig.put("port", IRC_HOOK_PORT);
        ircHookConfig.put("nick", IRC_HOOK_NICK);
        ircHookConfig.put("password", IRC_HOOK_PASSWORD);
        ircHookConfig.put("room", IRC_HOOK_ROOM);
        ircHookConfig.put("long_url", IRC_HOOK_LONG_URL);

        return Collections.unmodifiableMap(ircHookConfig);
    }
}
