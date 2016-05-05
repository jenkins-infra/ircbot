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
    withEnv([
        "BUILD_NUMBER=${env.BUILD_NUMBER}:${shortCommit}",
        "JAVA_HOME=${tool 'jdk8'}",
        "PATH+MVN=${tool 'mvn'}/bin",
    ]) {
        sh 'make bot'
    }

    stage 'Build container'
    def whale = docker.build("${imageName}:${imageTag}")

    stage 'Deploy container'
    whale.push()
}
