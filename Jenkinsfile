#!/usr/bin/env groovy

pipeline {
  agent {
    // 'docker' is the (legacy) label used on ci.jenkins.io for "Docker Linux AMD64" while 'linux-amd64-docker' is the label used on infra.ci.jenkins.io
    label 'docker || linux-amd64-docker'
  }
  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
    timestamps()
  }

  stages {
    stage('Build') {
      environment {
        JAVA_HOME = '/opt/jdk-8/'
        BUILD_NUMBER = env.GIT_COMMIT.take(6)
      }
      steps {
        sh 'make bot'
      }

      post {
        always {
          junit '**/target/surefire-reports/**/*.xml'
        }
        success {
            stash name: 'binary', includes: 'target/ircbot-2.0-SNAPSHOT-bin.zip'
        }
      }
    }

    stage('Docker image') {
      steps {
        buildDockerAndPublishImage('ircbot', [unstash: 'binary'])
      }
    }
  }
}
