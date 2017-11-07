package org.jenkinsci.backend.ircbot;

import junit.framework.TestCase;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import org.junit.runner.RunWith;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
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
    public void testForkOriginSameNameAsExisting() throws Exception {
        final String repoName = "some-new-name";
        final String channel = "#dummy";
        final String botUser = "bot-user";
        final String owner = "bar";
        final String from = "jenkins";

        PowerMockito.mockStatic(GitHub.class);

        GitHub gh = Mockito.mock(GitHub.class);
        Mockito.when(GitHub.connect()).thenReturn(gh);

        GHRepository originRepo = Mockito.mock(GHRepository.class);
        final GHRepository newRepo = Mockito.mock(GHRepository.class);

        GHUser user = Mockito.mock(GHUser.class);
        Mockito.when(gh.getUser(owner)).thenReturn(user);
        Mockito.when(user.getRepository(from)).thenReturn(originRepo);
        Mockito.when(originRepo.getName()).thenReturn(from);

        GHRepository repo = Mockito.mock(GHRepository.class);
        final GHOrganization gho = Mockito.mock(GHOrganization.class);
        Mockito.when(gho.getRepository(from)).thenReturn(repo);
        Mockito.when(repo.getName()).thenReturn(from);

        Mockito.when(gh.getOrganization(IrcBotConfig.GITHUB_ORGANIZATION)).thenReturn(gho);

        Mockito.when(originRepo.forkTo(gho)).thenReturn(newRepo);
        Mockito.when(newRepo.getName()).thenReturn(repoName);

        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                if (arguments != null && arguments.length > 0 && arguments[0] != null) {
                    String newName = (String) arguments[0];
                    Mockito.when(gho.getRepository(newName)).thenReturn(newRepo);
                }
                return null;
            }
        }).when(newRepo).renameTo(repoName);

        GHTeam t = Mockito.mock(GHTeam.class);
        Mockito.doNothing().when(t).add(user);

        Mockito.when(gho.createTeam("some-new-name Developers", GHOrganization.Permission.ADMIN, newRepo)).thenReturn(t);

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

    public void testForkOriginSameNameAsRenamed() throws Exception {
        final String repoName = "some-new-name";
        final String channel = "#dummy";
        final String botUser = "bot-user";
        final String owner = "bar";
        final String from = "jenkins";

        PowerMockito.mockStatic(GitHub.class);

        GitHub gh = Mockito.mock(GitHub.class);
        Mockito.when(GitHub.connect()).thenReturn(gh);

        GHRepository originRepo = Mockito.mock(GHRepository.class);
        final GHRepository newRepo = Mockito.mock(GHRepository.class);

        GHUser user = Mockito.mock(GHUser.class);
        Mockito.when(gh.getUser(owner)).thenReturn(user);
        Mockito.when(user.getRepository(from)).thenReturn(originRepo);
        Mockito.when(originRepo.getName()).thenReturn(from);

        GHRepository repo = Mockito.mock(GHRepository.class);
        final GHOrganization gho = Mockito.mock(GHOrganization.class);
        Mockito.when(gho.getRepository(from)).thenReturn(repo);
        Mockito.when(repo.getName()).thenReturn("othername");

        Mockito.when(gh.getOrganization(IrcBotConfig.GITHUB_ORGANIZATION)).thenReturn(gho);

        Mockito.when(originRepo.forkTo(gho)).thenReturn(newRepo);
        Mockito.when(newRepo.getName()).thenReturn(repoName);

        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                if (arguments != null && arguments.length > 0 && arguments[0] != null) {
                    String newName = (String) arguments[0];
                    Mockito.when(gho.getRepository(newName)).thenReturn(newRepo);
                }
                return null;
            }
        }).when(newRepo).renameTo(repoName);

        GHTeam t = Mockito.mock(GHTeam.class);
        Mockito.doNothing().when(t).add(user);

        Mockito.when(gho.createTeam("some-new-name Developers", GHOrganization.Permission.PULL, newRepo)).thenReturn(t);
        Mockito.doNothing().when(t).add(newRepo, GHOrganization.Permission.ADMIN);

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
                assertTrue(bot.forkGitHub(channel, botUser, owner, from, repoName));
            } else {
                fail("Could not get User constructor");
            }
        } else {
            fail("Could not get addUser method");
        }
    }

}
