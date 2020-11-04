package org.jenkinsci.backend.ircbot.hosting;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;
import org.apache.commons.lang.ArrayUtils;
import org.jenkinsci.backend.ircbot.HostingChecker;
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
public class MavenVerifierTest {
    @Test
    @Parameters
    @TestCaseName("mavenVerifierTests({0})")
    public void mavenVerifierTests(String testName, String pomFile, String[] expectedMessages) throws Exception {
        MavenVerifier verifier = new MavenVerifier();
        HostingContext context = new HostingContext();
        context.addFileContents(new HostingContext.TestFileContent("pom.xml", pomFile));
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

    public Object[] parametersForMavenVerifierTests() {
        return new Object[]{
                new Object[]{"All Good", "all-good-pom.xml", null},
                new Object[]{"Invalid Pom", "invalid-pom.xml", new String[]{MavenVerifier.INVALID_POM}},
                new Object[]{"Missing groupId", "missing-groupid-pom.xml", new String[]{"You must add a <groupdId> in your pom.xml with the value `io.jenkins.plugins`."}},
                new Object[]{"Bad artifactId vs fork to", "bad-artifactid-pom.xml", new String[]{"The <artifactId> from the pom.xml (test-bad) is incorrect, it should be 'test' ('New Repository Name' field with \"-plugin\" removed)"}},
                new Object[]{"artifactId includes \"Jenkins\"", "artifactid-with-jenkins-pom.xml", new String[]{"The <artifactId> from the pom.xml (jenkins-test) should not contain \"Jenkins\"", "The <artifactId> from the pom.xml (jenkins-test) is incorrect, it should be 'test' ('New Repository Name' field with \"-plugin\" removed)"}},
                new Object[]{"artifactId includes uppercase letters", "uppercase-in-artifactid-pom.xml", new String[]{"The <artifactId> from the pom.xml (Test) is incorrect, it should be 'test' ('New Repository Name' field with \"-plugin\" removed)", "The <artifactId> from the pom.xml (Test) should be all lower case"}},
                new Object[]{"missing value for artifactId", "blank-artifactid-pom.xml", new String[]{"The pom.xml file does not contain a valid <artifactId> for the project"}},
                new Object[]{"old groupId \"org.jenkins-ci.plugins\"", "old-groupid-pom.xml", new String[]{MavenVerifier.SHOULD_BE_IO_JENKINS_PLUGINS}},
                new Object[]{"missing value for <name></name>", "no-name-pom.xml", new String[]{"The pom.xml file does not contain a valid <name> for the project"}},
                new Object[]{"<name> contains \"Jenkins\"", "name-with-jenkins-pom.xml", new String[]{"The <name> field in the pom.xml should not contain \"Jenkins\""}},
                new Object[]{"parent version is too low", "parent-version-too-low-pom.xml", new String[]{String.format("The parent pom version '%s' should be at least %s or higher", "3.5", MavenVerifier.LOWEST_PARENT_POM_VERSION)}},
                new Object[]{"jenkins.version is too low", "jenkins-version-too-low-pom.xml", new String[]{String.format("Your pom.xml's <jenkins.version>(%s)</jenkins.version> does not meet the minimum Jenkins version required, please update your <jenkins.version> to at least %s", "2.122.3", HostingChecker.LOWEST_JENKINS_VERSION)}},
                new Object[]{"bad url for repo.jenkins-ci.org <repository>", "invalid-repository-url-pom.xml", new String[]{"The <repository><url></url></repository> in your pom.xml for 'repo.jenkins-ci.org' has an invalid URL"}},
                new Object[]{"http:// url for repo.jenkins-ci.org <repository>", "non-https-repository-url-pom.xml", new String[]{"You MUST use an https:// scheme in your pom.xml for the <repository><url></url></repository> tag for repo.jenkins-ci.org"}},
                new Object[]{"bad url for repo.jenkins-ci.org <pluginRepository>", "invalid-pluginrepository-url-pom.xml", new String[]{"The <pluginRepository><url></url></pluginRepository> in your pom.xml for 'repo.jenkins-ci.org' has an invalid URL"}},
                new Object[]{"http:// url for repo.jenkins-ci.org <pluginRepository>", "non-https-pluginrepository-url-pom.xml", new String[]{"You MUST use an https:// scheme in your pom.xml for the <pluginRepository><url></url></pluginRepository> tag for repo.jenkins-ci.org"}},
                new Object[]{"missing <licenses>", "missing-license-pom.xml", new String[]{MavenVerifier.SPECIFY_LICENSE}},
        };
    }
}
