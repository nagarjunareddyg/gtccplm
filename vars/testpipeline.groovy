def call(Map pipelineParams) {

  pipeline {
      agent {
          node {
              label 'VDCGLP00680.ics.cloud.ge.com2'
              customWorkspace "/u01/jenkins_local/wksp_${env.JOB_NAME}"
          }
      }
      options { buildDiscarder(logRotator(numToKeepStr: '5', daysToKeepStr: '30')) }
        
      
      
  parameters {
        string(name: 'Environment' )
        string(name: 'WebRelease', description: 'Branch used for Spinner build')
        string(name: 'APP_DB_BUILD_NUMBER', description: 'Auto or Manual Build ')
        string(name: 'BuildType', description: 'Auto or Manual Build ')
        string(name: 'SonarScan', description: 'Run Sonar')
        string(name: 'Junit',  description: 'Run Junit')
        string(name: 'Sast',  description: 'Run Sast')
      
    } 
      
    /*  tools {
    jdk 'jdk1.8'
  }*/

      
 environment {
        current_ws = pwd()
        branch = "${env.BRANCH_NAME}"

    /**  Jenkins tools and credential ids */
            credentials_id='plmenovia_github_cred_id'
        artifactory_repo="QQHDK"
        sonarqube_server='plmenovia-propel-sonarqube'
        sonarqube_scanner='sonarQube Runner'
        //artifactory_server='plmenovia_artifactory_server'
         artifactory_server='plmenovia_buildge_artifactory_server'
        antTool='ant'
         //jdkTool='jdk1.8'
        coverityTool='cov-analysis-2018.12'
        coverity_server='plm-coverity'
        
        AWS_PLM_PROXY='iss-americas-pitc-cincinnatiz.proxy.corporate.ge.com:80'
        AWS_PLM_REST_URL='s3-artifacts-uai3026525-us8p'
        aws_credentials_id='GTCCPLM_AWS'

    /** Constants **/
            appConfigRepo_git_url="github.build.ge.com/502808103/applicationConfig.git"
           //appConfigRepo_git_url="github.build.ge.com/pw-web/applicationConfig.git"

        
    /** Assign values later in stages. **/
            pipelineUtils = ""
            properties = ""
            buildMessage =""
            junitbuildResult=""
            lastStartedStage=""    
     
  
   }
    
stages {
    
    
stage('Initilize') {
            steps{
            script{
                
                           lastStartedStage = env.STAGE_NAME
                    /* load application properties */
                    pipelineUtils = new ge.plm.pipeline.PipelineUtils()
                    properties = pipelineUtils.initilize()
                                    
                    /** load input parameters*/
                    properties.putAll(params)
                    
                    print(properties)

                }
            }
        }
    
        
    /****************************************** SonarScan Stage ******************************/
            stage('SonarScan'){
                when{
                expression {'true'.equalsIgnoreCase(params.SonarScan) && properties.publishType == "db"  }
                }  
                steps {
                script { 
                 lastStartedStage = env.STAGE_NAME
                pipelineUtils.runSonarScan()
                }
                }
            }
    
         stage("SonarScan Quality Gate") {
                when { 
                    expression { 'true'.equalsIgnoreCase(params.SonarScan) && properties.publishType == "db" && 
                        properties.sonarscan_quality_gate_test == "true" }
                  } 

                steps {
                script {
                     lastStartedStage = env.STAGE_NAME
                    
                    timeout(time: 5, unit: 'MINUTES') {
                        def qg = waitForQualityGate()
                        if (qg.status != 'OK') {
                            buildMessage="Pipeline aborted due to sonarqube quality gate failure: ${qg.status}"
                            currentBuild.result = 'FAILURE'
                            error buildMessage 
                            }
                        }
                    }
                }
                          }
    
    
    /****************************************** Junit Stage *********************************************/             
            /** JUNIT Stage **/
            stage('Junit') {
                when{
                expression { params.Junit == 'true'  && properties.publishType == "db" }
                } 

                steps {
                script { 
                 lastStartedStage = env.STAGE_NAME
                def webcode_path = "${env.current_ws}/${properties.webDirectory}"
                junitbuildResult = build job: properties.Junit_job_name, parameters: [string(name: 'Environment', value: properties.Environment), string(name: 'WebRelease', value: properties.WebRelease), string(name: 'WebRelease_workspace', value: webcode_path)], propagate: false

                }
                }
            }
    stage('Junit Quality Gate') {
                when {
                    expression { params.Junit == 'true'  && properties.publishType == "db" && 
                    properties.junit_quality_gate_test == "true" } 
                }

                steps {
                script{
                     lastStartedStage = env.STAGE_NAME
                    if(junitbuildResult.result != "SUCCESS")
                    buildMessage = properties.junit_qualitygate_msg
                    currentBuild.result = 'FAILURE'
                    error buildMessage 
                }
                }
               }
    
                 
        /****************************************** SAST Stage *********************************************/        
                stage('SAST'){
                    when{
                    expression { params.Sast == 'true' }
                    }  

                    steps {
                    script { 
                         lastStartedStage = env.STAGE_NAME
                        try {
                        new ge.plm.pipeline.SastUtils().runSast([properties:properties])
                        } catch(Exception ex) {
                            echo "**** EXCeption caught"
                            buildMessage="Error with Coverity"
                            currentBuild.result = 'FAILURE'
                            error buildMessage 
                        }
                    }
                    }
                }
    
       
                stage('SAST Quality Gate') {
                    when { 
                    expression {  (!params.Environment.equalsIgnoreCase("PROD") && properties.coverity_quality_gate_test == "true") ||
                              (params.Environment.equalsIgnoreCase("PROD") && properties.coverity_quality_gate_test == "true" &&  params.Sast == 'true')}
                        
                              }

                    steps {
                    script{
                         lastStartedStage = env.STAGE_NAME
                        coverityResult =coverityIssueCheck coverityInstanceUrl: properties.coverityInstanceUrl, projectName: properties.coverity_project_name, returnIssueCount: true, viewName: properties.coverity_view
                        if(coverityResult !=null && coverityResult >0) {
                        buildMessage = properties.sast_qualitygate_msg
                        currentBuild.result = 'FAILURE'
                        error buildMessage 
                        }
                        
                    }
                    }
                    }
            
    /****************************************** Build & Publish Stage *********************************************/      
    
       stage('Build') {
                steps {    
                script {
                    echo "build"
                      lastStartedStage = env.STAGE_NAME
                      pipelineUtils.build([properties:properties])
                
                    }
                }
        }
            
                 
         stage('Publish') {
                
         
                steps {
                script { 
                     lastStartedStage = env.STAGE_NAME
                    echo "publish"
                    // Following statement will not update the appconfig.json file to start deployment.
                           pipelineUtils.deployArtifacts([properties:properties])
                    pipelineUtils.publishAppConfigWithoutCommitOnlyForTesting([properties:properties])
                    
                }
                }
          }
          
        
          
  }//stages

     /****************************************** POST build Actions *********************************************/    
      
    post{
                success {
                    script {
                        buildMessage="Success"
                        echo 'Duration:'
                        println "currentBuild.durationString :"+currentBuild.durationString
                        println "currentBuild.timeInMillis :"+currentBuild.timeInMillis
                        println "currentBuild.duration :"+currentBuild.duration
                        /**  Trigger build Metrics update job */
                        //build job: 'PLM-UPDATE-BUILD-INFO', parameters: [string(name: 'BuildType', value: properties.BuildType), string(name: 'Application', value: properties.application ), string(name: 'Environment', value: properties.Environment ), string(name: 'App_Db_Job_BuildNumber', value:  properties.APP_DB_BUILD_NUMBER), string(name: 'Component', value: properties.publishType), string(name: 'Job_name', value: env.JOB_NAME), string(name: 'Release', value: env.BRANCH_NAME), string(name: 'Build_number', value:  env.BUILD_NUMBER), string(name: 'Duration', value: currentBuild.duration.toString()), string(name: 'MetricsFor', value: 'Build')], propagate: false, wait: false

                    }
                }
                failure {
                    script {
                        echo 'Notify on Failed '
                        println(lastStartedStage)
                        pipelineUtils.notifyOnFailure([properties:properties, buildMessage:"${buildMessage}", lastStartedStage:lastStartedStage])
                    }
                }
        
                always { 
                echo '******** Clean Workspace************'
                cleanWs()
                } 

        }
                
  } //pipeline
  
  }//def call



