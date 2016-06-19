package org.jenkinsci.backend.ircbot;

import com.atlassian.jira.rest.client.domain.AssigneeType;
import hudson.plugins.jira.soap.RemoteComment;
import hudson.plugins.jira.soap.RemoteCustomFieldValue;
import hudson.plugins.jira.soap.RemoteFieldValue;
import org.apache.axis.AxisFault;
import org.apache.axis.collections.LRUMap;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.jira_scraper.JiraScraper;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHOrganization.Permission;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.*;

/**
 * IRC Bot on irc.freenode.net as a means to delegate administrative work to committers.
 *
 * @author Kohsuke Kawaguchi
 */
public class IrcBotImpl extends PircBot {
    private static final String FORK_TO_JIRA_FIELD = "customfield_10321";
    private static final String FORK_FROM_JIRA_FIELD = "customfield_10320";
    private static final String USER_LIST_JIRA_FIELD = "customfield_10323";
    private static final String DONE_JIRA_RESOLUTION_ID = "10000";

    /**
     * Records commands that we didn't understand.
     */
    private File unknownCommands;

    /**
     * Map from the issue number to the time it was last mentioned.
     * Used so that we don't repeatedly mention the same issues.
     */
    @SuppressWarnings("unchecked")
    private final Map<String,Long> recentIssues = Collections.synchronizedMap(new LRUMap(10));

    public IrcBotImpl(File unknownCommands) {
        setName(IrcBotConfig.NAME);
        this.unknownCommands = unknownCommands;
    }
    
    @Override
    protected void onMessage(String channel, String sender, String login, String hostname, String message) {
        if (!IrcBotConfig.getChannels().contains(channel))     return; // not in this channel
        if (sender.equals("jenkinsci_builds") || sender.equals("jenkins-admin") || sender.startsWith("ircbot-"))   
            return; // ignore messages from other bots
        final String directMessagePrefix = getNick() + ":";
        
        message = message.trim();
        try {
            if (message.startsWith(directMessagePrefix)) { // Direct command to the bot
                // remove prefixes, trim whitespaces
                String payload = message.substring(directMessagePrefix.length(), message.length()).trim();
                payload = payload.replaceAll("\\s+", " ");
                handleDirectCommand(channel, sender, login, hostname, payload);
            } else {   // Just a commmon message in the chat
                replyBugStatuses(channel, message);
            }
        } catch (RuntimeException ex) { // Catch unhandled runtime issues
            ex.printStackTrace();
            sendMessage(channel, "An error ocurred in the Bot. Please submit a bug to Jenkins INFRA project.");
            sendMessage(channel, ex.getMessage());
            throw ex; // Propagate the error to the caller in order to let it log and handle the issue
        }
    }
        
    private void replyBugStatuses(String channel, String message) {
        Matcher m = Pattern.compile("(?:hudson-|jenkins-|bug )([0-9]{2,})",CASE_INSENSITIVE).matcher(message);
        while (m.find()) {
            replyBugStatus(channel,"JENKINS-"+m.group(1));
        }

        m = Pattern.compile("(?:infra-)([0-9]+)",CASE_INSENSITIVE).matcher(message);
        while (m.find()) {
            replyBugStatus(channel,"INFRA-"+m.group(1));
        }
    }
    
