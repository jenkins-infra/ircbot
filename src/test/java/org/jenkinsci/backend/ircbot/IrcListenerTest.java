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
import org.pircbotx.User;
import org.pircbotx.UserLevel;
import org.pircbotx.output.OutputChannel;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


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
}
