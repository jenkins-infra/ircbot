/*
 * The MIT License
 *
 * Copyright (c) 2016 Oleg Nenashev.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.backend.ircbot;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.Component;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.Project;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.atlassian.util.concurrent.Promise;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jenkinsci.backend.ircbot.util.ConnectionInfo;

/**
 * Provides helper methods for JIRA access.
 * @author Oleg Nenashev
 * @since 2.0-SNAPSHOT
 */
public class JiraHelper {

    public static final String FORK_TO_JIRA_FIELD = "customfield_10321";
    public static final String FORK_FROM_JIRA_FIELD = "customfield_10320";
    public static final String USER_LIST_JIRA_FIELD = "customfield_10323";
    public static final String DONE_JIRA_RESOLUTION_NAME = "Done";
    
    /**
     * Creates JIRA client using settings from {@link ConnectionInfo} and {@link IrcBotConfig}.
     * @return Created client with configured authentication settings.
     * @throws IOException Client creation failure 
     */
    @Nonnull
    static JiraRestClient createJiraClient() throws IOException {
        ConnectionInfo con = new ConnectionInfo();
        JiraRestClient client = new AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(
                IrcBotConfig.JIRA_URI, con.userName, con.password);
        return client;
    }
    
    /**
     * Waits till the completion of the synchronized command.
     * @param <T> Type of the promise
     * @param promise Ongoing operation
     * @return Operation result
     * @throws InterruptedException Operation interrupted externally
     * @throws ExecutionException Execution failure
     * @throws TimeoutException Timeout (configured by {@link IrcBotConfig#JIRA_TIMEOUT_SEC}) 
     */
    @Nonnull
    static <T> T wait(Promise<T> promise) 
            throws InterruptedException, ExecutionException, TimeoutException {
        return promise.get(IrcBotConfig.JIRA_TIMEOUT_SEC, TimeUnit.SECONDS);
    }
    
    @Nonnull
    static Issue getIssue(JiraRestClient client, String ticket) 
            throws ExecutionException, TimeoutException, InterruptedException {
        return client.getIssueClient().getIssue(ticket).get(IrcBotConfig.JIRA_TIMEOUT_SEC, TimeUnit.SECONDS);
    }
    

    static boolean close(JiraRestClient client) {
        try {
            if (client != null) {
                client.close();
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    @Nonnull
    static BasicComponent getBasicComponent(JiraRestClient client, String projectId, String componentName) 
            throws ExecutionException, TimeoutException, InterruptedException, IOException {
        Project project = wait(client.getProjectClient().getProject(projectId));
        for (BasicComponent component : project.getComponents()) {
            if (component.getName().equals(componentName)) {
                return component;
            }
        }
        throw new IOException("Unable to find component " + componentName + " in the " + projectId + " issue tracker");
    }
    
    @Nonnull
    static Component getComponent(JiraRestClient client, String projectName, String componentName) 
            throws ExecutionException, TimeoutException, InterruptedException, IOException {
        BasicComponent bc = getBasicComponent(client, projectName, componentName);
        return wait(client.getComponentClient().getComponent(bc.getSelf()));
    }
    
    /**
     * Gets issue summary string.
     * @param ticket Ticket to be retrieved
     * @return Summary string for the issue
     * @throws IOException Operation failure
     * @throws InterruptedException Operation has been interrupted
     * @throws TimeoutException Timeout violation. See {@link IrcBotConfig#JIRA_TIMEOUT_SEC}.
     */
    static String getSummary(String ticket) throws IOException, ExecutionException, TimeoutException, InterruptedException {
        String result;
        JiraRestClient client = createJiraClient();
        Issue issue = client.getIssueClient().getIssue(ticket).get(IrcBotConfig.JIRA_TIMEOUT_SEC, TimeUnit.SECONDS);
        result = String.format("%s:%s (%s) %s",
                issue.getKey(), issue.getSummary(), issue.getStatus().getName(),
                IrcBotConfig.JIRA_URL + "/browse/" + ticket);
        close(client);
        return result;
    }
    
    @CheckForNull
    static String getFieldValue(@Nonnull Issue issue, @Nonnull String fieldId) {
        return getFieldValueOrDefault(issue, fieldId, null);
    }
    
    @Nullable
    public static String getFieldValueOrDefault(@Nonnull Issue issue, @Nonnull String fieldId, @CheckForNull String defaultValue) {
        String res = defaultValue;
        for (IssueField val : issue.getFields()) {
            String thisFieldId = val.getId();
            if (thisFieldId.equalsIgnoreCase(fieldId)) {
                Object _value = val.getValue();
                if (_value != null) {
                    res = _value.toString();
                }
            }
        }
        return res;
    }

    static Iterable<Transition> getTransitions(@Nonnull Issue issue) throws IOException, ExecutionException, TimeoutException, InterruptedException {
        JiraRestClient client = createJiraClient();
        return client.getIssueClient().getTransitions(issue).get(IrcBotConfig.JIRA_TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @CheckForNull
    static Transition getTransitionByName(@Nonnull Iterable<Transition> transitions, String name) {
        for (Transition transition : transitions) {
            if (transition.getName().equalsIgnoreCase(name)) {
                return transition;
            }
        }
        return null;
    }
}