    /**
     * Handles direct commands coming to the bot.
     * The handler presumes the external trimming of the payload.
     */
    private void handleDirectCommand(String channel, String sender, String login, String hostname, String payload) {
        Matcher m;

        m = Pattern.compile("(?:create|make|add) (\\S+)(?: repository)? (?:on|in) github(?: for (\\S+))?",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            createGitHubRepository(channel, sender, m.group(1), m.group(2));
            return;
        }

        m = Pattern.compile("fork (?:https://github\\.com/)?(\\S+)/(\\S+)(?: on github)?(?: as (\\S+))?",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            forkGitHub(channel, sender, m.group(1),m.group(2),m.group(3));
            return;
        }
        
        m = Pattern.compile("rename (?:github )repo (\\S+) to (\\S+)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            renameGitHubRepo(channel, sender, m.group(1), m.group(2));
            return;
        }

        m = Pattern.compile("(?:make|give|grant|add) (\\S+)(?: as)? (?:a )?(?:committ?er|commit access) (?:of|on|to|at) (\\S+)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            addGitHubCommitter(channel,sender,m.group(1),m.group(2));
            return;
        }

        m = Pattern.compile("(?:make|give|grant|add) (\\S+)(?: as)? (a )?(committ?er|commit access).*",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            addGitHubCommitter(channel,sender,m.group(1),null);
            return;
        }

        m = Pattern.compile("(?:create|make|add) (\\S+)(?: component)? in (?:the )?(?:issue|bug)(?: tracker| database)? for (\\S+)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            createComponent(channel, sender, m.group(1), m.group(2));
            return;
        }
        
        m = Pattern.compile("(?:rem|remove|del|delete) component (\\S+) and move its issues to (\\S+)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            deleteComponent(channel, sender, m.group(1), m.group(2));
            return;
        }

        m = Pattern.compile("rename component (\\S+) to (\\S+)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            renameComponent(channel, sender, m.group(1), m.group(2));
            return;
        }

        m = Pattern.compile("(?:rem|remove) (?:the )?(?:lead|default assignee) (?:for|of|from) (\\S+)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            removeDefaultAssignee(channel, sender, m.group(1));
            return;
        }
        
        m = Pattern.compile("(?:make|set) (\\S+) (?:the |as )?(?:lead|default assignee) (?:for|of) (\\S+)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            setDefaultAssignee(channel, sender, m.group(2), m.group(1));
            return;
        }
        
        m = Pattern.compile("set (?:the )?description (?:for|of) (?:component )?(\\S+) to \\\"(.*)\\\"",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            setComponentDescription(channel, sender, m.group(1) , m.group(2));
            return;
        }
        
        m = Pattern.compile("(?:rem|remove) (?:the )?description (?:for|of) (?:component )?(\\S+)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            setComponentDescription(channel, sender, m.group(1) , null);
            return;
        }
        
        m = Pattern.compile("(?:make|give|grant|add) (\\S+) voice(?: on irc)?",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            grantAutoVoice(channel,sender,m.group(1));
            return;
        }

        m = Pattern.compile("(?:rem|remove|ungrant|del|delete) (\\S+) voice(?: on irc)?",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            removeAutoVoice(channel,sender,m.group(1));
            return;
        }

        m = Pattern.compile("(?:kick) (\\S+)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            kickUser(channel,sender,m.group(1));
            return;
        }

        m = Pattern.compile("(?:host) (\\d+)((?:[ ]+)(\\S+))?", CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            String forkTo = "";
            if(m.groupCount() > 1) {
                forkTo = m.group(2);
            }
            setupHosting(channel,sender,m.group(1),forkTo);
            return;
        }

        if (payload.equalsIgnoreCase("version")) {
            version(channel);
            return;
        }

        if (payload.equalsIgnoreCase("help")) {
            help(channel);
            return;
        }

        if (payload.equalsIgnoreCase("refresh")) {
            // get the updated list
            sendRawLine("NAMES " + channel);
            return;
        }
        
        if (payload.equalsIgnoreCase("restart")) {
            restart(channel,sender);
        }

        sendMessage(channel,"I didn't understand the command");

        try {
            PrintWriter w = new PrintWriter(new FileWriter(unknownCommands, true));
            w.println(payload);
            w.close();
        } catch (IOException e) {// if we fail to write, let it be.
            e.printStackTrace();
        }
    }

    /**
     * Restart ircbot.
     * 
     * We just need to quit, and docker container manager will automatically restart
     * another one. We've seen for some reasons sometimes jenkins-admin loses its +o flag,
     * and when that happens a restart fixes it quickly.
     */
    private void restart(String channel, String sender) {
        if (!isSenderAuthorized(channel,sender)) {
            insufficientPermissionError(channel);
            return;
        }
        
        sendMessage(channel,"I'll quit and come back");
        System.exit(0);
    }

    private void kickUser(String channel, String sender, String target) {
        if (!isSenderAuthorized(channel,sender)) {
            insufficientPermissionError(channel);
            return;
        }

        kick(channel, target);
        sendMessage(channel, "Kicked user" + target);
    }

