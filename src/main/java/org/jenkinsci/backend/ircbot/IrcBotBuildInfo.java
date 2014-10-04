/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jenkinsci.backend.ircbot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Contains the info about IRC Bot Build.
 * The version info will be taken from versionInfo.properties file.
 * @author Oleg Nenashev <o.v.nenashev@gmail.com>
 */
/*package*/ class IrcBotBuildInfo {
    private final String buildNumber;
    private final String buildDate;
    private final String buildID;
    private final String buildURL;
    private final String gitCommit;

    private IrcBotBuildInfo(String buildNumber, String buildDate, String buildID, String buildURL, String gitCommit) {
        this.buildNumber = buildNumber;
        this.buildDate = buildDate;
        this.buildID = buildID;
        this.buildURL = buildURL;
        this.gitCommit = gitCommit;
    }

    public String getBuildID() {
        return buildID;
    }

    public String getBuildDate() {
        return buildDate;
    }
    
    public String getBuildNumber() {
        return buildNumber;
    }

    public String getBuildURL() {
        return buildURL;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    @Override
    public String toString() {
        return String.format("build%s %s (%s)", buildNumber, gitCommit, buildDate);
    }
    
    private static String readProperty(Properties propFile, String key) throws IOException {
        String value = propFile.getProperty(key);
        if (value == null) {
            throw new IOException("Property "+key+" does not exist");
        }
        return value;
    }
    
    public static IrcBotBuildInfo readResourceFile (String resourcePath) throws IOException {
        InputStream istream = IrcBotBuildInfo.class.getResourceAsStream(resourcePath);      
        if (istream == null) {
            throw new IOException("Cannot find resource "+resourcePath);
        }
        try {
            return readFile(istream);
        } finally {
            istream.close();
        }
    }
    
    public static IrcBotBuildInfo readFile(InputStream istream) throws IOException {
        final Properties prop = new Properties();
        prop.load(istream);
   
        return new IrcBotBuildInfo(
                readProperty(prop,"buildNumber"), 
                readProperty(prop,"buildDate"),
                readProperty(prop,"buildID"),
                readProperty(prop,"buildURL"), 
                readProperty(prop,"gitCommit"));
    }
}
