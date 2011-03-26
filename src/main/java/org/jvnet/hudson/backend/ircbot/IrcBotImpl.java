package org.jvnet.hudson.backend.ircbot;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.plugins.jira.soap.JiraSoapService;
import hudson.plugins.jira.soap.JiraSoapServiceServiceLocator;
import hudson.plugins.jira.soap.RemoteIssue;
import hudson.plugins.jira.soap.RemoteStatus;
import org.apache.axis.collections.LRUMap;
import org.apache.commons.io.IOUtils;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHOrganization.Permission;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.xml.sax.SAXException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.rpc.ServiceException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.rmi.RemoteException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

        m = Pattern.compile("add (\\S+) as (a )?github (collaborator|committ?er)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            addGitHubCommitter(channel, sender, m.group(1),null);
            return;
        }

        m = Pattern.compile("add (\\S+) as (a )?github (collaborator|committ?er) to (\\S+)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            addGitHubCommitter(channel, sender, m.group(1), m.group(4));
            return;
        }

        m = Pattern.compile("fork (\\S+)/(\\S+) on github(?: as (\\S+))?",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            forkGitHub(channel, m.group(1),m.group(2),m.group(3));
            return;
        }

        m = Pattern.compile("(?:make|give|grant|add) (\\S+) (a )?(committ?er|commit access) (on|in) github",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            addGitHubCommitter(channel,sender,m.group(1),null);
            return;
        }

        m = Pattern.compile("(?:make|give|grant|add) (\\S+) (a )?(committ?er|commit access).*",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            addGitHubCommitter(channel,sender,m.group(1),null);
            return;
        }

        m = Pattern.compile("(?:create|make|add) (\\S+)(?: component)? in (?:the )?(?:issue|bug)(?: tracker| database)? for (\\S+)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            createComponent(channel, sender, m.group(1), m.group(2));
            return;
        }

        m = Pattern.compile("(?:make|give|grant|add) (\\S+) voice",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            grantAutoVoice(channel,sender,m.group(1));
            return;
        }

        m = Pattern.compile("(?:rem|remove|ungrant|del|delete) (\\S+) voice",CASE_INSENSITIVE).matcher(payload);
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
            createComponent(subcomponent, owner);
            setDefaultAssignee(subcomponent, DefaultAssignee.COMPONENT_LEAD);
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

    /**
     * JIRA doesn't have the SOAP API to create a component, so we need to do this via a HTTP POST and page scraping.
     */
    private void createComponent(String subcomponent, String owner) throws IOException, SAXException, DocumentException {
        WebClient wc = createAuthenticatedSession();

        HtmlPage p = wc.getPage("http://issues.jenkins-ci.org/secure/project/AddComponent!default.jspa?pid=" + getProjectId());
        HtmlForm f = p.getFormByName("jiraform");
        f.getInputByName("name").setValueAttribute(subcomponent);
        f.getTextAreaByName("description").setText(subcomponent + " plugin");
        f.getInputByName("componentLead").setValueAttribute(owner);
        checkError((HtmlPage) f.submit());
    }

    private void createGitHubRepository(String channel, String name, String collaborator) {
        try {
            GitHub github = GitHub.connect();
            GHOrganization org = github.getOrganization("jenkinsci");
            GHRepository r = org.createRepository(name,"","","Everyone",true);
            r.setEmailServiceHook(POST_COMMIT_HOOK_EMAIL);

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
            GHRepository r = orig.forkTo(org);
            if (newName!=null)
                r.renameTo(newName);

            GHTeam t = getOrCreateRepoLocalTeam(org, r);
            t.add(user);    // the user immediately joins this team

            // the Everyone group gets access to this new repository, too.
            GHTeam everyone = org.getTeams().get("Everyone");
            everyone.add(r);

            r.setEmailServiceHook(POST_COMMIT_HOOK_EMAIL);

            sendMessage(channel, "Created https://github.com/jenkinsci/" + (newName != null ? newName : repo));
        } catch (IOException e) {
            sendMessage(channel,"Failed to fork a repository: "+e.getMessage());
            e.printStackTrace();
        }
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

    /**
     * Check if this submission resulted in an error, and if so, report an exception.
     */
    private  HtmlPage checkError(HtmlPage rsp) throws DocumentException, IOException {
        System.out.println(rsp.getWebResponse().getContentAsString());

        HtmlElement e = (HtmlElement) rsp.selectSingleNode("//*[@class='errMsg']");
        if (e==null)
            e = (HtmlElement) rsp.selectSingleNode("//*[@class='errorArea']");
        if (e!=null) {
            StringWriter w = new StringWriter();
            new XMLWriter(w, OutputFormat.createCompactFormat()) {
                // just print text
                @Override
                protected void writeElement(Element element) throws IOException {
                    writeElementContent(element);
                }
            }.write(e);
            throw new IOException(w.toString());
        }
        return rsp;
    }

    private String getProjectId() {
        // TODO: use JIRA SOAP API to get this ID.
        return "10172";
    }

    enum DefaultAssignee {
        PROJECT_DEFAULT,
        COMPONENT_LEAD,
        PROJECT_LEAD,
        UNASSIGNED
    }

    private void setDefaultAssignee(String component, DefaultAssignee assignee) throws Exception {
        WebClient wc = createAuthenticatedSession();

        HtmlPage rsp = wc.getPage("http://issues.jenkins-ci.org/secure/project/SelectComponentAssignees!default.jspa?projectId=" + getProjectId());
        List<HtmlElement> rows = rsp.selectNodes("//TABLE[@class='grid']//TR");   // [TD[1]='COMPONENTNAME'] somehow doesn't work any more. how come?

        for (HtmlElement row : rows) {
            String caption = ((HtmlElement)row.selectSingleNode("TD[1]")).getTextContent();
            if (caption.equals(component)) {
                // figure out the name field
                HtmlElement r = (HtmlElement)row.selectSingleNode(".//INPUT[@type='radio']");
                String name = r.getAttribute("name");

                HtmlForm f = rsp.getFormByName("jiraform");
                f.getInputByName(name).setValueAttribute(String.valueOf(assignee.ordinal()));
                checkError((HtmlPage)f.submit());
                return;
            }
        }

        throw new IOException("Unable to find component "+component+" in the issue tracker");
    }

    /**
     * Creates a conversation that's already logged in as the current user.
     */
    private WebClient createAuthenticatedSession() throws DocumentException, IOException, SAXException {
        ConnectionInfo con = new ConnectionInfo();

        WebClient wc = new WebClient();
        wc.setJavaScriptEnabled(false);
        HtmlPage p = wc.getPage("http://issues.jenkins-ci.org/login.jsp");
        HtmlForm f = (HtmlForm)p.getElementById("login-form");
        f.getInputByName("os_username").setValueAttribute(con.userName);
        f.getInputByName("os_password").setValueAttribute(con.password);
        checkError((HtmlPage) f.submit());

        return wc;
    }

    public static void main(String[] args) throws Exception {
        IrcBotImpl bot = new IrcBotImpl(new File("unknown-commands.txt"));
        bot.connect("irc.freenode.net");
        bot.setVerbose(true);
        bot.joinChannel("#jenkins");
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
