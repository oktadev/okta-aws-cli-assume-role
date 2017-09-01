#!/usr/bin/env groovy
 
node {
   checkout scm
      wrap([$class: 'AnsiColorBuildWrapper'])    
      {   
        withEnv(["AWS_ACCESS_KEY=${env.AWS_ACCESS_KEY}",
        "AWS_SECRET_ACCESS_KEY=${env.AWS_SECRET_ACCESS_KEY}"]) {
            stage 'compile'
            sh 'make dockerbuild'
            stage 'docker'
            sh 'make image'
        }
    }
}

