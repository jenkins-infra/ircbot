package org.jenkinsci.backend.ircbot.hosting;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.github.GitHub;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

@RunWith(PowerMockRunner.class)
@PrepareForTest(GitHub.class)
@PowerMockIgnore("javax.net.ssl.*")
public class GithubVerifierTest {
    private GitHub gh;

    @Test
    public void allGood() throws Exception {
        IssueRestClient issueClient = mock(IssueRestClient.class);
        HostingContext context = new HostingContext();
        Issue issue = context.mockHostingRequest();

        GithubVerifier verifier = new GithubVerifier();
        HashSet<VerificationMessage> hostingIssues = new HashSet<>();
        verifier.verify(issueClient, issue, hostingIssues);
        assertEquals(0, hostingIssues.size());
    }

    @Test
    public void badForkFrom() throws Exception {
        String forkFrom = "http://github.com/test/test-repo";
        IssueRestClient issueClient = mock(IssueRestClient.class);
        GithubVerifier verifier = new GithubVerifier();

        HostingContext context = new HostingContext();
        context.setForkFromUrl(forkFrom, false);
        Issue issue = context.mockHostingRequest();

        HashSet<VerificationMessage> hostingIssues = new HashSet<>();
        verifier.verify(issueClient, issue, hostingIssues);
        assertEquals(1, hostingIssues.size());
        VerificationMessage[] messages = hostingIssues.toArray(new VerificationMessage[0]);
        assertEquals(String.format("The origin repository '%s' doesn't use https, please fix", forkFrom), messages[0].getMessage());

        forkFrom = "https://github.com/test/test-repo.git";
        context = new HostingContext();
        context.setForkFromUrl(forkFrom, false);
        issue = context.mockHostingRequest();
        hostingIssues = new HashSet<>();
        verifier.verify(issueClient, issue, hostingIssues);
        assertEquals(1, hostingIssues.size());
        messages = hostingIssues.toArray(new VerificationMessage[0]);
        assertEquals(String.format("The origin repository '%s' ends in .git, please remove this", forkFrom), messages[0].getMessage());

        forkFrom = "https://someother.com/test/test-repo";
        context = new HostingContext();
        context.setForkFromUrl(forkFrom, false);
        issue = context.mockHostingRequest();
        hostingIssues = new HashSet<>();
        verifier.verify(issueClient, issue, hostingIssues);
        assertEquals(1, hostingIssues.size());
        messages = hostingIssues.toArray(new VerificationMessage[0]);
        assertEquals(String.format("Repository URL '%s' is not a valid GitHub repository (check that you do not have .git at the end, GitHub API doesn't support this).", forkFrom), messages[0].getMessage());
    }

    @Test
    public void noReadMe() throws Exception {
        IssueRestClient issueClient = mock(IssueRestClient.class);
        GithubVerifier verifier = new GithubVerifier();

        HostingContext context = new HostingContext();
        context.setHasValidReadme(false);

        Issue issue = context.mockHostingRequest();

        HashSet<VerificationMessage> hostingIssues = new HashSet<>();
        verifier.verify(issueClient, issue, hostingIssues);
        assertEquals(1, hostingIssues.size());
        VerificationMessage[] messages = hostingIssues.toArray(new VerificationMessage[0]);
        assertEquals("Please add a readme file to your repo, GitHub provides an easy mechanism to do this from their user interface.", messages[0].getMessage());
    }

    @Test
    public void noLicense() throws Exception {
        IssueRestClient issueClient = mock(IssueRestClient.class);
        GithubVerifier verifier = new GithubVerifier();

        HostingContext context = new HostingContext();
        context.setHasValidLicense(false);

        Issue issue = context.mockHostingRequest();

        HashSet<VerificationMessage> hostingIssues = new HashSet<>();
        verifier.verify(issueClient, issue, hostingIssues);
        assertEquals(1, hostingIssues.size());
        VerificationMessage[] messages = hostingIssues.toArray(new VerificationMessage[0]);
        assertEquals("Please add a license file to your repo, GitHub provides an easy mechanism to do this from their user interface.", messages[0].getMessage());
    }

    @Test
    public void badUsers() throws Exception {
        // one bad user
        HostingContext context = new HostingContext();
        context.setGHUsers(new String[] { "user1", "user2" });
        context.setJiraUserList(String.join("\n", new String[] { "user1", "user2", "user3" }));
        Issue issue = context.mockHostingRequest();

        IssueRestClient issueClient = mock(IssueRestClient.class);

        GithubVerifier verifier = new GithubVerifier();
        HashSet<VerificationMessage> hostingIssues = new HashSet<>();
        verifier.verify(issueClient, issue, hostingIssues);
        assertEquals(1, hostingIssues.size());
        VerificationMessage[] messages = hostingIssues.toArray(new VerificationMessage[0]);
        assertEquals("The following usernames in 'GitHub Users to Authorize as Committers' are not valid GitHub usernames or are Organizations: user3", messages[0].getMessage());

        // more than one bad user
        context = new HostingContext();
        context.setGHUsers(new String[] { "user1" });
        context.setJiraUserList(String.join("\n", new String[] { "user1", "user2", "user3" }));
        issue = context.mockHostingRequest();

        verifier = new GithubVerifier();
        hostingIssues = new HashSet<>();
        verifier.verify(issueClient, issue, hostingIssues);
        assertEquals(1, hostingIssues.size());
        messages = hostingIssues.toArray(new VerificationMessage[0]);
        assertEquals("The following usernames in 'GitHub Users to Authorize as Committers' are not valid GitHub usernames or are Organizations: user2,user3", messages[0].getMessage());
    }

    @Test
    public void badParentRepo() throws Exception {
        IssueRestClient issueClient = mock(IssueRestClient.class);
        GithubVerifier verifier = new GithubVerifier();

        HostingContext context = new HostingContext();
        context.setParentRepoName("jenkinsci/some-plugin");

        Issue issue = context.mockHostingRequest();

        HashSet<VerificationMessage> hostingIssues = new HashSet<>();
        verifier.verify(issueClient, issue, hostingIssues);
        assertEquals(1, hostingIssues.size());
        VerificationMessage[] messages = hostingIssues.toArray(new VerificationMessage[0]);
        assertEquals(String.format("Repository '%s' is currently showing as forked from a jenkinsci org repository, this relationship needs to be broken", context.getForkFromUrl()), messages[0].getMessage());
    }

    @Test
    public void badFork() throws Exception {
        IssueRestClient issueClient = mock(IssueRestClient.class);
        GithubVerifier verifier = new GithubVerifier();

        HostingContext context = new HostingContext();
        context.setForks(new String[] { "someorg/test-plugin", "jenkinsci/test-plugin" });
        Issue issue = context.mockHostingRequest();

        HashSet<VerificationMessage> hostingIssues = new HashSet<>();
        verifier.verify(issueClient, issue, hostingIssues);
        assertEquals(1, hostingIssues.size());
        VerificationMessage[] messages = hostingIssues.toArray(new VerificationMessage[0]);
        assertEquals(String.format("Repository '%s' already has the following forks in the jenkinsci org: jenkinsci/test-plugin", context.getForkFromUrl()), messages[0].getMessage());
    }
}
