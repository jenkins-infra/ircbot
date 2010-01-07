package org.jvnet.hudson.backend.ircbot;

import com.meterware.httpunit.ClientProperties;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebResponse;
import org.cyberneko.html.parsers.SAXParser;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import org.kohsuke.jnt.ConnectionInfo;
import org.kohsuke.jnt.JavaNet;
import org.kohsuke.jnt.ProcessingException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
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
            sendMessage(channel,"Only people with + or @ can run this command.");
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

    /**
     * Creates an issue tracker component.
     */
    private void createComponent(String channel, String sender, String subcomponent, String owner) {
        if (!isSenderAuthorized(channel,sender)) {
            sendMessage(channel,"Only people with + or @ can run this command.");
            return;
        }

        sendMessage(channel,String.format("Adding a new subcomponent %s to the bug tracker, owned by %s",subcomponent,owner));

        try {
            createComponent(subcomponent, owner);
            sendMessage(channel,"New component created");
        } catch (IOException e) {
            sendMessage(channel,"Failed to create a new component"+e.getMessage());
            e.printStackTrace();
        } catch (SAXException e) {
            sendMessage(channel,"Failed to create a new component"+e.getMessage());
            e.printStackTrace();
        } catch (ProcessingException e) {
            sendMessage(channel,"Failed to create a new component: "+e.getMessage());
            e.printStackTrace();
        } catch (DocumentException e) {
            sendMessage(channel,"Failed to create a new component"+e.getMessage());
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
        req.setParameter("pid","10172"); // TODO: use JIRA SOAP API to get this ID.
        req.setParameter("os_username",con.userName);
        req.setParameter("os_password",con.password);
        req.setParameter("name",subcomponent);
        req.setParameter("description",subcomponent+" plugin");
        req.setParameter("componentLead",owner);
        WebResponse rsp = wc.getResponse(req);

        // did it result in an error?
        Document tree = new SAXReader(new SAXParser()).read(new StringReader(rsp.getText()));
        Element e = (Element) tree.selectSingleNode("//*[@class='errMsg']");
        if (e!=null)
            throw new ProcessingException(e.getTextTrim());
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
