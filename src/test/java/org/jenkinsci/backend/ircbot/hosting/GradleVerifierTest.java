package org.jenkinsci.backend.ircbot.hosting;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;
import org.apache.commons.lang.ArrayUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.github.GitHub;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(JUnitParamsRunner.class)
@PrepareForTest(GitHub.class)
@PowerMockIgnore("javax.net.ssl.*")
public class GradleVerifierTest {
    @Test
    @Parameters
    @TestCaseName("gradleVerifierTests({0})")
    public void gradleVerifierTests(String testName, String buildGradleFile, String[] expectedMessages) throws Exception {
        GradleVerifier verifier = new GradleVerifier();
        HostingContext context = new HostingContext();
        context.addFileContents(new HostingContext.TestFileContent("build.gradle", buildGradleFile));
        context.addFileContents(new HostingContext.TestFileContent("LICENSE.md", "LICENSE.md"));

        IssueRestClient issueClient = mock(IssueRestClient.class);
        Issue issue = context.mockHostingRequest();

        HashSet<VerificationMessage> hostingIssues = new HashSet<>();
        verifier.verify(issueClient, issue, hostingIssues);

        if(expectedMessages != null) {
            assertEquals(expectedMessages.length, hostingIssues.size());
            VerificationMessage[] actualMessages = hostingIssues.toArray(new VerificationMessage[0]);
            assertEquals(expectedMessages.length, actualMessages.length);
            for(VerificationMessage msg : actualMessages) {
                assertTrue(ArrayUtils.contains(expectedMessages, msg.getMessage()));
            }
        } else {
            assertEquals(0, hostingIssues.size());
        }
    }

    public Object[] parametersForGradleVerifierTests() {
        return new Object[]{
                new Object[]{"All Good", "all-good-build.gradle", null},
        };
    }
}
