package org.jvnet.hudson.backend.ircbot;

import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 * One-off program to bulk update the post commit hook.
 *
 * @author Kohsuke Kawaguchi
 */
public class FixUpPostCommitHook {
    public static void main(String[] args) throws Exception {
        GitHub github = GitHub.connect();
        GHOrganization org = github.getOrganization("jenkinsci");

        for (GHRepository r : org.getRepositories().values()) {
            r.setEmailServiceHook(IrcBotImpl.POST_COMMIT_HOOK_EMAIL);
        }
    }
}
