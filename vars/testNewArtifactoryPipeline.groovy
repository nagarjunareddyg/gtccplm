def call(Map pipelineParams) {

  pipeline {
      agent {
          node {
              label 'VDCGLP00680.ics.cloud.ge.com2'
              customWorkspace "/u01/jenkins_local/wksp_${env.JOB_NAME}"
          }
      }
      options { buildDiscarder(logRotator(numToKeepStr: '50', daysToKeepStr: '30')) }
        
      
  parameters {
        string(name: 'Environment' )
        string(name: 'WebRelease', description: 'Branch used for Spinner build')
        string(name: 'APP_DB_BUILD_NUMBER', description: 'Auto or Manual Build ')
        string(name: 'BuildType', description: 'Auto or Manual Build ')
        string(name: 'SonarScan', description: 'Run Sonar')
        string(name: 'Junit',  description: 'Run Junit')
        string(name: 'Sast',  description: 'Run Sast')
      
    } 
      
 environment {
        current_ws = pwd()
        branch = "${env.BRANCH_NAME}"

        /**  POWER JENKINS: Jenkins tools and credential ids */
        credentials_id='plmenovia_github_cred_id'
        artifactory_repo="QQHDK"
        sonarqube_server='plmenovia-propel-sonarqube'
        sonarqube_scanner='sonarQube Runner'
         
         /** Artifactory server id for old artifactory Devcloud */
        //artifactory_server='plmenovia_artifactory_server'
         
         /** Artifactory server id for NEW artifactory BUILD.GE */
         artifactory_server='plmenovia_buildge_artifactory_server'
        
         antTool='ant'
        coverityTool='cov-analysis-2018.12'
        coverity_server='plm-coverity'
                
         /* AWS properties using for aws deployment */
            AWS_PLM_PROXY='iss-americas-pitc-cincinnatiz.proxy.corporate.ge.com:80'
        AWS_PLM_REST_URL='s3-artifacts-uai3026525-us8p'
        aws_credentials_id='GTCCPLM_AWS'
        
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
                        properties.sonarscan_quality_gate == "true" }
                  } 

                steps {
                script {
                           lastStartedStage = env.STAGE_NAME
                    timeout(time: 5, unit: 'MINUTES') {
                        def qg = waitForQualityGate()
                        if (qg.status != 'OK') {
                            buildMessage= properties.sonarscan_qualitygate_msg
                            currentBuild.result = 'FAILURE'
                            error buildMessage 
                            }
                        }
                    }
                }
                          }
    
    
    /****************************************** Junit Stage *********************************************/             
        
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
                    properties.junit_quality_gate == "true" } 
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
                    /** when condition 
                    for non prod environments, no need to run sast with every build to check defects. Sast parameter can be false.
                    for prod environments, sast should be run in order to check sast quality gate. Otherwise qualitygate stage will be skipped.**/
                    when { 
                    expression {        properties.coverity_quality_gate == "true" && 
                              (  !params.Environment.equalsIgnoreCase("PROD") || 
                                  (params.Environment.equalsIgnoreCase("PROD") && params.Sast == 'true') 
                              )       
                           }
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
                            echo "Build"
                               lastStartedStage = env.STAGE_NAME
                               pipelineUtils.build([properties:properties])

                            }
                        }
                }
            
            
                 
             stage('Publish') {

                        steps {
                        script { 
                            lastStartedStage = env.STAGE_NAME
                            echo "Publish"
                            try {
                            pipelineUtils.publish([properties:properties])
                            } catch(Exception ex) {
                            buildMessage = ex.toString()
                            currentBuild.result = 'FAILURE'
                            error buildMessage
                            }
                        }
                        }
                  }
          
  }//stages
  
  

/****************************************** POST build Actions *********************************************/    
     
    post{
                success {
                    script {
                        /**  Trigger build Metrics update job */
                        echo 'Save Build Metrics'
                        //build job: 'PLM-UPDATE-BUILD-INFO', parameters: [string(name: 'BuildType', value: properties.BuildType), string(name: 'Application', value: properties.application ), string(name: 'Environment', value: properties.Environment ), string(name: 'App_Db_Job_BuildNumber', value:  properties.APP_DB_BUILD_NUMBER), string(name: 'Component', value: properties.publishType), string(name: 'Job_name', value: env.JOB_NAME), string(name: 'Release', value: env.BRANCH_NAME), string(name: 'Build_number', value:  env.BUILD_NUMBER), string(name: 'Duration', value: currentBuild.duration.toString()), string(name: 'MetricsFor', value: 'Build')], propagate: false, wait: false

                    }
                }
                failure {
                    script {
                        echo 'Notify on Failed'
                        pipelineUtils.notifyOnFailure([properties:properties, buildMessage:buildMessage, lastStartedStage:lastStartedStage])
                    }
                }
        
                     always { 
                echo 'Clean Workspace'
                cleanWs()
                } 

        }
        
        
                
  } //pipeline
  
  }//def call



