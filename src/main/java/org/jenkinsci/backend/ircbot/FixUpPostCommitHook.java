package org.jenkinsci.backend.ircbot;

import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GitHub;

/**
 * One-off program to bulk update the plugin repository for fix up
 *
 * @author Kohsuke Kawaguchi
 */
public class FixUpPostCommitHook {
    public static void main(String[] args) throws Exception {
        GitHub github = GitHub.connect();
        GHOrganization org = github.getOrganization("jenkinsci");

        GHTeam e = org.getTeams().get("Everyone");

        for (GHRepository r : org.getRepositories().values()) {
            if (r.getName().endsWith("-plugin")) {
                System.out.println("Cleaning up "+r.getName());
                e.add(r);
            }


//            r.setEmailServiceHook(IrcBotImpl.POST_COMMIT_HOOK_EMAIL);
//
//            if (r.getName().endsWith("-plugin")) {
//                System.out.println("Cleaning up "+r.getName());
//                r.enableWiki(false);
//                Set<GHTeam> teams = r.getTeams();
//                for (GHTeam t : teams) {
//                    if (t.getName().equals("Everyone")) continue;   // leave it in
//                    if (t.getName().equals("bulk created plugin repository"))       continue;   // likewise. marker.
//                    if (t.getName().startsWith(r.getName()+" "))    continue;   // repository local team
//                    System.out.println("Removing "+t.getName()+"\tfrom "+r.getName());
//                    t.remove(r);
//                }
//            }
        }
    }
}
