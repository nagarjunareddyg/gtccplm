def call(Map pipelineParams) {

  pipeline {
      agent any
      options { buildDiscarder(logRotator(numToKeepStr: '50', daysToKeepStr: '30')) }
        
      environment {
    
        sonarqube_server='plmenovia-propel-sonarqube'
        sonarqube_scanner='sonarQube Runner'
           pipelineUtils = ""
  
   }
    
    /*   triggers {
          pollSCM('H/15 * * * *')
    }*/
      
stages {
    
                    stage('SonarScan'){
                    when{
                    expression {env.BRANCH_NAME.toLowerCase().contains('post_prod') || env.BRANCH_NAME.toLowerCase().contains('jpos-branch')}
                    }  
                        
                    steps {
                    script { 
                    pipelineUtils = new ge.plm.pipeline.PipelineUtils()
                    pipelineUtils.runSonarScan()
                    }
                    }
                    }
    
  }//stages
  
  

    post{
        
                  always { 
                echo '********Sonar scan is done************'
                } 

        }
        
        
                
  } //pipeline
  
  }//def call



