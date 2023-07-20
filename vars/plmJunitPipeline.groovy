def call(Map pipelineParams) {
pipeline {
                agent any
         
    
            options { buildDiscarder(logRotator(numToKeepStr: '50', daysToKeepStr: '30')) }
    
             parameters {
        string(name: 'Environment' )
        string(name: 'WebRelease', description: 'Branch used for Spinner build/Junit')
        string(name: 'WebRelease_workspace', description: 'Web release workspace')
        }
         
         environment {
            credentials_id="plmenovia_github_cred_id"
            antTool='ant'
            pipelineUtils = ""
            properties = ""
         }
 
stages {

    stage('Initilize') {
    
      steps {
      script{
      
          
                pipelineUtils = new ge.plm.pipeline.PipelineUtils()
                   /** Read applicaiton specific properties **/
                properties = pipelineUtils.initilizeForScan()
                      //properties = pipelineUtils.initilize()
                /** load input parameters*/
                properties.putAll(params)
                    print(properties)
                    
                if(WebRelease_workspace == null || (WebRelease_workspace != null && WebRelease_workspace.trim().length() == 0) ) {
                /** clone web branch master code to get ant build files **/
                pipelineUtils.checkOutGitCode([credentials_id:credentials_id, targetDirectory:properties.webDirectory, branchName:env.WebRelease, gitRepositoryURL:properties.webcode_git_repo_url])  
                /** copy junit build xml file into workspace from web code*/
                sh "cp ${WORKSPACE}/${properties.webDirectory}/${properties.web_repo_base_folder}/ant/build/${properties.Junit_buildFile}  ${WORKSPACE}"
                } else {
                 sh "cp ${properties.WebRelease_workspace}/${properties.web_repo_base_folder}/ant/build/${properties.Junit_buildFile}  ${WORKSPACE}"
                }
               
            }    
      }
    }
    
    
   
         stage('Junit Test') {
                        steps {                           
                    /** Execute Junit Tests */
                /*    withAnt(installation: env.antTool, jdk: properties.jdk) {
                    sh "ant -v \
                    -f ${properties.Junit_buildFile} \
                    -Dbas-dir=${properties.webDirectory} \
                    -Dclasspath-lib=${properties.jenkins_pwwebplm_lib_path} \
                    -Dweb_repo_base_folder=${properties.web_repo_base_folder} \
                    -Denv-tag=${params.Environment.toLowerCase()} \
                    pre-set-up clean compile testsuite junitreport"
                    }
           */
                    
                    withAnt(installation: env.antTool, jdk: properties.jdk) {
                    sh "ant -v \
                    -f ${properties.Junit_buildFile} \
                    -Dbas-dir=${properties.WebRelease_workspace} \
                    -Dclasspath-lib=${properties.jenkins_pwwebplm_lib_path} \
                    -Dweb_repo_base_folder=${properties.web_repo_base_folder} \
                    -Denv-tag=${params.Environment.toLowerCase()} \
                    pre-set-up clean compile testsuite junitreport"
                    }
                    
                       } 
                      } 
     
     
          stage('Publish test results') {
           steps {
                          script{
                               try {
                                    junit allowEmptyResults: true, keepLongStdio: true, testResults: '**/target/**/*.xml'
                                   
                               }catch(ex) {
                                    
                                         echo 'Publish Junit test results'
                                           if (currentBuild.result == 'UNSTABLE') {
                                                echo 'unstable test results'
                                               currentBuild.result = 'FAILED TESTCASES'
                                          //   error "Failed Testcases"
                                            }
                                   }
                               }
             }
           } 
        
        
  } //stages pipeline is completed.
   
   
      post {
              always {
                script{
                    if (currentBuild.currentResult == "FAILURE")
                    {
                    pipelineUtils.notifyOnFailure([properties:properties, buildMessage:properties.junit_build_failure_msg, lastStartedStage:"Junit"])
                    }else {
                    pipelineUtils.sendJunitReportMail([ subject:properties.Junit_mail_subject, mailGroup:properties.app_notify_email , Environment:params.Environment , Application:properties.application, JunitBuildresult:currentBuild.currentResult])
                    }
                     //cleanWs()
                }
            }
           }
       
    
          
 } //pipleline
}
