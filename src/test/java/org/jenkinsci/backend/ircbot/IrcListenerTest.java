package org.jenkinsci.backend.ircbot;

import com.google.common.collect.ImmutableSortedSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.kohsuke.github.*;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.pircbotx.Channel;
import org.pircbotx.User;
import org.pircbotx.UserLevel;
import org.pircbotx.output.OutputChannel;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * Created by slide on 11/14/2016.
 */
public class IrcListenerTest {
    static MockedStatic<GitHub> utilities = Mockito.mockStatic(GitHub.class);

    @AfterAll
    public static void afterClass() throws Exception {
        utilities.closeOnDemand();
    }

    @Test
    public void testForkGithubExistingRepo() throws Exception {
        final String repoName = "jenkins";
        final String channel = "#dummy";
        final String botUser = "bot-user";
        final String owner = "bar";
        final String from = "foobar";

        GitHub gh = mock(GitHub.class);
        utilities.when(GitHub::connect).thenReturn(gh);

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

        assertFalse(ircListener.forkGitHub(chan, sender, owner, from, repoName, emptyList(), false));
    }

    @Test
    public void testForkOriginSameNameAsExisting() throws Exception {
        final String repoName = "some-new-name";
        final String channel = "#dummy";
        final String botUser = "bot-user";
        final String owner = "bar";
        final String from = "jenkins";

        GitHub gh = mock(GitHub.class);
        utilities.when(GitHub::connect).thenReturn(gh);

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
        assertFalse(ircListener.forkGitHub(chan, sender, owner, from, repoName, emptyList(), false));
    }

    @Test
    public void testForkOriginSameNameAsRenamed() throws Exception {
        final String repoName = "some-new-name";
        final String channel = "#dummy";
        final String botUser = "bot-user";
        final String owner = "bar";
        final String from = "jenkins";

        GitHub gh = mock(GitHub.class);
        utilities.when(GitHub::connect).thenReturn(gh);

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

        GHTeamBuilder teamBuilder = mock(GHTeamBuilder.class);

        when(gho.createTeam("some-new-name Developers")).thenReturn(teamBuilder);
        when(teamBuilder.maintainers(any())).thenReturn(teamBuilder);
        when(teamBuilder.privacy(GHTeam.Privacy.CLOSED)).thenReturn(teamBuilder);

        GHTeam t = mock(GHTeam.class);
        when(teamBuilder.create()).thenReturn(t);
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
        assertTrue(ircListener.forkGitHub(chan, sender, owner, from, repoName, emptyList(), false));
    }

    @Test
    public void testCreateRepoWithNoIssueTracker() throws Exception {
        // see https://github.com/jenkins-infra/ircbot/issues/101

        final String repoName = "memkins";
        final String channel = "#dummy";
        final String botUser = "bot-user";
        final String owner = "bar";
        final String from = "foobar";

        GitHub gh = mock(GitHub.class);
        utilities.when(GitHub::connect).thenReturn(gh);

        GHUser awesomeUser = mock(GHUser.class);
        when(gh.getUser("awesome-user")).thenReturn(awesomeUser);

        GHRepository repo = mock(GHRepository.class);
        when(repo.getName()).thenReturn("memkins");

        GHCreateRepositoryBuilder repositoryBuilder = mock(GHCreateRepositoryBuilder.class);
        when(repositoryBuilder.private_(false)).thenReturn(repositoryBuilder);

        GHOrganization gho = mock(GHOrganization.class);
        when(gho.createRepository("memkins")).thenReturn(repositoryBuilder);
        when(gho.hasMember(awesomeUser)).thenReturn(true);

        when(repositoryBuilder.create()).thenReturn(repo);

        when(gh.getOrganization(IrcBotConfig.GITHUB_ORGANIZATION)).thenReturn(gho);

        GHTeam team = mock(GHTeam.class);

        GHTeamBuilder teamBuilder = mock(GHTeamBuilder.class);
        when(gho.createTeam("memkins Developers")).thenReturn(teamBuilder);
        when(teamBuilder.privacy(GHTeam.Privacy.CLOSED)).thenReturn(teamBuilder);
        when(teamBuilder.maintainers(any())).thenReturn(teamBuilder);
        when(teamBuilder.create()).thenReturn(team);

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

        ircListener.handleDirectCommand(chan, sender, "Create memkins on github for awesome-user");
        //assertFalse(ircListener.forkGitHub(chan, sender, owner, from, repoName, emptyList(), false));
    }
}
