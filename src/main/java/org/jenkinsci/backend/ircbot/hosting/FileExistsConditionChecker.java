package org.jenkinsci.backend.ircbot.hosting;

import com.atlassian.jira.rest.client.api.domain.Issue;

import java.io.IOException;

public class FileExistsConditionChecker extends ConditionChecker {
    private final String fileName;

    public FileExistsConditionChecker(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public boolean checkCondition(Issue issue) throws IOException {
        return fileExistsInForkFrom(issue, fileName);
    }
}
