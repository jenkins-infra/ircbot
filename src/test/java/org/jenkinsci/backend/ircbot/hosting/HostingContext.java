package org.jenkinsci.backend.ircbot.hosting;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.util.concurrent.Promise;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.backend.ircbot.JiraHelper;
import org.kohsuke.github.*;
import org.mockito.stubbing.OngoingStubbing;
import org.powermock.api.mockito.PowerMockito;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doAnswer;

public class HostingContext {
    private String[] ghUsers = new String[] { "user1", "user2", "user3" };
    private String jiraUserList = "";
    private String forkFromUrl = "https://github.com/test/test-repo";
    private String forkFromRepoName = "test/test-repo";
    private String forkToName = "test-plugin";
    private boolean hasValidReadme = true;
    private boolean hasValidLicense = true;
    private String parentRepoName = null;
    private String[] forks = null;
    private ArrayList<TestFileContent> fileContents;
    private String issueKey = "HOSTING-123";
    private LinkedHashMap<String, String> issueUpdates;

    public static class TestFileContent {
        private final String fileName;
        private final String contents;

        public TestFileContent(String fileName, String contents) {
            this.fileName = fileName;
            this.contents = contents;
        }

        public String getFileName() {
            return fileName;
        }

        public String getContents() {
            return contents;
        }
    }

    public HostingContext() {
        jiraUserList = String.join("\n", ghUsers);
        fileContents = new ArrayList<>();
        issueUpdates = new LinkedHashMap<>();
    }

    public void setGHUsers(String[] users, boolean setUserList) {
        this.ghUsers = users;
        if(setUserList) {
            jiraUserList = String.join("\n", ghUsers);
        }
    }

    public void setGHUsers(String[] users) {
        setGHUsers(users, false);
    }

    public String[] getGHUsers() {
        return ghUsers;
    }

    public void setJiraUserList(String jiraUserList) {
        this.jiraUserList = jiraUserList;
    }

    public String getJiraUserList() {
        return jiraUserList;
    }

    public void setForkFromUrl(String forkFrom, boolean setForkFromRepoName) {
        this.forkFromUrl = forkFrom;
        if(setForkFromRepoName) {
            Matcher m = Pattern.compile("(?:https?:\\/\\/.*\\/)(\\S+)/(\\S+)").matcher(forkFrom);
            setForkFromRepoName(m.group(1)+"/"+m.group(2));
        }
    }

    public void setForkFromUrl(String forkFrom) {
        setForkFromUrl(forkFrom, false);
    }

    public String getForkFromUrl() {
        return forkFromUrl;
    }

    public void setForkToName(String forkToName) {
        this.forkToName = forkToName;
    }

    public String getForkToName() {
        return forkToName;
    }

    public void setForkFromRepoName(String forkFromRepoName) {
        this.forkFromRepoName = forkFromRepoName;
    }

    public String getForkFromRepoName() {
        return forkFromRepoName;
    }

    public void setHasValidReadme(boolean hasValidReadme) {
        this.hasValidReadme = hasValidReadme;
    }

    public boolean isHasValidReadme() {
        return hasValidReadme;
    }

    public void setHasValidLicense(boolean hasValidLicense) {
        this.hasValidLicense = hasValidLicense;
    }

    public boolean isHasValidLicense() {
        return hasValidLicense;
    }

    public void setParentRepoName(String parentRepoName) {
        this.parentRepoName = parentRepoName;
    }

    public String getParentRepoName() {
        return parentRepoName;
    }

    public void setForks(String[] forks) {
        this.forks = forks;
    }

    public String[] getForks() {
        return forks;
    }

    public void addFileContents(TestFileContent testFile) {
        fileContents.add(testFile);
    }

    public List<TestFileContent> getFileContents() {
        return fileContents;
    }

    public HashMap<String, String> getIssueUpdates() {
        return issueUpdates;
    }

