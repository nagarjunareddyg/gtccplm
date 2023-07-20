#!/usr/bin/env groovy

import hudson.model.*
import groovy.transform.Field
import hudson.util.*
import jenkins.model.*
import hudson.FilePath.FileCallable
import hudson.slaves.OfflineCause
import hudson.node_monitors.*


def call(Map pipelineParams) {
  pipeline {
      agent any     
        
        parameters {
            //     string(name: 'Environment' )
                      //  string(name: 'WebRelease', description: 'Branch used for Spinner build')
            //    string(name: 'APP_DB_BUILD_NUMBER', description: 'Auto or Manual Build ')
            //    string(name: 'BuildType', description: 'Auto or Manual Build ')
            //    string(name: 'SonarScan', description: 'Run Sonar')
            //    string(name: 'Junit',  description: 'Run Junit')
            //     string(name: 'Sast',  description: 'Run Sast')
            

            choice choices: ['cdev1', 'trg', 'DEV1'], name: 'Environment'
            booleanParam(name: 'Sast', defaultValue: false, description: 'Enable Coverity Scan in dev environment')
            //choice choices: ['true', 'false'], name: 'SonarScan'
            choice choices: ['Dev_3_0_220', 'cloud_jenkins_test'], name: 'branch'
        } 
  
        environment {
            current_ws = pwd()
            branch = "${env.BRANCH_NAME}"     
            ENV_NAME = 'dev'
            dev_ACCOUNT = '598619258634'
            qa_ACCOUNT  = '598619258634'
            prd_ACCOUNT = '907050794246'
            UAI = 'uai3026525'
            APP = 'powerplmgtcc-dev1-common'
            ECR_DOCKER_REGISTRY = 'dkr.ecr.us-east-1.amazonaws.com'
            VAR_BASE_DOCKER_REGISTRY = '880379568593.dkr.ecr.us-east-1.amazonaws.com'
            plp_docker_registry='880379568593.dkr.ecr.us-east-1.amazonaws.com/uai3026525-powerplmgtcc-tr-common'
            TWISTCLI_CREDS = credentials('twistcli-credentials')
            TWISTCLI_ADDRESS="https://us-east1.cloud.twistlock.com/us-2-158286731"
                COV_HOST = "coverity.power.ge.com"
                        COV_PORT = 443
                        PROJECT_NAME = '1100214481_PowerPLM_GTCC'
                        STREAM = 'aws-jenkins-gtccplm-cov'
                        COV_URL = 'https://coverity.power.ge.com/'
            DESCRIPTION = 'aws-jenkins-gtccplm-cov'
            CAS_PLATFORM_FOLDER = 'gtcc_cas'


            /**  POWER JENKINS: Jenkins tools and credential ids */
            credentials_id='plmenovia_github_cred_id'
            artifactory_repo="QQHDK"
            //sonarqube_server='plmenovia-propel-sonarqube'
            sonarqube_server='sonarqube'
            //sonarqube_scanner='sonarQube Runner'
            sonarqube_scanner='sonarqube_scanner'
            

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
            covbin = '/gpcloud/jenkins/coverity/scan/cov-analysis-linux64-2022.6.1/bin'


        }
    
        stages {

            stage('Initilize') {
                steps{
                    script {
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
            
        
            

    /****************************************** Build & Publish Stage *********************************************/     
            stage('Build') {
//                  when{
//                 expression {'DEV1'.equalsIgnoreCase(params.Environment) && properties.publishType == "app" }
                                
//                 }
                steps {    
                    script {

                        echo "Build"
                        lastStartedStage = env.STAGE_NAME
                        try {
                           pipelineUtils.build([properties:properties])
                        } catch(Exception ex) {
                            buildMessage = properties.build_failed_msg
                            currentBuild.result = 'FAILURE'
                            error buildMessage
                        }
                    }
                }
            }
            
            
                     stage('Sast') {
                            when{
                allOf{
                expression { return params.Sast }
                }
                }
                               steps {
                        script {
                            echo "cov-configure --help"
                            sh '${covbin}/cov-configure --java'
                        sh 'rm -rf $WORKSPACE/covtemp'
                            sh 'mkdir -p $WORKSPACE/covtemp' 
                            sh '${covbin}/cov-build --dir $WORKSPACE/covtemp ant -Dssh-user=afn38x5  \
                                            -Dvcs-tag=${BRANCH_NAME} \
                                            -Dbas-dir=${current_ws} \
                                            -Denv-tag=${Environment} \
                                            -Dclasspath-lib=/jenkins/data/jenkins_home/userContent/pw_web_plm/lib \
                                            -Dclasspath-bin=/jenkins/data/jenkins_home/userContent/pw_web_plm/bin \
                                            -f  ./gtccplm/ant/build/gtccplm-secure-pipeline-jenkins-main-build.xml pre-set-up set-up cleanup set-flags mkdirs client war tar-war summary '
        sh '${covbin}/cov-analyze --dir $WORKSPACE/covtemp --all --webapp-security'
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'coverity-scan-cred', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                sh "${covbin}/cov-commit-defects --dir $WORKSPACE/covtemp --host $COV_HOST --https-port $COV_PORT --stream $STREAM  --description $DESCRIPTION  --user $USERNAME --password $PASSWORD --on-new-cert trust --debug --noxrefs"
        }
    }
      }
    }
            
            
    /****************************************** Build & Publish Stage *********************************************/                
            stage('Image Build') {
                steps {
                    script {
                        LAST_STARTED_STAGE=env.STAGE_NAME
                        println("Image Build Stage")
                        //tag the image to latest in registry
                        sh "tar -xvzf /var/lib/jenkins/workspace/gtccplm/gtccplm/war/build/gtccplm.tar.gz -C /var/lib/jenkins/workspace/gtccplm/"
                        sh "cp -r /var/lib/jenkins/workspace/gtccplm/gtccplm/docker-files/* /var/lib/jenkins/workspace/gtccplm/"
                        sh "chmod 755 -R /var/lib/jenkins/workspace/gtccplm/*"
                        sh "aws ecr get-login-password --region us-east-1  | docker login --username AWS --password-stdin ${VAR_BASE_DOCKER_REGISTRY}"
                        sh "docker build -t ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplminternal -f ./Dockerfile_noncas ./ --no-cache"
                    //    sh "docker build -t ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplmcad -f ./Dockerfile_cad ./ --no-cache"
                        sh "docker build -t ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplm -f ./Dockerfile_cas ./ --no-cache"
                    //    sh "docker build -t ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplmext -f ./Dockerfile_ext ./ --no-cache"
                    }
                }
            }
            /*stage('twistcli download and image scan') {
                        steps {
                            script {
                              println("twistcli download and image scan")
                              withCredentials([usernamePassword(credentialsId: 'twistcli-credentials', passwordVariable: 'TWISTLOCK_PASS', usernameVariable: 'TWISTLOCK_USER')]){
                              sh '''
                                    curl -k -u ${TWISTLOCK_USER}:${TWISTLOCK_PASS} --output ./twistcli ${TWISTCLI_ADDRESS}/api/v1/util/twistcli
                                    chmod a+x ./twistcli
                                    JWT=`curl -k -X POST ${TWISTCLI_ADDRESS}/api/v1/authenticate -H "Content-Type: application/json" -d '{"username": "'${TWISTLOCK_USER}'", "password":"'${TWISTLOCK_PASS}'"}' | jq -r .token`
                                    echo ${JWT}
                                    ./twistcli images scan ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplminternal --publish --address ${TWISTCLI_ADDRESS} --token "'${JWT}'"  || true
                              //    ./twistcli images scan ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplmcad --publish --address ${TWISTCLI_ADDRESS} --token "'${JWT}'"  || true
                                    ./twistcli images scan ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplm --publish --address ${TWISTCLI_ADDRESS} --token "'${JWT}'"  || true
                                    rm -f twistcli
                              '''
                              }
                              
                            }
                        }
                    }*/
            stage('Push to ECR') {
                steps {
                    script {
                        println("Publish")
                        sh "aws ecr get-login-password --region us-east-1  | docker login --username AWS --password-stdin ${VAR_BASE_DOCKER_REGISTRY}"
                        sh "docker image push ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplminternal"
                    //    sh "docker image push ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplmcad"
                        sh "docker image push ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplm"
                    //    sh "docker image push ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplmext"                        
                        sh "docker image rmi ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplminternal"
                    //    sh "docker image rmi ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplmcad"
                        sh "docker image rmi ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplm"
                    //    sh "docker image rmi ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplmext"
                    }
                }
            }
            stage('Deploy Containers') {
                steps {
                    script {
                        //LAST_STARTED_STAGE=env.STAGE_NAME
                        println("Publish")
            
                        /*sh "make buildplatform ENV_NAME=${params.Environment}"*/
                        sh "./platform/deployPlatform.sh ${params.Environment} gtcc-cas gtcc-noncas"
                        
                    }
                }
            } 
        }//stages
  
  

/****************************************** POST build Actions ********************************************/    
    /*     
    post{
                success {
                    script {
                        /**  Trigger build Metrics update job */
    /*                    build job: 'PLM-UPDATE-BUILD-INFO', parameters: [string(name: 'BuildType', value: properties.BuildType), string(name: 'Application', value: properties.application ), string(name: 'Environment', value: properties.Environment ), string(name: 'App_Db_Job_BuildNumber', value:  properties.APP_DB_BUILD_NUMBER), string(name: 'Component', value: properties.publishType), string(name: 'Job_name', value: env.JOB_NAME), string(name: 'Release', value: env.BRANCH_NAME), string(name: 'Build_number', value:  env.BUILD_NUMBER), string(name: 'Duration', value: currentBuild.duration.toString()), string(name: 'MetricsFor', value: 'Build')], propagate: false, wait: false

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
*/
                
  } //pipeline
  
  }//def call


