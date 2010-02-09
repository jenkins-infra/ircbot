package org.jvnet.hudson.backend.ircbot;

import com.meterware.httpunit.ClientProperties;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebForm;
import com.meterware.httpunit.WebResponse;
import org.cyberneko.html.parsers.SAXParser;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import org.kohsuke.jnt.ConnectionInfo;
import org.kohsuke.jnt.JavaNet;
import org.kohsuke.jnt.ProcessingException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IRC Bot on irc.freenode.net as a means to delegate administrative work to committers.
 *
 * @author Kohsuke Kawaguchi
 */
public class IrcBotImpl extends PircBot {
    public IrcBotImpl() {
        setName("hudson-admin");
    }

    @Override
    protected void onMessage(String channel, String sender, String login, String hostname, String message) {
        if (!channel.equals("#hudson"))     return; // not in this channel

        String prefix = getNick() + ":";
        if (!message.startsWith(prefix))  return;   // not send to me

        String payload = message.substring(prefix.length(), message.length()).trim().toLowerCase();

        Matcher m = Pattern.compile("(?:make|give|grant) (\\S+) (a )?(committer|commit access).*").matcher(payload);
        if (m.matches()) {
            grantCommitAccess(channel, sender, m.group(1));
            return;
        }

        m = Pattern.compile("(?:create|make) (\\S+)(?: component)? in (?:the )?(?:issue|bug)(?: tracker| database)? for (\\S+)").matcher(payload);
        if (m.matches()) {
            createComponent(channel, sender, m.group(1), m.group(2));
            return;
        }

        if (payload.equals("help")) {
            help(channel);
            return;
        }

        if (payload.equals("refresh")) {
            // get the updated list
            sendRawLine("NAMES #hudson");
            return;
        }

        sendMessage(channel,"I didn't understand the command");
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
        IrcBotImpl bot = new IrcBotImpl();
        bot.connect("irc.freenode.net");
        bot.setVerbose(true);
        bot.joinChannel("#hudson");
    }

    static {
        // HttpUnit can't handle gzip-encoded content with Content-Length==-1,
        // so disable gzip support
        ClientProperties.getDefaultProperties().setAcceptGzip(false);
    }
}
