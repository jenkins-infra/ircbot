package org.jenkinsci.backend.ircbot.hosting;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;
import org.apache.commons.lang.ArrayUtils;
import org.jenkinsci.backend.ircbot.HostingChecker;
import org.jenkinsci.backend.ircbot.JiraHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.github.GitHub;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(JUnitParamsRunner.class)
@PrepareForTest(GitHub.class)
@PowerMockIgnore("javax.net.ssl.*")
public class JiraVerifierTest {

    @Test
    @Parameters
    @TestCaseName("jiraVerifierTest({0})")
    public void jiraVerifierTest(String name, BiConsumer<HostingContext, String> setter, String value, String[] expectedMessages, HashMap<String, String> expectedUpdates) throws Exception {
        JiraVerifier verifier = new JiraVerifier();
        HashSet<VerificationMessage> hostingIssues = new HashSet<>();
        HostingContext context = new HostingContext();
        if(setter != null) {
            setter.accept(context, value);
        }
        Issue issue = context.mockHostingRequest();

        IssueRestClient issueClient = context.mockIssueRestClient();
        verifier.verify(issueClient, issue, hostingIssues);

        if (expectedMessages != null) {
            assertEquals("There should be the same number of expected and actual issues", expectedMessages.length, hostingIssues.size());
            VerificationMessage[] actualMessages = hostingIssues.toArray(new VerificationMessage[0]);
            for (VerificationMessage msg : actualMessages) {
                assertTrue("The actual message should appear in the expected messages", ArrayUtils.contains(expectedMessages, msg.getMessage()));
            }
        } else {
            assertEquals("There should be no issues with the hosting request", 0, hostingIssues.size());
        }

        HashMap<String, String> issueUpdates = context.getIssueUpdates();
        if (expectedUpdates != null) {
            assertEquals("There should be the same number of actual updates as expected", expectedUpdates.size(), issueUpdates.size());
            for (String field : issueUpdates.keySet()) {
                assertEquals("The actual updated value should match the expected update value", expectedUpdates.get(field), issueUpdates.get(field));
            }
        } else {
            assertEquals("There should be no updates to the issue", 0, issueUpdates.size());
        }
    }

    public Object[] parametersForJiraVerifierTest() {
        BiConsumer<HostingContext, String> jiraUserListSetter = (c, v) -> c.setJiraUserList(v);
        BiConsumer<HostingContext, String> forkFromSetter = (c, v) -> c.setForkFromUrl(v);
        BiConsumer<HostingContext, String> forkToSetter = (c, v) -> c.setForkToName(v);

        return new Object[]{
//                // user list
//                new Object[]{"All Good", null, null, null, null}, // everything should be all good
//                new Object[]{"User List: Empty", jiraUserListSetter, "", new String[]{"Missing list of users to authorize in 'GitHub Users to Authorize as Committers'"}, null},
//                new Object[]{"User List: Bad Format", jiraUserListSetter, "user1;user2;user3", null, new HashMap<String, String>() {
//                    {
//                        put(JiraHelper.USER_LIST_JIRA_FIELD, "user1\nuser2\nuser3");
//                    }
//                }},
//
//                // fork from
//                new Object[]{"Fork From: Empty", forkFromSetter, "", new String[]{String.format(HostingChecker.INVALID_FORK_FROM, "")}, null},
//                new Object[]{"Fork From: .git at end", forkFromSetter, "https://github.com/test/test-plugin.git", null, new HashMap<String, String>() {{
//                    put(JiraHelper.FORK_FROM_JIRA_FIELD, "https://github.com/test/test-plugin");
//                }}},
//                new Object[]{"Fork From: http url", forkFromSetter, "http://github.com/test/test-plugin", null, new HashMap<String, String>() {{
//                    put(JiraHelper.FORK_FROM_JIRA_FIELD, "https://github.com/test/test-plugin");
//                }}},
//                new Object[]{"Fork From: non-GitHub URL", forkFromSetter, "https://gitlab.com/test/test-plugin", new String[] { String.format(HostingChecker.INVALID_FORK_FROM, "https://gitlab.com/test/test-plugin") }, null},
//
                // fork to
                new Object[]{"Fork To: Empty", forkToSetter, "", new String[] {"You must specify the repository name to fork to in 'New Repository Name' field with the following rules:"}, null},
                new Object[]{"Fork To: Bad Letter Casing", forkToSetter, "TestPlugin", null, new HashMap<String, String>() {{
                    put(JiraHelper.FORK_TO_JIRA_FIELD, "test-plugin");
                }}},
                new Object[]{"Fork To: Contains -jenkins", forkToSetter, "test-jenkins-plugin", null, new HashMap<String,String>() {{
                    put(JiraHelper.FORK_TO_JIRA_FIELD, "test-plugin");
                }}},
                new Object[]{"Fork To: Contains jenkins", forkToSetter, "jenkins-test-plugin", null, new HashMap<String,String>() {{
                    put(JiraHelper.FORK_TO_JIRA_FIELD, "test-plugin");
                }}},
                new Object[]{"Fork To: Contains -hudson", forkToSetter, "test-hudson-plugin", null, new HashMap<String,String>() {{
                    put(JiraHelper.FORK_TO_JIRA_FIELD, "test-plugin");
                }}},
                new Object[]{"Fork To: Contains hudson", forkToSetter, "hudson-test-plugin", null, new HashMap<String,String>() {{
                    put(JiraHelper.FORK_TO_JIRA_FIELD, "test-plugin");
                }}},
                new Object[]{"Fork To: Does not end in -plugin", forkToSetter, "test", new String[] {"'New Repository Name' must end with \"-plugin\" (disregard if you are not requesting hosting of a plugin)" }, null},
        };
    }
}
