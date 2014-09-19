# JIRA/GitHub management IRCBot
[![Build Status](http://ci.jenkins-ci.org/view/Infrastructure/job/infra_ircbot/badge/icon)](http://ci.jenkins-ci.org/view/Infrastructure/job/infra_ircbot/)

This IRC bot sits on `#jenkins` as `jenkins-admin` and allow users to create/fork repositories on GitHub, etc. More info: [IRC Bot Wiki][1]

## Deployment
This repo is containerized, then deployed to our infrastructure via Puppet. Currently, the functionality is broken (see INFRA-129)

[1]: https://wiki.jenkins-ci.org/display/JENKINS/IRC+Bot