    private void setupHosting(String channel, String sender, String hostingId, String forkTo) {
        if (!isSenderAuthorized(channel,sender)) {
            insufficientPermissionError(channel);
            return;
        }

        try {
            JiraSoapService svc = new JiraSoapServiceServiceLocator().getJirasoapserviceV2(new URL("http://issues.jenkins-ci.org/rpc/soap/jirasoapservice-v2"));
            ConnectionInfo con = new ConnectionInfo();
            String token = svc.login(con.userName,con.password);
            RemoteIssue issue = svc.getIssue(token,"HOSTING-"+hostingId);

            String forkFrom = "";
            List<String> users = new ArrayList<String>();

            String defaultAssignee = issue.getReporter();

            RemoteCustomFieldValue[] customFields = issue.getCustomFieldValues();
            for(RemoteCustomFieldValue val : customFields) {
                String fieldId = val.getCustomfieldId();
                if(fieldId.equalsIgnoreCase(FORK_FROM_JIRA_FIELD)) {
                    String[] values = val.getValues();
                    if(values.length > 0) {
                        forkFrom = val.getValues()[0];
                    }
                }

                if(fieldId.equalsIgnoreCase(USER_LIST_JIRA_FIELD)) {
                    String[] values = val.getValues();
                    if(values.length > 0) {
                        String userList = values[0];
                        for(String u : userList.split("\\n")) {
                            users.add(u.trim());
                        }
                    }
                }

                if(StringUtils.isBlank(forkTo) && fieldId.equalsIgnoreCase(FORK_TO_JIRA_FIELD)) {
                    String[] values = val.getValues();
                    if(values.length > 0) {
                        forkTo = values[0];
                    }
                }
            }

            if(StringUtils.isBlank(forkFrom) || StringUtils.isBlank(forkTo) || users.size() == 0) {
                sendMessage(channel,"Could not retrieve information (or information does not exist) from the HOSTING JIRA");
                return;
            }

            // we assume the first person in the list is the "owner"
            if(!forkGitHub(channel,sender,users.get(0),forkFrom,forkTo)) {
                sendMessage(channel,"Hosting request failed to fork repository on Github");
                return;
            }

            // add the users to the repo
            for(String user : users) {
                if(!addGitHubCommitter(channel,sender,user,forkTo)) {
                    sendMessage(channel,"Hosting request failed to add "+user+" as committer, continuing anyway");
                }
            }

            // create the JIRA component
            if(!createComponent(channel,sender,forkTo,defaultAssignee)) {
                sendMessage(channel,"Hosting request failed to create component "+forkTo+" in JIRA");
                return;
            }

            // update the issue with information on next steps
            String msg = "The code has been forked into the jenkinsci project on GitHub as "
                    + "https://github.com/jenkinsci/" + forkTo
                    + "\n\nA JIRA component named " + forkTo + " has also been created with "
                    + defaultAssignee + " as the default assignee for issues."
                    + "\n\nPlease remove your original repository so that the jenkinsci repository "
                    + "is the definitive source for the code. Also, please make sure you have "
                    + "a wiki page setup with the following guidelines in mind: "
                    + "https://wiki.jenkins-ci.org/display/JENKINS/Hosting+Plugins#HostingPlugins-CreatingaWikipage"
                    + "\n\nWelcome aboard!";

            // add comment
            RemoteComment c = new RemoteComment();
            c.setBody(msg);
            svc.addComment(token,"HOSTING-"+hostingId,c);

            // mark as "done".
            // comment set here doesn't work. see http://jira.atlassian.com/browse/JRA-11278
            try {
                svc.progressWorkflowAction(token,"HOSTING-"+hostingId,DONE_JIRA_RESOLUTION_ID,
                        new RemoteFieldValue[]{ new RemoteFieldValue("comment",new String[]{"closing comment"})});
            } catch (AxisFault e) {
                // if the issue cannot be put into the "resolved" state
                // (perhaps it's already in that state), let it be. Or else
                // we end up with the carpet bombing like HUDSON-2552.
                // See HUDSON-5133 for the failure mode.
                System.err.println("Failed to mark the issue as Done");
                e.printStackTrace();
            }

            sendMessage(channel,"Hosting setup complete");
        } catch(Exception e) {
            e.printStackTrace();
            sendMessage(channel,"Failed setting up hosting for HOSTING-"+hostingId);
        }
    }

