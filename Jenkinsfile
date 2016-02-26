#!groovy

def imageName = 'jenkinsciinfra/ircbot'

/* Only keep the 10 most recent builds. */
properties([[$class: 'BuildDiscarderProperty',
                strategy: [$class: 'LogRotator', numToKeepStr: '10']]])

node('docker') {
    checkout scm

    /* Using this hack right now to grab the appropriate abbreviated SHA1 of
     * our current build's commit. We must do this because right now I cannot
     * refer to `env.GIT_COMMIT` in Pipeline scripts
     */
    sh 'git rev-parse HEAD > GIT_COMMIT'
    shortCommit = readFile('GIT_COMMIT').take(6)
    def imageTag = "build${shortCommit}"

    stage 'Build ircbot'
    withMavenEnv(["BUILD_NUMBER=${env.BUILD_NUMBER}:${shortCommit}"]) {
        sh 'make bot'
    }

    stage 'Build container'
    def whale = docker.build("${imageName}:${imageTag}")

    stage 'Deploy container'
    whale.push()
}


/* The following is shamelessly stolen from https://github.com/jenkinsci/jenkins/pull/1999
 *
 * I am optimistic that at some point we either standardize this in ci.j.o or a
 * pipeline-utility-step gets created to make this easier
 */

// This method sets up the Maven and JDK tools, puts them in the environment along
// with whatever other arbitrary environment variables we passed in, and runs the
// body we passed in within that environment.
void withMavenEnv(List envVars = [], def body) {
    // The names here are currently hardcoded for my test environment. This needs
    // to be made more flexible.
    // Using the "tool" Workflow call automatically installs those tools on the
    // node.
    String mvntool = tool name: "mvn3.3.3", type: 'hudson.tasks.Maven$MavenInstallation'
    String jdktool = tool name: "jdk7_80", type: 'hudson.model.JDK'

    // Set JAVA_HOME, MAVEN_HOME and special PATH variables for the tools we're
    // using.
    List mvnEnv = ["PATH+MVN=${mvntool}/bin", "PATH+JDK=${jdktool}/bin", "JAVA_HOME=${jdktool}", "MAVEN_HOME=${mvntool}"]

    // Add any additional environment variables.
    mvnEnv.addAll(envVars)

    // Invoke the body closure we're passed within the environment we've created.
    withEnv(mvnEnv) {
        body.call()
    }
}
