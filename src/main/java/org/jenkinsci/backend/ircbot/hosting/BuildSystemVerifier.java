package org.jenkinsci.backend.ircbot.hosting;

import com.atlassian.jira.rest.client.api.domain.Issue;

import java.io.IOException;

public interface BuildSystemVerifier extends Verifier {
    boolean hasBuildFile(Issue issue) throws IOException;
}
