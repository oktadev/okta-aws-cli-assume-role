#!/usr/bin/env groovy
 
node {
   checkout scm
      wrap([$class: 'AnsiColorBuildWrapper'])    
      {   
        withEnv(["AWS_ACCESS_KEY=${env.AWS_ACCESS_KEY}",
        "AWS_SECRET_ACCESS_KEY=${env.AWS_SECRET_ACCESS_KEY}"]) {
          try {
            sh `gradle distTar`
            sh `make image`
          }
          catch(error) {
            emailext attachLog: true, body: "Build failed (see ${env.BUILD_URL}): ${error}", subject: "[JENKINS] ${env.JOB_NAME} failed", to: 'josh.mahowald@code42.com'
          }
        }
    }
}

