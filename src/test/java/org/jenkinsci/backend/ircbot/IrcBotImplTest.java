package org.jenkinsci.backend.ircbot;

import junit.framework.TestCase;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import org.junit.runner.RunWith;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;


/**
 * Created by slide on 11/14/2016.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(GitHub.class)
@PowerMockIgnore("javax.net.ssl.*")
public class IrcBotImplTest extends TestCase {
    public void testForkGithubExistingRepo() throws Exception {
        final String repoName = "jenkins";
        final String channel = "#dummy";
        final String botUser = "bot-user";
        final String owner = "bar";
        final String from = "foobar";

        PowerMockito.mockStatic(GitHub.class);

        GitHub gh = Mockito.mock(GitHub.class);
        Mockito.when(GitHub.connect()).thenReturn(gh);

        GHRepository repo = Mockito.mock(GHRepository.class);

        GHOrganization gho = Mockito.mock(GHOrganization.class);
        Mockito.when(gho.getRepository(repoName)).thenReturn(repo);

        Mockito.when(gh.getOrganization(IrcBotConfig.GITHUB_ORGANIZATION)).thenReturn(gho);

        System.setProperty("ircbot.testSuperUser", botUser);

        IrcBotImpl bot = new IrcBotImpl(null);
        Method m = PircBot.class.getDeclaredMethod("addUser", String.class, User.class);
        if(m != null) {
            m.setAccessible(true);
            Constructor<User> bc = User.class.getDeclaredConstructor(String.class, String.class);
            if(bc != null) {
                bc.setAccessible(true);
                User bot_user = bc.newInstance("+", botUser);
                m.invoke(bot, channel, bot_user);
                User[] users = bot.getUsers(channel);
                assertEquals(users.length, 1);
                assertEquals(users[0].getPrefix(), "+");
                assertEquals(users[0].getNick(), botUser);
                assertFalse(bot.forkGitHub(channel, botUser, owner, from, repoName));
            } else {
                fail("Could not get User constructor");
            }
        } else {
            fail("Could not get addUser method");
        }
    }
}
