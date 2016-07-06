# JIRA/GitHub management IRCBot
[![Build Status](https://ci.jenkins-ci.org/view/Infrastructure/job/Containers/job/infra_ircbot/badge/icon)](https://ci.jenkins-ci.org/view/Infrastructure/job/Containers/job/infra_ircbot/)

This IRC bot sits on `#jenkins` as `jenkins-admin` and allow users to create/fork repositories on GitHub, etc. More info: [IRC Bot Wiki][1]

## Deployment
This repo is containerized, then deployed to our infrastructure via Puppet. 
You should have a Write permission to https://github.com/jenkins-infra/ircbot and https://github.com/jenkins-infra/jenkins-infra to deploy the new version of the Bot

Actions:

1. Commit/merge changes into the master branch
2. Wait till the preparation of Docker package on Jenkins INFRA 
 * Go to the [IRC Bot job][2] on https://ci.jenkins-ci.org
 * Wait till the automatic build finishes with a SUCCESS status
4. Modify the version on Puppet infrastructure
 *  Edit your <b>local fork</b> the following file: https://github.com/jenkins-infra/jenkins-infra/blob/staging/hieradata/common.yaml#L94  
 * Change the `profile::jenkinsadmin::image_tag` variable.
   * Format: `build${JENKINSCI_BUILD_NUMBER}`
 * Create a pull request to the main repo. Branch=staging
 * Wait till the merge of the pull request. Write to #jenkins-infra channel to request the review
5. Wait till the deployment
 * See first steps of the deployment process on https://jenkins.ci.cloudbees.com/job/infra/job/jenkins-infra
 * The further deployment will be performed asynchronously (puppet checks for changes once per 15 minutes)
   * <code>jenkins-admin</code> will leave and join the chat
   * infra-butler will mention in #jenkins-infra that Spinach was updated

## License
[MIT License][3]


## Developer guide

This section contains some info for developers.

### Reusing IRCBot in non-Jenkins project

The bot is designed to be used in Jenkins, but it can be adjusted in other projects, 
which use the similar infrastructure (GitHub, IRC, JIRA). 
Adjustements can be made via System properties.
These properties are located and documented in the 
<code>org.jenkinsci.backend.ircbot.IrcBotConfig</code> class.

Several examples are provided below.

### Building the bot

0. Use Maven to build the project and to run the unit tests.
0. Then use Dockerfile to create a Docker image

For detailed examples see [Jenkinsfile](Jenkinsfile) located in this repository.

### Testing the bot locally

Preconditions:

0. You have a JIRA **Test** Project, where you have admin permissions.
1. You have a GitHub Organization with ```Administer``` permissions

Setting up the environment:

0. Setup Github credentials in the ```~/.github``` file
 * Format: Java properties
 * Entries to set: ```login``` and ```password```
 * It's also possible ```oauth``` and ```endpoint``` properties 
 (see [github-api](https://github.com/kohsuke/github-api))
1. Setup JIRA credentials in the ```~/.jenkins-ci.org``` file
 * Format: Java properties
 * Entries to set: ```userName``` and ```password```

Running the bot:

```
java -Dircbot.name=test-ircbot \ -Dircbot.channels="#jenkins-ircbot-test" \ -Dircbot.testSuperUser="${YOUR_IRC_NAME}" \ -Dircbot.github.organization="jenkinsci-infra-ircbot-test" \
-Dircbot.jira.url=${JIRA_URL} \
-Dircbot.jira.defaultProject=TEST \
-jar target/ircbot-2.0-SNAPSHOT-bin/ircbot-2.0-SNAPSHOT.jar 
```
   
After executing this command the bot should connect to your IRC chat.
   
[1]: https://wiki.jenkins-ci.org/display/JENKINS/IRC+Bot
[2]: https://ci.jenkins-ci.org/view/Infrastructure/job/Containers/job/infra_ircbot/
[3]: http://www.opensource.org/licenses/mit-license.php
