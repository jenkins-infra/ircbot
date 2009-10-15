package org.jvnet.hudson.backend.ircbot;

import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.User;
import org.kohsuke.jnt.JavaNet;
import org.kohsuke.jnt.ProcessingException;
import org.kohsuke.jnt.JNIssueComponent;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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
            JavaNet jn = JavaNet.connect();
            JNIssueComponent comp = jn.getProject("hudson").getIssueTracker().getComponent("hudson");
            comp.add(subcomponent,subcomponent+" plugin",owner,owner);
            sendMessage(channel,"New component created");
        } catch (ProcessingException e) {
            sendMessage(channel,"Failed to create a new component: "+e.getMessage().replace('\n',' '));
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException, IrcException {
        IrcBotImpl bot = new IrcBotImpl();
        bot.connect("irc.freenode.net");
        bot.setVerbose(true);
        bot.joinChannel("#hudson");
    }
}
