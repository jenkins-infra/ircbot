package org.jvnet.hudson.backend.ircbot;

import com.meterware.httpunit.ClientProperties;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebForm;
import com.meterware.httpunit.WebResponse;
import hudson.plugins.jira.soap.JiraSoapService;
import hudson.plugins.jira.soap.JiraSoapServiceServiceLocator;
import hudson.plugins.jira.soap.RemoteIssue;
import hudson.plugins.jira.soap.RemoteStatus;
import org.apache.commons.io.IOUtils;
import org.cyberneko.html.parsers.SAXParser;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHOrganization.Permission;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.jnt.ConnectionInfo;
import org.kohsuke.jnt.JavaNet;
import org.kohsuke.jnt.ProcessingException;
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
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

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

    public IrcBotImpl(File unknownCommands) {
        setName("hudson-admin");
        this.unknownCommands = unknownCommands;
    }

    @Override
    protected void onMessage(String channel, String sender, String login, String hostname, String message) {
        if (!channel.equals("#hudson"))     return; // not in this channel
        if (sender.equals("hudsonci_builds"))   return; // ignore messages from other bots

        String prefix = getNick() + ":";
        if (!message.startsWith(prefix)) {
            // not send to me
            Matcher m = Pattern.compile("(?:hudson-|bug )([0-9]+)",CASE_INSENSITIVE).matcher(message);
            while (m.find()) {
                replyBugStatus(channel,m.group(1));
            }
            return;
        }

        String payload = message.substring(prefix.length(), message.length()).trim();
        Matcher m;

        m = Pattern.compile("(?:create|make|add) (\\S+)(?: repository)? (?:on|in) github(?: for (\\S+))?",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            createGitHubRepository(channel, m.group(1), m.group(2));
            return;
        }

        m = Pattern.compile("add (\\S+) as (a )?github (collaborator|committ?er)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            addGitHubCommitter(channel, m.group(1),null);
            return;
        }

        m = Pattern.compile("add (\\S+) as (a )?github (collaborator|committ?er) to (\\S+)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            addGitHubCommitter(channel, m.group(1), m.group(4));
            return;
        }

        m = Pattern.compile("fork (\\S+)/(\\S+) on github(?: as (\\S+))?",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            forkGitHub(channel, m.group(1),m.group(2),m.group(3));
            return;
        }

        m = Pattern.compile("(?:make|give|grant|add) (\\S+) (a )?(committ?er|commit access) (on|in) github",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            addGitHubCommitter(channel, m.group(1),null);
            return;
        }

        m = Pattern.compile("(?:make|give|grant|add) (\\S+) (a )?(committ?er|commit access).*",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            grantCommitAccess(channel, sender, m.group(1));
            return;
        }

        m = Pattern.compile("(?:create|make|add) (\\S+)(?: component)? in (?:the )?(?:issue|bug)(?: tracker| database)? for (\\S+)",CASE_INSENSITIVE).matcher(payload);
        if (m.matches()) {
            createComponent(channel, sender, m.group(1), m.group(2));
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
            sendRawLine("NAMES #hudson");
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
        try {
            sendMessage(channel, getSummary(number));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getSummary(String number) throws ServiceException, RemoteException, ProcessingException, MalformedURLException {
        JiraSoapService svc = new JiraSoapServiceServiceLocator().getJirasoapserviceV2(new URL("http://issues.hudson-ci.org/rpc/soap/jirasoapservice-v2"));
        ConnectionInfo con = new ConnectionInfo();
        String token = svc.login(con.userName, con.password);
        RemoteIssue issue = svc.getIssue(token, "HUDSON-" + number);
        return String.format("%s:%s (%s) %s",
                issue.getKey(), issue.getSummary(), findStatus(svc,token,issue.getStatus()).getName(), "http://hudson-ci.org/issue/"+number);
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
                joinChannel("#hudson");
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
        sendMessage(channel,"See http://wiki.hudson-ci.org/display/HUDSON/IRC+Bot");
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

    /**
     * Grant commit access to a new user.
     */
    private void grantCommitAccess(String channel, String sender, String id) {
        if (!isSenderAuthorized(channel,sender)) {
            insufficientPermissionError(channel);
            return;
        }

        sendMessage(channel,"Making "+id+" a committer");
        try {
            JavaNet jn = JavaNet.connect();
            jn.getProject("hudson").getMembership().grantRole(jn.getUser(id),"Developer");
            jn.getProject("maven2-repository").getMembership().grantRole(jn.getUser(id),"javatools>maven2-repository.Maven Publisher");
            sendMessage(channel,id +" is now a committer");
        } catch (ProcessingException e) {
            sendMessage(channel,"Failed to make "+id+" a committer: "+e.getMessage().replace('\n',' '));
            e.printStackTrace();
        }
    }

    private void insufficientPermissionError(String channel) {
        sendMessage(channel,"Only people with + or @ can run this command.");
        // I noticed that sometimes the bot just get out of sync, so ask the sender to retry
        sendRawLine("NAMES #hudson");
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

    /**
     * JIRA doesn't have the SOAP API to create a component, so we need to do this via a HTTP POST and page scraping.
     */
    private void createComponent(String subcomponent, String owner) throws ProcessingException, IOException, SAXException, DocumentException {
        ConnectionInfo con = new ConnectionInfo();
        WebConversation wc = new WebConversation();
        wc.setExceptionsThrownOnErrorStatus(true);
        
        PostMethodWebRequest req = new PostMethodWebRequest("http://issues.hudson-ci.org/secure/project/AddComponent.jspa");
        req.setParameter("pid", getProjectId());
        req.setParameter("os_username",con.userName);
        req.setParameter("os_password",con.password);
        req.setParameter("name",subcomponent);
        req.setParameter("description",subcomponent+" plugin");
        req.setParameter("componentLead",owner);
        checkError(wc.getResponse(req));
    }

    private void createGitHubRepository(String channel, String name, String collaborator) {
        try {
            GitHub github = GitHub.connect();
            GHOrganization org = github.getOrganization("hudson");
            GHRepository r = org.createRepository(name,"","","Everyone",true);

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
    private void addGitHubCommitter(String channel, String collaborator, String justForThisRepo) {
        try {
            GitHub github = GitHub.connect();
            GHUser c = github.getUser(collaborator);
            GHOrganization o = github.getOrganization("hudson");
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

            GHOrganization org = github.getOrganization("hudson");
            GHRepository r = orig.forkTo(org);
            if (newName!=null)
                r.renameTo(newName);

            GHTeam t = getOrCreateRepoLocalTeam(org, r);
            t.add(user);    // the user immediately joins this team

            // the Everyone group gets access to this new repository, too.
            GHTeam everyone = org.getTeams().get("Everyone");
            everyone.add(r);
            sendMessage(channel,"Created https://github.com/hudson/"+(newName!=null?newName:repo));
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
    private WebResponse checkError(WebResponse rsp) throws DocumentException, IOException, ProcessingException {
        System.out.println(rsp.getText());

        Document tree = asDom(rsp);
        Element e = (Element) tree.selectSingleNode("//*[@class='errMsg']");
        if (e==null)
            e = (Element) tree.selectSingleNode("//*[@class='errorArea']");
        if (e!=null) {
            StringWriter w = new StringWriter();
            new XMLWriter(w, OutputFormat.createCompactFormat()) {
                // just print text
                @Override
                protected void writeElement(Element element) throws IOException {
                    writeElementContent(element);
                }
            }.write(e);
            throw new ProcessingException(w.toString());
        }
        return rsp;
    }

    private Document asDom(WebResponse rsp) throws DocumentException, IOException {
        return new SAXReader(new SAXParser()).read(new StringReader(rsp.getText()));
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
        WebConversation wc = createAuthenticatedSession();

        WebResponse rsp = wc.getResponse("http://issues.hudson-ci.org/secure/project/SelectComponentAssignees!default.jspa?projectId="+getProjectId());
        Document dom = asDom(rsp);
        Element row = (Element)dom.selectSingleNode("//TABLE[@class='grid']/TR[TD[1]='"+component+"']");
        // figure out the name field
        Element r = (Element)row.selectSingleNode(".//INPUT[@type='radio']");
        String name = r.attributeValue("name");

        WebForm f = rsp.getFormWithName("jiraform");
        f.setParameter(name,String.valueOf(assignee.ordinal()));
        checkError(f.submit());
    }

    /**
     * Creates a conversation that's already logged in as the current user.
     */
    private WebConversation createAuthenticatedSession() throws ProcessingException, DocumentException, IOException, SAXException {
        ConnectionInfo con = new ConnectionInfo();
        WebConversation wc = new WebConversation();
        wc.setExceptionsThrownOnErrorStatus(true);

        PostMethodWebRequest req = new PostMethodWebRequest("http://issues.hudson-ci.org/login.jsp");
        req.setParameter("os_username",con.userName);
        req.setParameter("os_password",con.password);
        checkError(wc.getResponse(req));

        return wc;
    }

    public static void main(String[] args) throws Exception {
        IrcBotImpl bot = new IrcBotImpl(new File("unknown-commands.txt"));
        bot.connect("irc.freenode.net");
        bot.setVerbose(true);
        bot.joinChannel("#hudson");
    }

    static {
        // HttpUnit can't handle gzip-encoded content with Content-Length==-1,
        // so disable gzip support
        ClientProperties.getDefaultProperties().setAcceptGzip(false);


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
