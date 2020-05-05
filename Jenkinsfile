pipeline {
  agent any
  stages {
    stage('Building') {
      steps {
        sh 'mvn -B -DskipTests clean package'
      }
    }

    stage('Archive artifacts') {
      steps {
        archiveArtifacts(artifacts: 'TogglePvP', onlyIfSuccessful: true)
      }
    }

  }
}