package org.jenkinsci.backend.ircbot;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.javatuples.Triplet;
import org.jenkinsci.backend.ircbot.hosting.*;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.pircbotx.output.OutputChannel;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

public class HostingChecker {

    private Log log = LogFactory.getLog(HostingChecker.class);

    public static final String INVALID_FORK_FROM = "Repository URL '%s' is not a valid GitHub repository (check that you do not have .git at the end, GitHub API doesn't support this).";

    public static final Version LOWEST_JENKINS_VERSION = new Version(2, 164, 3);

    public void checkRequest(OutputChannel out, String issueID) {
        JiraRestClient client = null;
        boolean hasBuildSystem = false;
        HashSet<VerificationMessage> hostingIssues = new HashSet<>();

        boolean debug = System.getProperty("debugHosting", "false").equalsIgnoreCase("true");

        ArrayList<Triplet<String, Verifier, ConditionChecker>> verifications = new ArrayList<>();
        verifications.add(Triplet.with("Jira", new JiraVerifier(), null));
        verifications.add(Triplet.with("GitHub", new GithubVerifier(), null));
        verifications.add(Triplet.with("Maven", new MavenVerifier(), new FileExistsConditionChecker("pom.xml")));
        verifications.add(Triplet.with("Gradle", new GradleVerifier(), new FileExistsConditionChecker("build.gradle")));
        //verifications.add(Triplet.with("Kotlin", new KotlinVerifier(), new FileExistsConditionChecker("build.gradle.kts")));

        try {
            client = JiraHelper.createJiraClient();
            final IssueRestClient issueClient = client.getIssueClient();
            final Issue issue = JiraHelper.getIssue(client, issueID);

            final com.atlassian.jira.rest.client.api.domain.User reporter = issue.getReporter();
            if (reporter == null) {
                hostingIssues.add(new VerificationMessage(VerificationMessage.Severity.REQUIRED, "Invalid user for \"reporter\", not a valid Jira user"));
            }

            for(Triplet<String, Verifier, ConditionChecker> verifier : verifications) {
                try {
                    boolean runIt = verifier.getValue2() != null ? verifier.getValue2().checkCondition(issue) : true;
                    if(runIt) {
                        out.message("Running verification '"+verifier.getValue0()+"'");
                        verifier.getValue1().verify(issueClient, issue, hostingIssues);
                    }

                    if(verifier.getValue1() instanceof BuildSystemVerifier) {
                        hasBuildSystem |= ((BuildSystemVerifier)verifier.getValue1()).hasBuildFile(issue);
                    }
                } catch(Exception e) {
                    out.message("Error running verification '"+verifier.getValue0()+"': "+e.toString());
                }
            }

            if(!hasBuildSystem) {
                hostingIssues.add(new VerificationMessage(VerificationMessage.Severity.WARNING, "No build system found (pom.xml, build.gradle, build.gradle.kts)"));
            }

            StringBuilder msg = new StringBuilder("Hello from your friendly Jenkins Hosting Checker\n\n");
            log.info("Checking if there were errors");
            if(hostingIssues.size() > 0) {
                msg.append("It appears you have some issues with your hosting request. Please see the list below and "
                        + "correct all issues marked {color:red}REQUIRED{color}. Your hosting request will not be "
                        + "approved until these issues are corrected. Issues marked with {color:orange}WARNING{color} "
                        + "or INFO are just recommendations and will not stall the hosting process.\n");
                log.info("Appending issues to msg");
                appendIssues(msg, hostingIssues, 1);
            } else {
                msg.append("It looks like you have everything in order for your hosting request. "
                        + "A human volunteer will check over things that I am not able to check for "
                        + "(code review, README content, etc) and process the request as quickly as possible. "
                        + "Thank you for your patience.");
            }

            log.info(msg.toString());
            if(!debug) {
                issueClient.addComment(new URI(issue.getSelf().toString() + "/comment"), Comment.valueOf(msg.toString())).
                        get(IrcBotConfig.JIRA_TIMEOUT_SEC, TimeUnit.SECONDS);
            } else {
                out.message("Here are the results of the checking:");
                out.message(msg.toString());
            }
        } catch (IOException|URISyntaxException|ExecutionException|TimeoutException|InterruptedException e) {
            out.message("Error occurred during hosting check: " + e.getMessage());
        } finally {
            if(!JiraHelper.close(client)) {
                out.message("Failed to close JIRA client, possible leaked file descriptors");
            }
        }
    }

    private void appendIssues(StringBuilder msg, HashSet<VerificationMessage> issues, int level) {
        for(VerificationMessage issue : issues.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
            if(level == 1) {
                msg.append(String.format("%s {color:%s}%s: %s{color}%n", StringUtils.repeat("*", level), issue.getSeverity().getColor(), issue.getSeverity().getMessage(), issue.getMessage()));
            } else {
                msg.append(String.format("%s %s%n", StringUtils.repeat("*", level), issue.getMessage()));
            }

            if(issue.getSubItems() != null) {
                appendIssues(msg, issue.getSubItems(), level+1);
            }
        }
    }

    public static boolean fileExistsInRepo(Issue issue, String fileName) throws IOException {
        boolean res = false;
        GitHub github = GitHub.connect();
        String forkFrom = JiraHelper.getFieldValueOrDefault(issue, JiraHelper.FORK_FROM_JIRA_FIELD, "");
        if(StringUtils.isNotBlank(forkFrom)) {
            Matcher m = Pattern.compile("(?:https:\\/\\/github\\.com/)(\\S+)/(\\S+)", CASE_INSENSITIVE).matcher(forkFrom);
            if(m.matches()) {
                String owner = m.group(1);
                String repoName = m.group(2);

                try {
                    GHRepository repo = github.getRepository(owner + "/" + repoName);
                    GHContent file = repo.getFileContent(fileName);
                    res = file != null && file.isFile();
                } catch(GHFileNotFoundException e) {
                    res = false;
                }
            }
        }
        return res;
    }
}
