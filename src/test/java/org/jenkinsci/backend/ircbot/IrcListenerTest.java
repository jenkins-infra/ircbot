package org.jenkinsci.backend.ircbot;

import com.google.common.collect.ImmutableSortedSet;
import junit.framework.TestCase;
import org.junit.runner.RunWith;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.UserLevel;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.output.OutputChannel;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doAnswer;


/**
 * Created by slide on 11/14/2016.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(GitHub.class)
@PowerMockIgnore("javax.net.ssl.*")
public class IrcListenerTest extends TestCase {
    public void testForkGithubExistingRepo() throws Exception {
        final String repoName = "jenkins";
        final String channel = "#dummy";
        final String botUser = "bot-user";
        final String owner = "bar";
        final String from = "foobar";

        PowerMockito.mockStatic(GitHub.class);

        GitHub gh = mock(GitHub.class);
        when(GitHub.connect()).thenReturn(gh);

        GHRepository repo = mock(GHRepository.class);

        GHOrganization gho = mock(GHOrganization.class);
        when(gho.getRepository(repoName)).thenReturn(repo);

        when(gh.getOrganization(IrcBotConfig.GITHUB_ORGANIZATION)).thenReturn(gho);

        System.setProperty("ircbot.testSuperUser", botUser);

        IrcListener ircListener = new IrcListener(null);
        User sender = mock(User.class);
        when(sender.getNick()).thenReturn(botUser);

        Channel chan = mock(Channel.class);
        when(chan.getName()).thenReturn(channel);

        OutputChannel out = mock(OutputChannel.class);
        when(chan.send()).thenReturn(out);

        ImmutableSortedSet.Builder<UserLevel> builder = ImmutableSortedSet.naturalOrder();
        builder.add(UserLevel.VOICE);
        when(sender.getUserLevels(chan)).thenReturn(builder.build());

        assertFalse(ircListener.forkGitHub(chan, sender, owner, from, repoName));
    }

    public void testForkOriginSameNameAsExisting() throws Exception {
        final String repoName = "some-new-name";
        final String channel = "#dummy";
        final String botUser = "bot-user";
        final String owner = "bar";
        final String from = "jenkins";

        PowerMockito.mockStatic(GitHub.class);

        GitHub gh = mock(GitHub.class);
        when(GitHub.connect()).thenReturn(gh);

        GHRepository originRepo = mock(GHRepository.class);
        final GHRepository newRepo = mock(GHRepository.class);

        GHUser user = mock(GHUser.class);
        when(gh.getUser(owner)).thenReturn(user);
        when(user.getRepository(from)).thenReturn(originRepo);
        when(originRepo.getName()).thenReturn(from);

        GHRepository repo = mock(GHRepository.class);
        final GHOrganization gho = mock(GHOrganization.class);
        when(gho.getRepository(from)).thenReturn(repo);
        when(repo.getName()).thenReturn(from);

        when(gh.getOrganization(IrcBotConfig.GITHUB_ORGANIZATION)).thenReturn(gho);

        when(originRepo.forkTo(gho)).thenReturn(newRepo);
        when(newRepo.getName()).thenReturn(repoName);

        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                if (arguments != null && arguments.length > 0 && arguments[0] != null) {
                    String newName = (String) arguments[0];
                    when(gho.getRepository(newName)).thenReturn(newRepo);
                }
                return null;
            }
        }).when(newRepo).renameTo(repoName);

        GHTeam t = mock(GHTeam.class);
        Mockito.doNothing().when(t).add(user);

        when(gho.createTeam("some-new-name Developers", GHOrganization.Permission.ADMIN, newRepo)).thenReturn(t);

        System.setProperty("ircbot.testSuperUser", botUser);

        IrcListener ircListener = new IrcListener(null);
        User sender = mock(User.class);
        when(sender.getNick()).thenReturn(botUser);

        Channel chan = mock(Channel.class);
        when(chan.getName()).thenReturn(channel);

        OutputChannel out = mock(OutputChannel.class);
        when(chan.send()).thenReturn(out);

        ImmutableSortedSet.Builder<UserLevel> builder = ImmutableSortedSet.naturalOrder();
        builder.add(UserLevel.VOICE);
        when(sender.getUserLevels(chan)).thenReturn(builder.build());
        assertFalse(ircListener.forkGitHub(chan, sender, owner, from, repoName));
    }

    public void testForkOriginSameNameAsRenamed() throws Exception {
        final String repoName = "some-new-name";
        final String channel = "#dummy";
        final String botUser = "bot-user";
        final String owner = "bar";
        final String from = "jenkins";

        PowerMockito.mockStatic(GitHub.class);

        GitHub gh = mock(GitHub.class);
        when(GitHub.connect()).thenReturn(gh);

        GHRepository originRepo = mock(GHRepository.class);
        final GHRepository newRepo = mock(GHRepository.class);

        GHUser user = mock(GHUser.class);
        when(gh.getUser(owner)).thenReturn(user);
        when(user.getRepository(from)).thenReturn(originRepo);
        when(originRepo.getName()).thenReturn(from);

        GHRepository repo = mock(GHRepository.class);
        final GHOrganization gho = mock(GHOrganization.class);
        when(gho.getRepository(from)).thenReturn(repo);
        when(repo.getName()).thenReturn("othername");

        when(gh.getOrganization(IrcBotConfig.GITHUB_ORGANIZATION)).thenReturn(gho);

        when(originRepo.forkTo(gho)).thenReturn(newRepo);
        when(newRepo.getName()).thenReturn(repoName);

        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                if (arguments != null && arguments.length > 0 && arguments[0] != null) {
                    String newName = (String) arguments[0];
                    when(gho.getRepository(newName)).thenReturn(newRepo);
                }
                return null;
            }
        }).when(newRepo).renameTo(repoName);

        GHTeam t = mock(GHTeam.class);
        Mockito.doNothing().when(t).add(user);

        when(gho.createTeam("some-new-name Developers", GHOrganization.Permission.PULL, newRepo)).thenReturn(t);
        Mockito.doNothing().when(t).add(newRepo, GHOrganization.Permission.ADMIN);

        System.setProperty("ircbot.testSuperUser", botUser);

        IrcListener ircListener = new IrcListener(null);
        User sender = mock(User.class);
        when(sender.getNick()).thenReturn(botUser);

        Channel chan = mock(Channel.class);
        when(chan.getName()).thenReturn(channel);

        OutputChannel out = mock(OutputChannel.class);
        when(chan.send()).thenReturn(out);

        ImmutableSortedSet.Builder<UserLevel> builder = ImmutableSortedSet.naturalOrder();
        builder.add(UserLevel.VOICE);
        when(sender.getUserLevels(chan)).thenReturn(builder.build());
        assertTrue(ircListener.forkGitHub(chan, sender, owner, from, repoName));
    }

    public void testAddCommitter() throws Exception {
        final String channel = "#jenkins";
        final String botUser = "bot-user";
        final String newCommitter = "new-committer";
        final String repoName = "the-repo";
        final List<GHUser> addedUsers = new ArrayList<>();

        PowerMockito.mockStatic(GitHub.class);

        GitHub gh = mock(GitHub.class);
        when(GitHub.connect()).thenReturn(gh);

        GHOrganization o = mock(GHOrganization.class);
        when(gh.getOrganization(IrcBotConfig.GITHUB_ORGANIZATION)).thenReturn(o);

        GHRepository repo = mock(GHRepository.class);
        when(repo.getName()).thenReturn(repoName);

        when(o.getRepository(repoName)).thenReturn(repo);

        GHTeam team = mock(GHTeam.class);
        Map<String, GHTeam> teams = new HashMap<>();
        teams.put(repoName+" Developers", team);
        when(o.getTeams()).thenReturn(teams);

        GHUser user = mock(GHUser.class);
        when(gh.getUser(newCommitter)).thenReturn(user);

        doAnswer((Answer) invocation -> {
            GHUser u = invocation.getArgumentAt(0, GHUser.class);
            addedUsers.add(u);
            return null;
        }).when(team).add(user);

        System.setProperty("ircbot.testSuperUser", botUser);

        IrcListener ircListener = new IrcListener(null);
        User sender = mock(User.class);
        when(sender.getNick()).thenReturn(botUser);

        Channel chan = mock(Channel.class);
        when(chan.getName()).thenReturn(channel);

        OutputChannel out = mock(OutputChannel.class);
        when(chan.send()).thenReturn(out);

        ImmutableSortedSet.Builder<UserLevel> builder = ImmutableSortedSet.naturalOrder();
        builder.add(UserLevel.VOICE);
        when(sender.getUserLevels(chan)).thenReturn(builder.build());

        MessageEvent<?> event = mock(MessageEvent.class);
        when(event.getChannel()).thenReturn(chan);
        when(event.getMessage()).thenReturn("jenkins-admin: make "+newCommitter+" a committer on "+repoName);
        when(event.getUser()).thenReturn(sender);

        PircBotX bot = mock(PircBotX.class);
        when(bot.getNick()).thenReturn("jenkins-admin");
        when(event.getBot()).thenReturn(bot);

        assertEquals(0, addedUsers.size());

        ircListener.onMessage(event);

        assertEquals(1, addedUsers.size());
    }

    public void testAddCommitters() throws Exception {
        final String channel = "#jenkins";
        final String botUser = "bot-user";
        final List<String> newCommitters = new ArrayList<>();
        newCommitters.add("new-committer1");
        newCommitters.add("new-committer2");
        newCommitters.add("new-committer3");

        final List<GHUser> addedUsers = new ArrayList<>();

        final String repoName = "the-repo";

        PowerMockito.mockStatic(GitHub.class);

        GitHub gh = mock(GitHub.class);
        when(GitHub.connect()).thenReturn(gh);

        GHOrganization o = mock(GHOrganization.class);
        when(gh.getOrganization(IrcBotConfig.GITHUB_ORGANIZATION)).thenReturn(o);

        GHRepository repo = mock(GHRepository.class);
        when(repo.getName()).thenReturn(repoName);

        when(o.getRepository(repoName)).thenReturn(repo);

        GHTeam team = mock(GHTeam.class);
        Map<String, GHTeam> teams = new HashMap<>();
        teams.put(repoName+" Developers", team);
        when(o.getTeams()).thenReturn(teams);

        GHUser committerUser;
        for(String newCommitter : newCommitters) {
            committerUser = mock(GHUser.class);
            when(gh.getUser(newCommitter)).thenReturn(committerUser);
            doAnswer((Answer) invocation -> {
                GHUser u = invocation.getArgumentAt(0, GHUser.class);
                addedUsers.add(u);
                return null;
            }).when(team).add(committerUser);
        }

        System.setProperty("ircbot.testSuperUser", botUser);

        IrcListener ircListener = new IrcListener(null);
        User sender = mock(User.class);
        when(sender.getNick()).thenReturn(botUser);

        Channel chan = mock(Channel.class);
        when(chan.getName()).thenReturn(channel);

        OutputChannel out = mock(OutputChannel.class);
        when(chan.send()).thenReturn(out);

        ImmutableSortedSet.Builder<UserLevel> builder = ImmutableSortedSet.naturalOrder();
        builder.add(UserLevel.VOICE);
        when(sender.getUserLevels(chan)).thenReturn(builder.build());

        MessageEvent<?> event = mock(MessageEvent.class);
        when(event.getChannel()).thenReturn(chan);
        when(event.getMessage()).thenReturn("jenkins-admin: make "+String.join(",  ", newCommitters)+" committers on "+repoName);
        when(event.getUser()).thenReturn(sender);

        PircBotX bot = mock(PircBotX.class);
        when(bot.getNick()).thenReturn("jenkins-admin");
        when(event.getBot()).thenReturn(bot);

        assertEquals(0, addedUsers.size());

        ircListener.onMessage(event);

        assertEquals(3, addedUsers.size());
    }
}
