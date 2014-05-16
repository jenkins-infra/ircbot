package org.jenkinsci.backend.ircbot;

import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
public class FixupEmailHook {
    private static final Logger LOG = Logger.getLogger(FixUpIrcCommitHook.class.getName());

    public static void main(String[] args) throws Exception {
        final GitHub github = GitHub.connect();

        for (GHRepository repository : github.getOrganization("jenkinsci").listRepositories()) {
            LOG.info("Found GitHub repository " + repository.getName());

            if (hasMailHook(repository)) {
                LOG.info(" ... existing email hook, bailing out");
                continue;
            }

            repository.setEmailServiceHook(IrcBotImpl.POST_COMMIT_HOOK_EMAIL);
            LOG.info(" ... created new email hook");
        }
    }

    protected static boolean hasMailHook(final GHRepository repository) throws IOException {
        if (repository != null) {
            for (final GHHook hook : repository.getHooks()) {
                if (hook != null) {
                    if ("email".equalsIgnoreCase(hook.getName()))
                        return true;
                }
            }
        }

        return false;
    }
}
