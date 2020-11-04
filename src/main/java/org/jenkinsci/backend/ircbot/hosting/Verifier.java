package org.jenkinsci.backend.ircbot.hosting;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;

import java.io.IOException;
import java.util.HashSet;

public interface Verifier {
    void verify(IssueRestClient issueClient, Issue issue, HashSet<VerificationMessage> hostingIssues) throws IOException;
}
