package org.jenkinsci.backend.ircbot;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.ProjectRestClient;
import com.atlassian.jira.rest.client.api.UserRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.Component;
import com.atlassian.jira.rest.client.api.domain.Project;
import com.atlassian.util.concurrent.Promise;
import junit.framework.TestCase;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.Queue;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by slide on 11/14/2016.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ GitHub.class, JiraHelper.class })
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

    public void testComponentListNonEmpty() throws Exception {
        _testComponentListNonEmpty(false);
    }

    public void testComponentListNonEmptyCommand() throws Exception {
        _testComponentListNonEmpty(true);
    }

    public void _testComponentListNonEmpty(boolean useCommand) throws Exception {
        final String channel = "#dummy";
        final String botUser = "bot-user";
        final String owner = "bar";

        PowerMockito.mockStatic(JiraHelper.class);
        JiraRestClient client = Mockito.mock(JiraRestClient.class);
        Mockito.when(JiraHelper.createJiraClient()).thenReturn(client);

        UserRestClient userClient = Mockito.mock(UserRestClient.class);
        com.atlassian.jira.rest.client.api.domain.User u = Mockito.mock(com.atlassian.jira.rest.client.api.domain.User.class);
        Mockito.when(u.getName()).thenReturn(owner);
        Promise<com.atlassian.jira.rest.client.api.domain.User> uPromise = Mockito.mock(Promise.class);
        Mockito.when(userClient.getUser(owner)).thenReturn(uPromise);
        Mockito.when(uPromise.claim()).thenReturn(u);
        Mockito.when(client.getUserClient()).thenReturn(userClient);

        ProjectRestClient projectClient = Mockito.mock(ProjectRestClient.class);
        Project p = Mockito.mock(Project.class);
        Promise<Project> pPromise = Mockito.mock(Promise.class);
        Mockito.when(client.getProjectClient()).thenReturn(projectClient);
        Mockito.when(projectClient.getProject(IrcBotConfig.JIRA_DEFAULT_PROJECT)).thenReturn(pPromise);
        Mockito.when(pPromise.claim()).thenReturn(p);

        List<BasicComponent> componentList = new ArrayList<BasicComponent>();

        Component c = Mockito.mock(Component.class);
        Mockito.when(c.getName()).thenReturn("Component A");
        Mockito.when(c.getLead()).thenReturn(u);
        componentList.add(c);

        c = Mockito.mock(Component.class);
        Mockito.when(c.getName()).thenReturn("Component B");
        com.atlassian.jira.rest.client.api.domain.User dummy = Mockito.mock(com.atlassian.jira.rest.client.api.domain.User.class);
        Mockito.when(u.getName()).thenReturn("dummy");
        Mockito.when(c.getLead()).thenReturn(dummy);
        componentList.add(c);

        c = Mockito.mock(Component.class);
        Mockito.when(c.getName()).thenReturn("Component C");
        Mockito.when(c.getLead()).thenReturn(u);
        componentList.add(c);

        Mockito.when(p.getComponents()).thenReturn(componentList);

        Mockito.when(JiraHelper.close(client)).thenReturn(true);

        System.setProperty("ircbot.testSuperUser", botUser);

        IrcBotImpl bot = new IrcBotImpl(null);
        Method m = PircBot.class.getDeclaredMethod("addUser", String.class, User.class);
        Field f = PircBot.class.getDeclaredField("_outQueue");
        f.setAccessible(true);

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
                if(useCommand) {
                    bot.handleDirectCommand(channel, botUser, "", "", "list the components for " + owner);
                } else {
                    bot.listComponentsForUser(channel, botUser, owner);
                }
                Queue q = (Queue) f.get(bot);
                assertTrue(q.hasNext());
                assertEquals(q.next(), "PRIVMSG #dummy :User bar is default assignee of the following components in the issue tracker: Component A, Component C");
            } else {
                fail("Could not get User constructor");
            }
        } else {
            fail("Could not get addUser method");
        }
    }

    public void testComponentListEmpty() throws Exception {
        _testComponentListEmpty(false);
    }

    public void testComponentListEmptyCommand() throws Exception {
        _testComponentListEmpty(true);
    }

    public void _testComponentListEmpty(boolean useCommand) throws Exception {
        final String channel = "#dummy";
        final String botUser = "bot-user";
        final String owner = "bar";

        PowerMockito.mockStatic(JiraHelper.class);
        JiraRestClient client = Mockito.mock(JiraRestClient.class);
        Mockito.when(JiraHelper.createJiraClient()).thenReturn(client);

        UserRestClient userClient = Mockito.mock(UserRestClient.class);
        com.atlassian.jira.rest.client.api.domain.User u = Mockito.mock(com.atlassian.jira.rest.client.api.domain.User.class);
        Mockito.when(u.getName()).thenReturn(owner);
        Promise<com.atlassian.jira.rest.client.api.domain.User> uPromise = Mockito.mock(Promise.class);
        Mockito.when(userClient.getUser(owner)).thenReturn(uPromise);
        Mockito.when(uPromise.claim()).thenReturn(u);
        Mockito.when(client.getUserClient()).thenReturn(userClient);

        ProjectRestClient projectClient = Mockito.mock(ProjectRestClient.class);
        Project p = Mockito.mock(Project.class);
        Promise<Project> pPromise = Mockito.mock(Promise.class);
        Mockito.when(client.getProjectClient()).thenReturn(projectClient);
        Mockito.when(projectClient.getProject(IrcBotConfig.JIRA_DEFAULT_PROJECT)).thenReturn(pPromise);
        Mockito.when(pPromise.claim()).thenReturn(p);

        com.atlassian.jira.rest.client.api.domain.User dummy = Mockito.mock(com.atlassian.jira.rest.client.api.domain.User.class);
        Mockito.when(u.getName()).thenReturn("dummy");

        List<BasicComponent> componentList = new ArrayList<BasicComponent>();

        Component c = Mockito.mock(Component.class);
        Mockito.when(c.getName()).thenReturn("Component A");
        Mockito.when(c.getLead()).thenReturn(dummy);
        componentList.add(c);

        c = Mockito.mock(Component.class);
        Mockito.when(c.getName()).thenReturn("Component B");
        Mockito.when(c.getLead()).thenReturn(dummy);
        componentList.add(c);

        c = Mockito.mock(Component.class);
        Mockito.when(c.getName()).thenReturn("Component C");
        Mockito.when(c.getLead()).thenReturn(dummy);
        componentList.add(c);

        Mockito.when(p.getComponents()).thenReturn(componentList);

        Mockito.when(JiraHelper.close(client)).thenReturn(true);

        System.setProperty("ircbot.testSuperUser", botUser);

        IrcBotImpl bot = new IrcBotImpl(null);
        Method m = PircBot.class.getDeclaredMethod("addUser", String.class, User.class);
        Field f = PircBot.class.getDeclaredField("_outQueue");
        f.setAccessible(true);

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
                if(useCommand) {
                    bot.handleDirectCommand(channel, botUser, "", "", "list the components for " + owner);
                } else {
                    bot.listComponentsForUser(channel, botUser, owner);
                }
                Queue q = (Queue) f.get(bot);
                assertTrue(q.hasNext());
                assertEquals(q.next(), "PRIVMSG #dummy :User bar is not the default assignee for any components in the issue tracker");
            } else {
                fail("Could not get User constructor");
            }
        } else {
            fail("Could not get addUser method");
        }
    }
}
