package org.jenkinsci.backend.ircbot;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link IrcBotConfig}.
 * @author Oleg Nenashev <o.v.nenashev@gmail.com>
 */
public class IrcBotConfigTest  {
    @Test
    public void testGetConfig() {
        System.out.println("name = " + IrcBotConfig.NAME);
        System.out.println("default JIRA project = " + IrcBotConfig.JIRA_DEFAULT_PROJECT);        
    }
    
    @Test
    public void testGetIRCHookConfig() {
        Map<String,String> ircHookConfig = IrcBotConfig.getIRCHookConfig();
        
        for (Map.Entry<String,String> e : ircHookConfig.entrySet()) {
            System.out.println(e.getKey() + " = " + e.getValue());
        }
    }
}
