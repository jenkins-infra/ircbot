package org.jenkinsci.backend.ircbot;

import java.util.Map;
import junit.framework.TestCase;

/**
 * Tests for {@link IrcBotConfig}.
 * @author Oleg Nenashev <o.v.nenashev@gmail.com>
 */
public class IrcBotConfigTest  extends TestCase {
    
    public void testGetConfig() {
        System.out.println("name = " + IrcBotConfig.NAME);
        System.out.println("default JIRA project = " + IrcBotConfig.JIRA_DEFAULT_PROJECT);        
    }
    
    public void testGetIRCHookConfig() {
        Map<String,String> ircHookConfig = IrcBotConfig.getIRCHookConfig();
        
        for (Map.Entry<String,String> e : ircHookConfig.entrySet()) {
            System.out.println(e.getKey() + " = " + e.getValue());
        }
    }
    
}
