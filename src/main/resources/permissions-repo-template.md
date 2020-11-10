<!-- This PR template only applies to permission changes. Ignore it for changes to the tool updating permissions in Artifactory -->

# Description
https://github.com/jenkinsci/__FORKTO
https://issues.jenkins-ci.org/browse/__JIRA_HOSTING_KEY
<!-- fill in description here, this will at least be a link to a GitHub repository, and often also links to hosting request, and @mentioning other committers/maintainers as per the checklist below -->

# Submitter checklist for changing permissions

<!--
Make sure to implement all relevant entries (see section headers to when they apply) and mark them as checked (by replacing the space between brackets with an "x"). Remove sections that don't apply, e.g. the second and third when adding a new uploader to an existing permissions file.
-->

### Always

- [x] Add link to plugin/component Git repository in description above

### For a newly hosted plugin only

- [x] Add link to resolved HOSTING issue in description above

### For a new permissions file only

- [x] Make sure the file is created in `permissions/` directory
- [x] `artifactId` (pom.xml) is used for `name` (permissions YAML file).
- [x] [`groupId` / `artifactId` (pom.xml) are correctly represented in `path` (permissions YAML file)](https://github.com/jenkins-infra/repository-permissions-updater/#managing-permissions)
- [x] Check that the file is named `plugin-${artifactId}.yml` for plugins

### When adding new uploaders (this includes newly created permissions files)

- [x] [Make sure to `@`mention an existing maintainer to confirm the permissions request, if applicable](https://github.com/jenkins-infra/repository-permissions-updater/#requesting-permissions)
- [x] Use the Jenkins community (LDAP) account name in the YAML file, not the GitHub account name
- [x] [All newly added users have logged in to Artifactory at least once](https://github.com/jenkins-infra/repository-permissions-updater/#requesting-permissions)