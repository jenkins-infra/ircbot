package org.jenkinsci.backend.ircbot;

import java.io.IOException;
import java.util.Collection;
import java.util.logging.Logger;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 * One-off program to bulk update the IRC post commit hook.
 * 
 * @author <a href="mailto:jieryn@gmail.com">Jesse Farinacci</a>
 */
public final class FixUpIrcCommitHook {
    private static final Logger LOG = Logger.getLogger(FixUpIrcCommitHook.class
                                            .getName());

    public static void main(String[] args) throws Exception {
        final GitHub github = GitHub.connect();

        for (GHRepository repository : github.getOrganization("jenkinsci")
                .getRepositories().values()) {
            LOG.info("Found GitHub repository " + repository.getName());

            if (hasIrcHook(repository)) {
                LOG.info(" ... existing IRC hook, bailing out");
                continue;
            }

            repository.createHook(IrcBotConfig.IRC_HOOK_NAME,
                    IrcBotConfig.getIRCHookConfig(), (Collection<GHEvent>) null,
                    true);
            LOG.info(" ... created new IRC hook");
        }
    }

    protected static boolean hasIrcHook(final GHRepository repository)
            throws IOException {
        if (repository != null) {
            for (final GHHook hook : repository.getHooks()) {
                if (hook != null) {
                    if (IrcBotConfig.IRC_HOOK_NAME.equalsIgnoreCase(hook.getName()))
                        return true;
                }
            }
        }

        return false;
    }
}
