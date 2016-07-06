package org.jenkinsci.backend.ircbot.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Stores JIRA connection credentials.
 * Originally this code 
 * @author Kohsuke Kawaguchi
 */
public class ConnectionInfo {
    
    public String userName,password;
    
    public ConnectionInfo() throws IOException {
        this(new File(new File(System.getProperty("user.home")),".jenkins-ci.org"));
    }
    
    /*package*/ ConnectionInfo(File f) throws IOException {
        Properties prop = new Properties();
        InputStream propInputStream = new FileInputStream(f);
        try {
            prop.load(propInputStream);
        } finally {
            propInputStream.close();
        }
        userName = prop.getProperty("userName");
        password = prop.getProperty("password");
    }
}
