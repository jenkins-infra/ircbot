package org.jvnet.hudson.backend.ircbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @author Kohsuke Kawaguchi
 */
public class ConnectionInfo {
    public String userName,password;
    public ConnectionInfo() throws IOException {
        File f = new File(new File(System.getProperty("user.home")),".jenkins-ci.org");
        Properties prop = new Properties();
        prop.load(new FileInputStream(f));
        userName = prop.getProperty("userName");
        password = prop.getProperty("password");
    }
}