    private void replyBugStatus(String channel, String ticket) {
        Long time = recentIssues.get(ticket);

        recentIssues.put(ticket,System.currentTimeMillis());

        if (time!=null) {
            if (System.currentTimeMillis()-time < 60*1000) {
                return; // already mentioned recently. don't repeat
            }
        }

        try {
            sendMessage(channel, JiraHelper.getSummary(ticket));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    

    /**
     * Is the sender respected in the channel?
     *
     * IOW, does he have a voice of a channel operator?
     */
    private boolean isSenderAuthorized(String channel, String sender) {
        return isSenderAuthorized(channel, sender, true);
    }
    
    private boolean isSenderAuthorized(String channel, String sender, boolean acceptVoice) {
        for (User u : getUsers(channel)) {
            System.out.println(u.getPrefix()+u.getNick());
            if (u.getNick().equals(sender)) {
                String p = u.getPrefix();
                if (p.contains("@") || (acceptVoice && p.contains("+")))
                    return true;
            }
        }
        return false;
    }

    @Override
    protected void onDisconnect() {
        while (!isConnected()) {
            try {
                reconnect();
                for (String channel : IrcBotConfig.getChannels()) {
                    joinChannel(channel);
                }
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException _) {
                    return; // abort
                }
            }
        }
    }

    private void help(String channel) {
        sendMessage(channel,"See http://wiki.jenkins-ci.org/display/JENKINS/IRC+Bot");
    }

    private void version(String channel) {
        try {
            IrcBotBuildInfo buildInfo = IrcBotBuildInfo.readResourceFile("/versionInfo.properties");
            sendMessage(channel,"My version is "+buildInfo);
            sendMessage(channel,"Build URL: "+buildInfo.getBuildURL());
        } catch (IOException e) {
            e.printStackTrace();
            sendMessage(channel,"I don't know who I am");
        } 
    }

    private void insufficientPermissionError(String channel) {
        insufficientPermissionError(channel, true);
    }
    
    private void insufficientPermissionError(String channel, boolean acceptVoice ) {
        final String requiredPrefix = acceptVoice ? "+ or @" : "@";
        sendMessage(channel,"Only people with "+requiredPrefix+" can run this command.");
        // I noticed that sometimes the bot just get out of sync, so ask the sender to retry
        sendRawLine("NAMES " + channel);
        sendMessage(channel,"I'll refresh the member list, so if you think this is an error, try again in a few seconds.");
    }

    /**
     * Creates an issue tracker component.
     */
    private boolean createComponent(String channel, String sender, String subcomponent, String owner) {
        if (!isSenderAuthorized(channel,sender)) {
            insufficientPermissionError(channel);
            return false;
        }

        sendMessage(channel,String.format("Adding a new subcomponent %s to the bug tracker, owned by %s",subcomponent,owner));

        boolean result = false;
        try {
            JiraScraper js = new JiraScraper();
            js.createComponent(IrcBotConfig.JIRA_DEFAULT_PROJECT, subcomponent, owner, AssigneeType.COMPONENT_LEAD);
            sendMessage(channel,"New component created");
            result = true;
        } catch (Exception e) {
            sendMessage(channel,"Failed to create a new component: "+e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Renames an issue tracker component.
     */
    private void renameComponent(String channel, String sender, String oldName, String newName) {
        if (!isSenderAuthorized(channel,sender)) {
            insufficientPermissionError(channel);
            return;
        }

        sendMessage(channel,String.format("Renaming subcomponent %s to %s", oldName, newName));

        try {
            JiraScraper js = new JiraScraper();
            js.renameComponent(IrcBotConfig.JIRA_DEFAULT_PROJECT, oldName, newName);
            sendMessage(channel,"The component has been renamed");
        } catch (Exception e) {
            sendMessage(channel,e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Deletes an issue tracker component.
     */
    private void deleteComponent(String channel, String sender, String deletedComponent, String backupComponent) {
        if (!isSenderAuthorized(channel,sender)) {
            insufficientPermissionError(channel);
            return;
        }

        sendMessage(channel,String.format("Deleting the subcomponent %s. All issues will be moved to %s", deletedComponent, backupComponent));

        try {
            JiraScraper js = new JiraScraper();
            js.deleteComponent(IrcBotConfig.JIRA_DEFAULT_PROJECT, deletedComponent, backupComponent);
            sendMessage(channel,"The component has been deleted");
        } catch (Exception e) {
            sendMessage(channel,e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Deletes an assignee from the specified component
     */
    private void removeDefaultAssignee(String channel, String sender, String subcomponent) {
        setDefaultAssignee(channel, sender, subcomponent, null);
    }
    
    /**
     * Creates an issue tracker component.
     * @param owner User ID or null if the owner should be removed 
     */
    private void setDefaultAssignee(String channel, String sender, String subcomponent, String owner) {
        if (!isSenderAuthorized(channel,sender)) {
            insufficientPermissionError(channel);
            return;
        }
        
        sendMessage(channel,String.format("Changing default assignee of subcomponent %s to %s",subcomponent,owner));
        
        try {
            JiraScraper js = new JiraScraper();
            if (owner != null) {
                js.setDefaultAssignee(IrcBotConfig.JIRA_DEFAULT_PROJECT, subcomponent, AssigneeType.COMPONENT_LEAD, owner);
                sendMessage(channel,"Default assignee set to " + owner);
            } else {
                js.removeDefaultAssignee(IrcBotConfig.JIRA_DEFAULT_PROJECT, subcomponent, AssigneeType.COMPONENT_LEAD);
                sendMessage(channel,"Default assignee has been removed");
            }
        } catch (Exception e) {
            sendMessage(channel,"Failed to set default assignee: "+e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Sets the component description.
     * @param description Component description. Use null to remove the description
     */
    private void setComponentDescription(String channel, String sender, String componentName, String description) {
        if (!isSenderAuthorized(channel,sender)) {
            insufficientPermissionError(channel);
            return;
        }

        sendMessage(channel,String.format("Updating the description of component %s", componentName));

        try {
            JiraScraper js = new JiraScraper();
            js.setComponentDescription(IrcBotConfig.JIRA_DEFAULT_PROJECT, componentName, description);        
            sendMessage(channel,"The component description has been " + (description != null ? "updated" : "removed"));
        } catch (Exception e) {
            sendMessage(channel,e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void grantAutoVoice(String channel, String sender, String target) {
        if (!isSenderAuthorized(channel,sender)) {
          insufficientPermissionError(channel);
          return;
        }

        sendMessage("CHANSERV", "flags " + channel + " " + target + " +V");
        sendMessage("CHANSERV", "voice " + channel + " " + target);
        sendMessage(channel, "Voice privilege (+V) added for " + target);
    }

    private void removeAutoVoice(String channel, String sender, String target) {
      if (!isSenderAuthorized(channel,sender)) {
        insufficientPermissionError(channel);
        return;
      }

      sendMessage("CHANSERV", "flags " + channel + " " + target + " -V");
      sendMessage("CHANSERV", "devoice " + channel + " " + target);
      sendMessage(channel, "Voice privilege (-V) removed for " + target);
    }

    private void createGitHubRepository(String channel, String sender, String name, String collaborator) {
        try {
            if (!isSenderAuthorized(channel,sender)) {
                insufficientPermissionError(channel);
                return;
            }

            GitHub github = GitHub.connect();
            GHOrganization org = github.getOrganization(IrcBotConfig.GITHUB_ORGANIZATION);
            GHRepository r = org.createRepository(name, "", "", IrcBotConfig.GITHUB_DEFAULT_TEAM, true);
            setupRepository(r);

            GHTeam t = getOrCreateRepoLocalTeam(org, r);
            if (collaborator!=null)
                t.add(github.getUser(collaborator));

            sendMessage(channel,"New github repository created at "+r.getUrl());
        } catch (IOException e) {
            sendMessage(channel,"Failed to create a repository: "+e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Adds a new collaborator to existing repositories.
     *
     * @param justForThisRepo
     *      Null to add to add the default team ("Everyone"), otherwise add him to a team specific repository.
     */
    private boolean addGitHubCommitter(String channel, String sender, String collaborator, String justForThisRepo) {
        boolean result = false;
        if (!isSenderAuthorized(channel,sender)) {
            insufficientPermissionError(channel);
            return false;
        }
        try {
            GitHub github = GitHub.connect();
            GHUser c = github.getUser(collaborator);
            GHOrganization o = github.getOrganization(IrcBotConfig.GITHUB_ORGANIZATION);
            
            final GHTeam t;
            if (justForThisRepo != null) {
                GHRepository forThisRepo = o.getRepository(justForThisRepo);
                 if (forThisRepo == null) {
                     sendMessage(channel,"Could not find repository:  "+justForThisRepo);
                     return false;
                 }
                 t = getOrCreateRepoLocalTeam(o, forThisRepo);
            } else {
                t = o.getTeams().get(IrcBotConfig.GITHUB_DEFAULT_TEAM);
            }
                
            if (t==null) {
                sendMessage(channel,"No team for "+justForThisRepo);
                return false;
            }

            t.add(c);
            String successMsg = "Added "+collaborator+" as a GitHub committer";
            if (justForThisRepo != null) {
                successMsg += " for repository " + justForThisRepo;
            }
            sendMessage(channel,successMsg);
            result = true;
        } catch (IOException e) {
            sendMessage(channel,"Failed to create a repository: "+e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    private void renameGitHubRepo(String channel, String sender, String repo, String newName) {
        try {
            if (!isSenderAuthorized(channel, sender, false)) {
                insufficientPermissionError(channel, false);
                return;
            }
            sendMessage(channel, "Renaming " + repo + " to " + newName);

            GitHub github = GitHub.connect();
            GHOrganization o = github.getOrganization(IrcBotConfig.GITHUB_ORGANIZATION);

            GHRepository orig = o.getRepository(repo);
            if (orig == null) {
                sendMessage(channel, "No such repository: " + repo);
                return;
            }

            orig.renameTo(newName);
            sendMessage(channel, "The repository has been renamed: https://github.com/" + IrcBotConfig.GITHUB_ORGANIZATION+"/"+newName);
        } catch (IOException e) {
            sendMessage(channel, "Failed to rename a repository: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * @param newName
     *      If not null, rename a epository after a fork.
     */
    private boolean forkGitHub(String channel, String sender, String owner, String repo, String newName) {
        boolean result = false;
        try {
            if (!isSenderAuthorized(channel,sender)) {
                insufficientPermissionError(channel);
                return false;
            }

            sendMessage(channel, "Forking "+repo);

            GitHub github = GitHub.connect();
            GHUser user = github.getUser(owner);
            if (user==null) {
                sendMessage(channel,"No such user: "+owner);
                return false;
            }
            GHRepository orig = user.getRepository(repo);
            if (orig==null) {
                sendMessage(channel,"No such repository: "+repo);
                return false;
            }

            GHOrganization org = github.getOrganization(IrcBotConfig.GITHUB_ORGANIZATION);
            GHRepository r;
            try {
                r = orig.forkTo(org);
            } catch (IOException e) {
                // we started seeing 500 errors, presumably due to time out.
                // give it a bit of time, and see if the repository is there
                System.out.println("GitHub reported that it failed to fork "+owner+"/"+repo+". But we aren't trusting");
                r = null;
                for (int i=0; r==null && i<5; i++) {
                    Thread.sleep(1000);
                    r = org.getRepository(repo);
                }
                if (r==null)
                    throw e;
            }
            if (newName!=null) {
                r.renameTo(newName);

                r = null;
                for (int i=0; r==null && i<5; i++) {
                    Thread.sleep(1000);
                    r = org.getRepository(newName);
                }
                if (r==null)
                    throw new IOException(repo+" renamed to "+newName+" but not finding the new repository");
            }

            // GitHub adds a lot of teams to this repo by default, which we don't want
            Set<GHTeam> legacyTeams = r.getTeams();

            GHTeam t = getOrCreateRepoLocalTeam(org, r);
            try {
                t.add(user);    // the user immediately joins this team
                
            } catch (IOException e) {
                // if 'user' is an org, the above command would fail
                sendMessage(channel,"Failed to add "+user+" to the new repository. Maybe an org?: "+e.getMessage());
                // fall through
            }

            

            setupRepository(r);

            sendMessage(channel, "Created https://github.com/" + IrcBotConfig.GITHUB_ORGANIZATION + "/" + (newName != null ? newName : repo));

            // remove all the existing teams
            for (GHTeam team : legacyTeams)
                team.remove(r);

            result = true;
        } catch (InterruptedException e) {
            sendMessage(channel,"Failed to fork a repository: "+e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            sendMessage(channel,"Failed to fork a repository: "+e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Fix up the repository set up to our policy.
     */
    private void setupRepository(GHRepository r) throws IOException {
        r.setEmailServiceHook(IrcBotConfig.GITHUB_POST_COMMIT_HOOK_EMAIL);
        r.enableIssueTracker(false);
        r.enableWiki(false);
        r.createHook(IrcBotConfig.IRC_HOOK_NAME,
                IrcBotConfig.getIRCHookConfig(), (Collection<GHEvent>) null,
                true);
    }

    /**
     * Creates a repository local team, and grants access to the repository.
     */
    private GHTeam getOrCreateRepoLocalTeam(GHOrganization org, GHRepository r) throws IOException {
        String teamName = r.getName() + " Developers";
        GHTeam t = org.getTeams().get(teamName);
        if (t==null) {
            t = org.createTeam(teamName, Permission.ADMIN, r);
        } else {
            if (!t.getRepositories().containsValue(r)) {
                t.add(r);
            }
        }
        return t;
    }

    public static void main(String[] args) throws Exception {
        IrcBotImpl bot = new IrcBotImpl(new File("unknown-commands.txt"));
        System.out.println("Connecting to "+IrcBotConfig.SERVER+" as "+IrcBotConfig.NAME);
        System.out.println("GitHub organization = "+IrcBotConfig.GITHUB_ORGANIZATION);
        bot.connect(IrcBotConfig.SERVER);
        bot.setVerbose(true);
        for (String channel : IrcBotConfig.getChannels()) {
            bot.joinChannel(channel);
        }
        if (args.length>0) {
            System.out.println("Authenticating with NickServ");
            bot.sendMessage("nickserv","identify "+args[0]);
        }
    }

    static {
        /*
            I started seeing the SSL related problem. Given that there's no change in project_tools.html
            that include this Google Analytics, I'm not really sure why this is happening.
            Until I sort it out, I'm just dumbing down HTTPS into HTTP. Given that this only runs in one place
            and its network is well protected, I don't think this enables attacks to the system.


1270050442632 ### java.lang.RuntimeException: Error loading included script: java.io.IOException: HTTPS hostname wrong:  should be <ssl.google-analytics.com>
1270050442632 ###       at com.meterware.httpunit.ParsedHTML.getScript(ParsedHTML.java:291)
1270050442632 ###       at com.meterware.httpunit.ParsedHTML.interpretScriptElement(ParsedHTML.java:269)
1270050442632 ###       at com.meterware.httpunit.ParsedHTML.access$600(ParsedHTML.java:37)
1270050442632 ###       at com.meterware.httpunit.ParsedHTML$ScriptFactory.recordElement(ParsedHTML.java:404)
1270050442632 ###       at com.meterware.httpunit.ParsedHTML$2.processElement(ParsedHTML.java:556)
1270050442632 ###       at com.meterware.httpunit.NodeUtils$PreOrderTraversal.perform(NodeUtils.java:169)
1270050442632 ###       at com.meterware.httpunit.ParsedHTML.loadElements(ParsedHTML.java:566)
1270050442632 ###       at com.meterware.httpunit.ParsedHTML.getForms(ParsedHTML.java:101)
1270050442632 ###       at com.meterware.httpunit.WebResponse.getForms(WebResponse.java:311)
1270050442632 ###       at org.kohsuke.jnt.JNMembership.grantRole(JNMembership.java:200)
1270050442632 ###       at org.jvnet.hudson.backend.ircbot.IrcBotImpl.grantCommitAccess(IrcBotImpl.java:181)
1270050442632 ###       at org.jvnet.hudson.backend.ircbot.IrcBotImpl.onMessage(IrcBotImpl.java:74)
1270050442632 ###       at org.jibble.pircbot.PircBot.handleLine(PircBot.java:927)
1270050442633 ###       at org.jibble.pircbot.InputThread.run(InputThread.java:95)
         */
        class TrustAllManager implements X509TrustManager {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
            }

            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
            }
        }

        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, new TrustManager[]{new TrustAllManager()}, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (GeneralSecurityException e) {
            throw new Error(e);
        }
    }
}
