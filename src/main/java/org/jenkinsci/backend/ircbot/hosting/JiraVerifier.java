package org.jenkinsci.backend.ircbot.hosting;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.backend.ircbot.HostingChecker;
import org.jenkinsci.backend.ircbot.JiraHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JiraVerifier implements Verifier {

    @Override
    public void verify(IssueRestClient issueClient, Issue issue, HashSet<VerificationMessage> hostingIssues) {
        String forkTo = JiraHelper.getFieldValueOrDefault(issue, JiraHelper.FORK_TO_JIRA_FIELD, "");
        String forkFrom = JiraHelper.getFieldValueOrDefault(issue, JiraHelper.FORK_FROM_JIRA_FIELD, "");
        String userList = JiraHelper.getFieldValueOrDefault(issue, JiraHelper.USER_LIST_JIRA_FIELD, "");

        List<IssueInput> issueUpdates = new ArrayList<>();

        // check list of users
        if(StringUtils.isBlank(userList)) {
            hostingIssues.add(new VerificationMessage(VerificationMessage.Severity.REQUIRED, "Missing list of users to authorize in 'GitHub Users to Authorize as Committers'"));
        } else {
            String[] usersList = userList.split("[ ,\n;]");
            String cleanedList = String.join("\n", usersList);
            if(!cleanedList.equals(userList)) {
                issueUpdates.add(new IssueInputBuilder().setFieldValue(JiraHelper.USER_LIST_JIRA_FIELD, cleanedList).build());
            }
        }

        if(StringUtils.isBlank(forkFrom)) {
            hostingIssues.add(new VerificationMessage(VerificationMessage.Severity.REQUIRED, HostingChecker.INVALID_FORK_FROM, ""));
        } else {
            boolean updateIssue = false;
            if(forkFrom.endsWith(".git")) {
                forkFrom = forkFrom.substring(0, forkFrom.length() - 4);
                updateIssue = true;
            }

            if(forkFrom.startsWith("http://")) {
                forkFrom = forkFrom.replace("http://", "https://");
                updateIssue = true;
            }

            // check the repo they want to fork from to make sure it conforms
            if(!Pattern.matches("(?:https:\\/\\/github\\.com/)(\\S+)/(\\S+)", forkFrom)) {
                hostingIssues.add(new VerificationMessage(VerificationMessage.Severity.REQUIRED, HostingChecker.INVALID_FORK_FROM, forkFrom));
            }

            if(updateIssue) {
                issueUpdates.add(new IssueInputBuilder().setFieldValue(JiraHelper.FORK_FROM_JIRA_FIELD, forkFrom).build());
            }
        }

        if(StringUtils.isBlank(forkTo)) {
            HashSet<VerificationMessage> subitems = new HashSet<>();
            subitems.add(new VerificationMessage(VerificationMessage.Severity.REQUIRED, "It must match the artifactId (with -plugin added) from your build file (pom.xml/build.gradle)."));
            subitems.add(new VerificationMessage(VerificationMessage.Severity.REQUIRED, "It must end in -plugin if hosting request is for a Jenkins plugin."));
            subitems.add(new VerificationMessage(VerificationMessage.Severity.REQUIRED, "It must be all lowercase."));
            subitems.add(new VerificationMessage(VerificationMessage.Severity.REQUIRED, "It must NOT contain \"Jenkins\"."));
            subitems.add(new VerificationMessage(VerificationMessage.Severity.REQUIRED, "It must use hyphens ( - ) instead of spaces or camel case."));
            hostingIssues.add(new VerificationMessage(VerificationMessage.Severity.REQUIRED, subitems, "You must specify the repository name to fork to in 'New Repository Name' field with the following rules:"));
        } else {
            String originalForkTo = forkTo;
            // we don't like camel case - ThisIsCamelCase becomes this-is-camel-case
            Matcher m = Pattern.compile("(\\B[A-Z]+?(?=[A-Z][^A-Z])|\\B[A-Z]+?(?=[^A-Z]))").matcher(forkTo);
            String forkToLower = m.replaceAll("-$1").toLowerCase();
            if(forkToLower.contains("-jenkins") || forkToLower.contains("-hudson")) {
                forkToLower = forkToLower.replace("-jenkins", "").replace("-hudson", "");
            } else if(forkToLower.contains("jenkins") || forkToLower.contains("hudson")) {
                forkToLower = forkToLower.replace("jenkins", "").replace("hudson", "");
            }

            // sometimes if we remove jenkins/hudson, we're left with something like -jmh, so trim it
            forkToLower = StringUtils.strip(forkToLower, "- ");

            if(!forkToLower.endsWith("-plugin")) {
                hostingIssues.add(new VerificationMessage(VerificationMessage.Severity.REQUIRED, "'New Repository Name' must end with \"-plugin\" (disregard if you are not requesting hosting of a plugin)"));
            }

            // we don't like spaces...
            forkToLower = forkToLower.replace(" ", "-");

            if(!forkToLower.equals(originalForkTo)) {
                issueUpdates.add(new IssueInputBuilder().setFieldValue(JiraHelper.FORK_TO_JIRA_FIELD, forkToLower).build());
            }
        }

        if(issueUpdates.size() > 0) {
            for(IssueInput issueUpdate : issueUpdates) {
                issueClient.updateIssue(issue.getKey(), issueUpdate).claim();
            }
        }
    }
}