    public Issue mockHostingRequest() throws Exception {
        PowerMockito.mockStatic(GitHub.class);

        GitHub gh = mock(GitHub.class);
        when(GitHub.connect()).thenReturn(gh);

        for(String user : ghUsers) {
            GHUser u = mock(GHUser.class);
            when(u.getType()).thenReturn("User");
            when(gh.getUser(user)).thenReturn(u);
        }

        GHRepository repo = mock(GHRepository.class);
        when(gh.getRepository(forkFromRepoName)).thenReturn(repo);

        for(TestFileContent content : fileContents) {
            GHContent c = mock(GHContent.class);
            when(c.isFile()).thenReturn(true);
            when(c.read()).thenReturn(getClass()
                    .getClassLoader().getResourceAsStream(content.getContents()));
            when(repo.getFileContent(content.getFileName())).thenReturn(c);
        }

        if(forks != null && forks.length > 0) {
            PagedIterable<GHRepository> forkIterable = mock(PagedIterable.class);
            PagedIterator<GHRepository> forkIterator = mock(PagedIterator.class);

            Boolean[] hasNextReturns = new Boolean[forks.length]; // need extra 1 for false
            GHRepository[] nextReturns = new GHRepository[forks.length-1];
            Arrays.fill(hasNextReturns, true);
            hasNextReturns[hasNextReturns.length - 1] = false;

            GHRepository firstFork = mock(GHRepository.class);
            when(firstFork.getFullName()).thenReturn(forks[0]);
            for(int i = 1; i < forks.length; i++) {
                GHRepository p = mock(GHRepository.class);
                when(p.getFullName()).thenReturn(forks[i]);
                nextReturns[i-1] = p;
            }
            when(forkIterator.hasNext()).thenReturn(true, hasNextReturns);
            when(forkIterator.next()).thenReturn(firstFork, nextReturns);

            when(forkIterable.iterator()).thenReturn(forkIterator);
            when(repo.listForks()).thenReturn(forkIterable);
        }

        if(StringUtils.isNotBlank(parentRepoName)) {
            GHRepository parentRepo = mock(GHRepository.class);
            when(parentRepo.getFullName()).thenReturn(parentRepoName);
            when(repo.getParent()).thenReturn(parentRepo);
        } else {
            when(repo.getParent()).thenReturn(null);
        }

        if(hasValidReadme) {
            GHContent readme = mock(GHContent.class);
            when(repo.getReadme()).thenReturn(readme);
        } else {
            when(repo.getReadme()).thenThrow(new IOException());
        }

        if(hasValidLicense) {
            GHLicense license = mock(GHLicense.class);
            when(repo.getLicense()).thenReturn(license);
        } else {
            when(repo.getLicense()).thenReturn(null);
        }

        List<IssueField> fields = new ArrayList<>();

        IssueField forkFromField = mock(IssueField.class);
        when(forkFromField.getId()).thenReturn(JiraHelper.FORK_FROM_JIRA_FIELD);
        when(forkFromField.getValue()).thenReturn(forkFromUrl);
        fields.add(forkFromField);

        IssueField userListField = mock(IssueField.class);
        when(userListField.getId()).thenReturn(JiraHelper.USER_LIST_JIRA_FIELD);
        when(userListField.getValue()).thenReturn(jiraUserList);
        fields.add(userListField);

        IssueField forkToField = mock(IssueField.class);
        when(forkToField.getId()).thenReturn(JiraHelper.FORK_TO_JIRA_FIELD);
        when(forkToField.getValue()).thenReturn(forkToName);
        fields.add(forkToField);

        Issue issue = mock(Issue.class);
        when(issue.getKey()).thenReturn(issueKey);
        when(issue.getFields()).thenReturn(fields);
        return issue;
    }

    public IssueRestClient mockIssueRestClient() {
        IssueRestClient issueClient = mock(IssueRestClient.class);
        Promise p = mock(Promise.class);

        doAnswer(invocation -> null).when(p).claim();

        doAnswer(invocation -> {
            IssueInput issueInput = invocation.getArgument(1);
            for(String fieldName : issueInput.getFields().keySet()) {
                issueUpdates.put(fieldName, issueInput.getField(fieldName).getValue().toString());
            }
            return p;
        }).when(issueClient).updateIssue(anyString(), any(IssueInput.class));

        return issueClient;
    }
}
