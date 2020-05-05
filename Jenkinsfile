pipeline {
  agent any
  stages {
    stage('Building') {
      steps {
        sh 'mvn -B -DskipTests clean package'
      }
    }

  }
}