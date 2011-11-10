package org.jvnet.hudson.backend.ircbot;

import com.atlassian.jira.rest.client.domain.AssigneeType;
import hudson.plugins.jira.soap.JiraSoapService;
import hudson.plugins.jira.soap.JiraSoapServiceServiceLocator;
import hudson.plugins.jira.soap.RemoteIssue;
import hudson.plugins.jira.soap.RemoteStatus;
import org.apache.axis.collections.LRUMap;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.jira_scraper.ConnectionInfo;
import org.jenkinsci.jira_scraper.JiraScraper;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
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
import javax.xml.rpc.ServiceException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.rmi.RemoteException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Collections;
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
    /**
     * Records commands that we didn't understand.
     */
    private File unknownCommands;

    /**
     * Map from the issue number to the time it was last mentioned.
     * Used so that we don't repeatedly mention the same issues.
     */
    private final Map recentIssues = Collections.synchronizedMap(new LRUMap(10));

    public IrcBotImpl(File unknownCommands) {
        setName("jenkins-admin");
        this.unknownCommands = unknownCommands;
    }

    @Override
    protected void onMessage(String channel, String sender, String login, String hostname, String message) {
        if (!channel.equals("#jenkins"))     return; // not in this channel
        if (sender.equals("jenkinsci_builds"))   return; // ignore messages from other bots

        String prefix = getNick() + ":";
        if (!message.startsWith(prefix)) {
            // not send to me
            Matcher m = Pattern.compile("(?:hudson-|jenkins-|bug )([0-9]+)",CASE_INSENSITIVE).matcher(message);
            while (m.find()) {
                replyBugStatus(channel,m.group(1));
            }
            return;
        }

        String payload = message.substring(prefix.length(), message.length()).trim();
        // replace duplicate whitespace with a single space
        payload = payload.replaceAll("\\s+", " ");
        Matcher m;

        m = Pattern.compile("(?:create|make|add) (\\S+)(?: repository)? (?:on|in) github(?: for (\\S+))?",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            createGitHubRepository(channel, m.group(1), m.group(2));
            return;
        }

        m = Pattern.compile("fork (\\S+)/(\\S+)(?: on github)?(?: as (\\S+))?",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            forkGitHub(channel, m.group(1),m.group(2),m.group(3));
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
            sendRawLine("NAMES #jenkins");
            return;
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

    private void replyBugStatus(String channel, String number) {
        Long time = (Long)recentIssues.get(number);

        recentIssues.put(number,System.currentTimeMillis());

        if (time!=null) {
            if (System.currentTimeMillis()-time < 60*1000) {
                return; // already mentioned recently. don't repeat
            }
        }

        try {
            sendMessage(channel, getSummary(number));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getSummary(String number) throws ServiceException, IOException {
        JiraSoapService svc = new JiraSoapServiceServiceLocator().getJirasoapserviceV2(new URL("http://issues.jenkins-ci.org/rpc/soap/jirasoapservice-v2"));
        ConnectionInfo con = new ConnectionInfo();
        String token = svc.login(con.userName, con.password);
        RemoteIssue issue = svc.getIssue(token, "JENKINS-" + number);
        return String.format("%s:%s (%s) %s",
                issue.getKey(), issue.getSummary(), findStatus(svc,token,issue.getStatus()).getName(), "http://jenkins-ci.org/issue/"+number);
    }

    private RemoteStatus findStatus(JiraSoapService svc, String token, String statusId) throws RemoteException {
        RemoteStatus[] statuses = svc.getStatuses(token);
        for (RemoteStatus s : statuses)
            if(s.getId().equals(statusId))
                return s;
        return null;
    }

    /**
     * Is the sender respected in the channel?
     *
     * IOW, does he have a voice of a channel operator?
     */
    private boolean isSenderAuthorized(String channel, String sender) {
        for (User u : getUsers(channel)) {
            System.out.println(u.getPrefix()+u.getNick());
            if (u.getNick().equals(sender)) {
                String p = u.getPrefix();
                if (p.contains("@") || p.contains("+"))
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
                joinChannel("#jenkins");
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Thread.sleep(3000);
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
            String v = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("version.txt"));
            sendMessage(channel,"My version is "+v);
        } catch (IOException e) {
            e.printStackTrace();
            sendMessage(channel,"I don't know who I am");
        }
    }

    private void insufficientPermissionError(String channel) {
        sendMessage(channel,"Only people with + or @ can run this command.");
        // I noticed that sometimes the bot just get out of sync, so ask the sender to retry
        sendRawLine("NAMES #jenkins");
        sendMessage(channel,"I'll refresh the member list, so if you think this is an error, try again in a few seconds.");
    }

    /**
     * Creates an issue tracker component.
     */
    private void createComponent(String channel, String sender, String subcomponent, String owner) {
        if (!isSenderAuthorized(channel,sender)) {
            insufficientPermissionError(channel);
            return;
        }

        sendMessage(channel,String.format("Adding a new subcomponent %s to the bug tracker, owned by %s",subcomponent,owner));

        try {
            JiraScraper js = new JiraScraper();
            js.createComponent("JENKINS", subcomponent, owner, AssigneeType.COMPONENT_LEAD);
            sendMessage(channel,"New component created");
        } catch (Exception e) {
            sendMessage(channel,"Failed to create a new component: "+e.getMessage());
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
        sendMessage(channel, "Voice priviledge (+V) added for " + target);
    }

    private void removeAutoVoice(String channel, String sender, String target) {
      if (!isSenderAuthorized(channel,sender)) {
        insufficientPermissionError(channel);
        return;
      }

      sendMessage("CHANSERV", "flags " + channel + " " + target + " -V");
      sendMessage("CHANSERV", "devoice " + channel + " " + target);
      sendMessage(channel, "Voice priviledge (-V) removed for " + target);
    }

    private void createGitHubRepository(String channel, String name, String collaborator) {
        try {
            GitHub github = GitHub.connect();
            GHOrganization org = github.getOrganization("jenkinsci");
            GHRepository r = org.createRepository(name, "", "", "Everyone", true);
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
     *      Null to add to "Everyone", otherwise add him to a team specific repository.
     */
    private void addGitHubCommitter(String channel, String sender, String collaborator, String justForThisRepo) {
        if (!isSenderAuthorized(channel,sender)) {
            insufficientPermissionError(channel);
            return;
        }
        try {
            GitHub github = GitHub.connect();
            GHUser c = github.getUser(collaborator);
            GHOrganization o = github.getOrganization("jenkinsci");
            GHTeam t = justForThisRepo==null ? o.getTeams().get("Everyone") : o.getTeams().get(justForThisRepo+" Developers");
            if (t==null) {
                sendMessage(channel,"No team for "+justForThisRepo);
                return;
            }

            t.add(c);
            sendMessage(channel,"Added "+collaborator+" as a GitHub committer");
        } catch (IOException e) {
            sendMessage(channel,"Failed to create a repository: "+e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * @param newName
     *      If not null, rename a epository after a fork.
     */
    private void forkGitHub(String channel, String owner, String repo, String newName) {
        try {
            sendMessage(channel, "Forking "+repo);

            GitHub github = GitHub.connect();
            GHUser user = github.getUser(owner);
            if (user==null) {
                sendMessage(channel,"No such user: "+owner);
                return;
            }
            GHRepository orig = user.getRepository(repo);
            if (orig==null) {
                sendMessage(channel,"No such repository: "+repo);
                return;
            }

            GHOrganization org = github.getOrganization("jenkinsci");
            GHRepository r;
            try {
                r = orig.forkTo(org);
            } catch (IOException e) {
                // we started seeing 500 errors, presumably due to time out.
                // give it a bit of time, and see if the repository is there
                System.out.println("GitHub reported that it failed to fork "+owner+"/"+repo+". But we aren't trusting");
                Thread.sleep(3000);
                r = org.getRepository(repo);
                if (r==null)
                    throw e;
            }
            if (newName!=null)
                r.renameTo(newName);

            // GitHub adds a lot of teams to this repo by default, which we don't want
            Set<GHTeam> legacyTeams = r.getTeams();

            GHTeam t = getOrCreateRepoLocalTeam(org, r);
            t.add(user);    // the user immediately joins this team

            // the Everyone group gets access to this new repository, too.
            GHTeam everyone = org.getTeams().get("Everyone");
            everyone.add(r);

            setupRepository(r);

            sendMessage(channel, "Created https://github.com/jenkinsci/" + (newName != null ? newName : repo));

            // remove all the existing teams
            for (GHTeam team : legacyTeams)
                team.remove(r);

        } catch (InterruptedException e) {
            sendMessage(channel,"Failed to fork a repository: "+e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            sendMessage(channel,"Failed to fork a repository: "+e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Fix up the repository set up to our policy.
     */
    private void setupRepository(GHRepository r) throws IOException {
        r.setEmailServiceHook(POST_COMMIT_HOOK_EMAIL);
        r.enableIssueTracker(false);
        r.enableWiki(false);
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
            t.add(r);
        }
        return t;
    }

    public static void main(String[] args) throws Exception {
        IrcBotImpl bot = new IrcBotImpl(new File("unknown-commands.txt"));
        bot.connect("irc.freenode.net");
        bot.setVerbose(true);
        bot.joinChannel("#jenkins");
        if (args.length>0) {
            System.out.println("Authenticating with NickServ");
            bot.sendMessage("nickserv","identify "+args[0]);
        }
//
//        bot.setDefaultAssignee("kktest4",DefaultAssignee.COMPONENT_LEAD);
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

    static final String POST_COMMIT_HOOK_EMAIL = "jenkinsci-commits@googlegroups.com";
}
