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
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Status;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.jira_scraper.ConnectionInfo;

/**
 * Provides helper methods for JIRA access.
 * @author Oleg Nenashev
 * @since 2.0-SNAPSHOT
 */
class JiraHelper {
    
    /**
     * Creates JIRA client using settings from {@link ConnectionInfo} and {@link IrcBotConfig}.
     * @return Created client with configured authentication settings.
     * @throws IOException Client creation failure 
     */
    @Nonnull
    static JiraRestClient createJiraClient() throws IOException {
        ConnectionInfo con = new ConnectionInfo();
        
        final URI uri;
        try {
            uri = new URI(IrcBotConfig.JIRA_URL); 
        } catch(URISyntaxException ex) {
            throw new IOException("Cannot create JIRA URI from string " + IrcBotConfig.JIRA_URL, ex);
        }
        
        JiraRestClient client = new AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(
                uri, con.userName, con.password);
        return client;
    }
    
    @Nonnull
    static Issue getIssue(JiraRestClient client, String ticket) 
            throws ExecutionException, TimeoutException, InterruptedException {
        return client.getIssueClient().getIssue(ticket).get(IrcBotConfig.JIRA_TIMEOUT_SEC, TimeUnit.SECONDS);
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
        JiraRestClient client = createJiraClient();
        Issue issue = client.getIssueClient().getIssue(ticket).get(IrcBotConfig.JIRA_TIMEOUT_SEC, TimeUnit.SECONDS);
        return String.format("%s:%s (%s) %s",
                issue.getKey(), issue.getSummary(), issue.getStatus().getName(), 
                IrcBotConfig.JIRA_URL + "/browse/"+ticket);
    }

    @CheckForNull
    private static Status findStatus(JiraRestClient client, Long statusId) 
            throws TimeoutException, ExecutionException, InterruptedException {
        Iterable<Status> statuses = client.getMetadataClient().getStatuses().get(IrcBotConfig.JIRA_TIMEOUT_SEC, TimeUnit.SECONDS);
        for (Status s : statuses) {
            if(s.getId().equals(statusId)) {
                return s;
            }
        }
        return null;
    }
}
