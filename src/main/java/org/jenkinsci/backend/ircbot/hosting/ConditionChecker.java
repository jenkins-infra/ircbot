package org.jenkinsci.backend.ircbot.hosting;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.backend.ircbot.JiraHelper;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ConditionChecker {
    public abstract boolean checkCondition(Issue issue) throws IOException;

    protected boolean fileExistsInForkFrom(Issue issue, String fileName) throws IOException {
        boolean res = false;
        GitHub github = GitHub.connect();
        String forkFrom = JiraHelper.getFieldValueOrDefault(issue, JiraHelper.FORK_FROM_JIRA_FIELD, "");

        if(StringUtils.isNotBlank(forkFrom)) {
            Matcher m = Pattern.compile("(?:https:\\/\\/github\\.com/)?(\\S+)\\/(\\S+)", Pattern.CASE_INSENSITIVE).matcher(forkFrom);
            if(m.matches()) {
                String owner = m.group(1);
                String repoName = m.group(2);

                GHRepository repo = github.getRepository(owner+"/"+repoName);
                try {
                    GHContent file = repo.getFileContent(fileName);
                    res = file != null && file.isFile();
                } catch(IOException e) {
                }
            }
        }
        return res;
    }
}
