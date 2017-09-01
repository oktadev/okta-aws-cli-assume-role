#!/usr/bin/env groovy
 
node {
   checkout scm
      wrap([$class: 'AnsiColorBuildWrapper'])    
      {   

            stage 'compile'
            sh './gradlew test distTar'
            stage 'docker'
            sh 'make image'
    }
}

