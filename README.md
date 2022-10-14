# JIRA/GitHub management IRCBot

[![Build Status](https://ci.jenkins.io/job/Infra/job/ircbot/job/main/badge/icon)](https://ci.jenkins.io/job/Infra/job/ircbot/job/main/)
[![Docker Pulls](https://img.shields.io/docker/pulls/jenkinsciinfra/ircbot)](https://hub.docker.com/r/jenkinsciinfra/ircbot)

This IRC bot sits on `#jenkins` as `jenkins-admin` and allow users to create/fork repositories on GitHub, etc. More info: [Jenkins IRC Bot Page](https://jenkins.io/projects/infrastructure/ircbot/)

## Deployment

This repo is containerized (image available [on docker hub](https://hub.docker.com/r/jenkinsciinfra/ircbot/)), then [deployed to our infrastructure](https://github.com/jenkins-infra/kubernetes-management/blob/d843bf1f05334a3ca30394cca875b6d99492ab93/clusters/prodpublick8s.yaml#L116-L123) via Helmfile.

You can find the helm chart and instructions to install it in [jenkins-infra/helm-charts](https://github.com/jenkins-infra/helm-charts/tree/main/charts/ircbot).

## License

[MIT License](https://opensource.org/licenses/mit-license.php)

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

Running the bot for testing:

```
java -Dircbot.name=test-ircbot \ 
-Dircbot.channels="#jenkins-ircbot-test" \ 
-Dircbot.testSuperUser="${YOUR_IRC_NAME}" \ 
-Dircbot.github.organization="jenkinsci-infra-ircbot-test" \
-Dircbot.jira.url=${JIRA_URL} \
-Dircbot.jira.defaultProject=TEST \
-jar target/ircbot-2.0-SNAPSHOT-bin/ircbot-2.0-SNAPSHOT.jar 
```
   
After executing this command the bot should connect to your IRC chat.
