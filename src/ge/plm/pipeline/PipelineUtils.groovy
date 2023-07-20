#!/usr/bin/groovy
package ge.plm.pipeline;
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.json.JsonSlurperClassic
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
/**
 Author : PLM CCM Team
 */
 
 /********** Sonar Scan Utils *************/
 /*
  Desc : Below method is used for sonarscans and when we have sonar scanner is configured in jenkins under Globaltool configuration
  Generally used for secure pipeline jenkins
*/
   
     def runSonarScan() {
       println("Sonar scan utils method called")
       script {
        scannerHome = tool "${env.sonarqube_scanner}";
           /* added for cloud jenkins job*/
        //scannerHome = tool "sonarqube_scanner";
        /*scannerHome = tool "${env.SonarQube}";*/
       }
    withSonarQubeEnv("${env.sonarqube_server}") 
      /*withSonarQubeEnv("sonarqube_server")*/ {
        //sh "${scannerHome}/bin/sonar-scanner -X" 
        sh "${scannerHome}/bin/sonar-scanner -X -Dsonar.projectKey=gtccplm -Dsonar.host.url=https://i.ci.build.ge.com/ph0jp7l8/sq -Dsonar.exclusions=gtccplm/war/build/widget/images/MyApps/**"
      }
     }
     
    
  

     

/******** Copy Artifacts into Artifactory Utils  ********/

/*
  Desc :  Copy Artifacts into Artifacatory
  Usage: Generally used for war, spinner tar and appConfig.json files copy into artifactory
  
*/
/*
def copyArtifacts(params){
  
                 def server = Artifactory.server env.artifactory_server
                
                // Uploading specifications
        
                def uploadSpec = """{
                  "files": [
                    {
                      "pattern": "${params.filepattern}",
                       "target": "${params.target}"
                    }
                 ]
                }"""
                
                println uploadSpec
                def buildinfo=server.upload(uploadSpec)
                echo "buildinfo: ${buildinfo}"
}
*/


/*********  AppConfig update Utils *******/
/* @NonCPS
def xmlTransform(txt, modulename, buildnumber) {
   
    def inputJSON = new JsonSlurper().parseText(txt)
    echo 'Start tranforming XML'
    def jsonbuilder = new JsonBuilder(inputJSON)
   

    for(int i = 0;i<jsonbuilder.content.components.size;i++) {
        
        if( jsonbuilder.content.components[i] !=null) 
            for(int j = 0; j<jsonbuilder.content.components[i].size; j++)
            if(jsonbuilder.content.components[i][j].Module_Name == modulename)  {
                      jsonbuilder.content.components[i][j].Build_Number = buildnumber as Integer
                  println "updated build number"+ jsonbuilder.content.components[i][j].Build_Number
            }
        
    }
        

   return jsonbuilder.toPrettyString()
}

*/

/*** Generic util methods ************/

/**
  * Returns the path to a temp location of a script from the global library (resources/ subdirectory)
  *
  * @param srcPath path within the resources/ subdirectory of this repo
  * @param destPath destination path (optional)
  * @return path to local file
  */
    String copyGlobalLibraryScript(String srcPath, String destPath = null) {
      destPath = destPath ?: createTempLocation(srcPath)
      writeFile file: destPath, text: libraryResource(srcPath)
      echo "copyGlobalLibraryScript: copied ${srcPath} to ${destPath}"
    return destPath
    }
  

@NonCPS
String createTempLocation(String path) {
  String tmpDir = pwd tmp: true
  return tmpDir + File.separator + new File(path).getName()
}

/**
Checkout given repository branch
 Usage : using for Web branch checkout for spinner build And AppConfig repo checkout to update build information
*/

def checkOutGitCode(params) {
            // Delete target directory before clone the git into it.
            //sh "rm -Rf ${params.targetDirectory}"
            //sh 'rm -Rf "applicationConfig/"'
            checkout([$class: 'GitSCM',
            branches: [[name: "*/${params.branchName}"]],
            extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${params.targetDirectory}"],
            [$class: 'LocalBranch', localBranch: "**"],
            [$class: 'CloneOption', noTags: true, reference: '', shallow: true]
            ],
            userRemoteConfigs: [[credentialsId: "${env.credentials_id}",
            url: "${params.gitRepositoryURL}"]]
            ])
}


/*** Read Properties  **/

def getPropertiesFileName(job_name) {
    //println(job_name)
    def repositoryName = job_name
    if( job_name.indexOf('/') >0 ) {
        arr = job_name.split('/')
        repositoryName= arr[1]
    }
    println("repositoryName"+repositoryName)
    // Read mapping file to get the property filename based on GIT url    
    def mappingFile =     copyGlobalLibraryScript('application_mapping.properties')
    def mappingFilecontent = readFile mappingFile
    Properties mappingFileproperties = new Properties()
    InputStream mappingStream = new ByteArrayInputStream(mappingFilecontent.getBytes());
    mappingFileproperties.load(mappingStream)
    def appProperties_File= mappingFileproperties."$repositoryName"
    println("appProperties_File:"+appProperties_File)    
    return appProperties_File
}



def readProperties(job_name) {
    
    if( job_name.indexOf('/') >0 ) {
        arr = job_name.split('/')
        repositoryName= arr[1]
    }

    Properties properties = new Properties()
    def appProperties_File = getPropertiesFileName(job_name)
        
/* Read  applicaiton specific properties */
    def applicationPropertiesFile = copyGlobalLibraryScript(appProperties_File)
    def content = readFile applicationPropertiesFile
    InputStream is = new ByteArrayInputStream(content.getBytes());
    properties.load(is)
  
    //end
    return properties

}

def readGenericProperties() {
    Properties properties = new Properties()
    def triggerPropertiesFile = copyGlobalLibraryScript('applicationproperties/generic.properties')
     def triggercontent = readFile triggerPropertiesFile
    InputStream is = new ByteArrayInputStream(triggercontent.getBytes());
    properties.load(is)
    return properties
}

/** Read all properties to use them for any other pipelines like junit 
 Skipping web code checkout for performace testing 
**/
def initilizeForScan() {
    Properties properties = new Properties()
    /** Read application specific properties */
    properties = readProperties(env.JOB_NAME)
    /** Read Generic properties and them to properties **/
    def genericProperties = readGenericProperties()
    properties.putAll(genericProperties)
    return properties
}

/** 
    Read application and other properties to initilize the build process
*/

def initilize() {

    Properties properties = new Properties()
    
    /** Read application specific properties */
    properties = readProperties(env.JOB_NAME)
    
    /** Read Generic properties and them to properties **/
    def genericProperties = readGenericProperties()
    properties.putAll(genericProperties)
    
    /** clone web branch code for spinner build **/
    if(properties.publishType == 'db') {
    // checkout corresponding web code for spinner builds as spinner build referfew files from web code
    new ge.plm.pipeline.BuildUtils().initilizeWebCodeForSpinnerBuild([properties:properties])
    }
    
    /* Read github username and token to use it for commit appConfig json file while publish */
    withCredentials([usernamePassword(credentialsId: env.credentials_id, passwordVariable: 'pwd', usernameVariable: 'uname')]) {
     credentials_id_username="$uname"
    credentials_id_token="$pwd"
    
    Reader reader = new StringReader("credentials_id_username="+credentials_id_username)
    properties.load(reader)
    
    reader = new StringReader("credentials_id_token="+credentials_id_token)
    properties.load(reader)
    
    }

    return properties
}



/**
  To call Web and Spinner build methods from BuildUtils
 */
def build(params) {

            if (params.properties.publishType == 'app') {
            new ge.plm.pipeline.BuildUtils().writeLastDeployedJsp([properties:params.properties])
            new ge.plm.pipeline.BuildUtils().webBuild([properties:params.properties])
            }
            
            else if (params.properties.publishType == 'db') {
                    new ge.plm.pipeline.BuildUtils().spinnerBuild([properties:params.properties])
            } //else if ends
            
}


/** Read, Update appconfig file.
    Upload into artifactory.
    Commit into Github
    For the builds in other than power jenkins where jq is not get installed. 
    make sure to enable appConfigRepo_git_url variable in vars/<>.groovy file to use this method.
*/

def publishAppConfig(params) {
 
 def appconfig_workspace_path = ""
 if(params.properties.applicationConfig_workspace_path == null) {
           // checkout Appconfig git code 
           def gitRepositoryURL = "https://${params.properties.credentials_id_username}:${params.properties.credentials_id_token}@${env.appConfigRepo_git_url}"
           sh "rm -Rf applicationConfig"
           checkOutGitCode([targetDirectory:"applicationConfig", branchName:"master", gitRepositoryURL:gitRepositoryURL]) 
            
            appconfig_workspace_path ="applicationConfig"       
 } 
         else {
           appconfig_workspace_path = params.properties.applicationConfig_workspace_path
         }
              // Read and update appconfig file 
              def appConfig_key =  env.Environment+"_artifactory_appconfig_file"
               def artifactory_appConfig_path= params.properties.getProperty(appConfig_key)
            def configFile = "${appconfig_workspace_path}/${artifactory_appConfig_path}"
            def fileName = "${artifactory_appConfig_path}"
            def xmlTemplate = readFile(configFile)
            println("***before parsing")
            println(xmlTemplate)
            println("*****Updated File*********")
            
            //use app-db job build number to place artifacts and update appconfig file with same build number
            def modulename = params.properties.getProperty("${params.properties.Environment}_appconfig_${params.properties.publishType}_module")
            if(modulename ==null || (modulename!=null && modulename.trim().length()==0))
                 modulename=params.properties.publishType
            
            //def upadatedFile = xmlTransform(xmlTemplate, params.properties.publishType, params.properties.APP_DB_BUILD_NUMBER)
            println("module name **** "+modulename)
            def upadatedFile = xmlTransform(xmlTemplate, modulename, params.properties.APP_DB_BUILD_NUMBER)
            println(upadatedFile)
            writeFile file: configFile, text: upadatedFile


            // Commit appconfig file into github 
            scriptFile = copyGlobalLibraryScript('scripts/updateAppConfigFile.sh')
            sh "chmod a+x ${scriptFile}"
            sh "${scriptFile} '${appconfig_workspace_path}' '${fileName}' '${params.properties.credentials_id_username}' '${params.properties.credentials_id_token}' '${env.appConfigRepo_git_url}' "


            // copy appconfig file into Artifactory as suggested by Jonathan 
            def target = "${env.artifactory_repo}/Applications/${artifactory_appConfig_path}"
            println(target)
            copyArtifacts([filepattern:configFile, target:target ])

}


def publishAppConfigPower(params) {
    /** Update appConfig file and commit **/
            def appconfig_workspace_path = params.properties.applicationConfig_workspace_path
            def appConfig_key =  env.Environment+"_artifactory_appconfig_file"
               def artifactory_appConfig_path= params.properties.getProperty(appConfig_key)
            def configFile = "${appconfig_workspace_path}/${artifactory_appConfig_path}"
    
            /** Read module name. Example: Empty or appdev2/dbdev2. Assign publishType(app/db) to modulename if its empty*/
            def modulename = params.properties.getProperty("${params.properties.Environment}_appconfig_${params.properties.publishType}_module")
            if(modulename ==null || (modulename!=null && modulename.trim().length()==0))
                 modulename=params.properties.publishType
    
            scriptFile = copyGlobalLibraryScript('scripts/updateAppConfigFilePower.sh')
            sh "chmod a+x ${scriptFile}"
            sh "${scriptFile} '${appconfig_workspace_path}' '${artifactory_appConfig_path}'  '${params.properties.APP_DB_BUILD_NUMBER}' '${params.properties.publishType}' '${modulename}'"

    /** Copy appconfig file into Artifactory **/
    /*        def target = "${env.artifactory_repo}/Applications/${artifactory_appConfig_path}"
            println(target)
            copyArtifacts([filepattern:configFile, target:target ])
*/    
}
def copyLibraryFilesToWorkspace() {
    try {
     copyGlobalLibraryScript("Makefile")
     copyGlobalLibraryScript("Makefile.settings")
    }catch(Exception ex) {
        println("Error in copyLibraryFilesToWorkspace method")
        println (ex)
    }
}



/*
def publishAppConfigWithoutCommitOnlyForTesting(params) {
    /** Update appConfig file **/
/*            def appconfig_workspace_path = params.properties.applicationConfig_workspace_path
            def appConfig_key =  env.Environment+"_artifactory_appconfig_file"
               def artifactory_appConfig_path= params.properties.getProperty(appConfig_key)
            def configFile = "${appconfig_workspace_path}/${artifactory_appConfig_path}"
    
    /** Read module name. Example: Empty or appdev2/dbdev2. Assign publishType(app/db) to modulename if its empty*/
/*            def modulename = params.properties.getProperty("${params.properties.Environment}_appconfig_${params.properties.publishType}_module")
            if(modulename ==null || (modulename!=null && modulename.trim().length()==0))
                 modulename=params.properties.publishType
                
            scriptFile = copyGlobalLibraryScript('scripts/updateAppConfigFilePowerTemp.sh')
            sh "chmod a+x ${scriptFile}"
            sh "${scriptFile} '${appconfig_workspace_path}' '${artifactory_appConfig_path}'  '${params.properties.APP_DB_BUILD_NUMBER}' '${params.properties.publishType}' '${modulename}' "

        
    
    /** Copy appconfig file into Artifactory **/
/*            def target = "${env.artifactory_repo}/Applications/${artifactory_appConfig_path}"
            println(target)
//copyArtifacts([filepattern:configFile, target:target ])
    
}

def publish(params) {
        
        /* place war/spinner tar files in folder with build number generated from app-db job */
        def target = "arn:aws:s3:::uai3026525-powerplmgtcc-tr-spinner"
        //         def target = "arn:aws:s3:::uai3026525-powerplmgtcc-dev1-spinner"
        target = target.replace("<BUILD_NUMBER_TO_REPLACE>", params.properties.APP_DB_BUILD_NUMBER)
        println(target)
              withAWS(credentials:"${env.aws_credentials_id}") {
            withEnv(["http_proxy=${env.AWS_PLM_PROXY}",
            "https_proxy=${env.AWS_PLM_PROXY}",
            "PYTHONWARNINGS=${env.AWS_HTTPS_WARNINGS_IGNORE}"
            ]) {
                 
                 sh """
                aws s3 ls  s3://${env.AWS_PLM_REST_URL} --no-verify-ssl
                aws s3 cp  $WORKSPACE/${params.properties.application_artifacts_filepattern}  ${target}
                aws s3 ls  ${target} --no-verify-ssl
                
                            
                """
            }
              }
    
/** To copy war and spinner tar files into artifactory */
/*def deployArtifacts(params) {
    
        def artifact_key = env.Environment+"_artifactory_artifact_file"
        
        /* place war/spinner tar files in folder with build number generated from app-db job */
        
/*        def target = "${env.artifactory_repo}/Applications/"+params.properties.getProperty(artifact_key)
        target = target.replace("<BUILD_NUMBER_TO_REPLACE>", params.properties.APP_DB_BUILD_NUMBER)
        copyArtifacts([filepattern:params.properties.application_artifacts_filepattern, target:target ])

}

def deployArtifactsToAWS(params) {
    
      
        def aws_target_key = env.Environment+"_aws_artifact_file"
        
        /* place war/spinner tar files in folder with build number generated from app-db job */
    /*    def target = "s3://${env.AWS_PLM_REST_URL}/"+params.properties.getProperty(aws_target_key)
        target = target.replace("<BUILD_NUMBER_TO_REPLACE>", params.properties.APP_DB_BUILD_NUMBER)
        println(target)
            def temp="s3://${env.AWS_PLM_REST_URL}"
        
          withAWS(credentials:"${env.aws_credentials_id}") {
            withEnv(["http_proxy=${env.AWS_PLM_PROXY}",
            "https_proxy=${env.AWS_PLM_PROXY}",
            "PYTHONWARNINGS=${env.AWS_HTTPS_WARNINGS_IGNORE}"
            ]) {
                 
                 sh """
                aws s3 ls  s3://${env.AWS_PLM_REST_URL} --no-verify-ssl
                aws s3 cp  $WORKSPACE/${params.properties.application_artifacts_filepattern}  ${target} --sse-kms-key-id alias/data-${params.properties.aws_uai_attr.toLowerCase()}-${env.Environment.toLowerCase()}-3dspace --sse aws:kms --no-verify-ssl
                aws s3 ls  ${target} --no-verify-ssl
                aws ssm send-command --document-name "${params.properties.aws_document_name_attr}"  --targets '[{"Key":"tag:uai","Values":["${params.properties.aws_uai_attr}"]},{"Key":"tag:role","Values":["app"]},{"Key":"tag:Name","Values":["${params.properties.QA3_aws_name_attr}"]},{"Key":"tag:env","Values":["qa"]}]' --parameters '{"BuildType":["${params.properties.publishType}"],"BuildNo":["${params.properties.APP_DB_BUILD_NUMBER}"],"ReleaseTag":["${env.BRANCH_NAME}"],"emailFlag":["N"]}' --comment "test" --timeout-seconds 600 --max-concurrency "50" --max-errors "0" --region us-east-1 --no-verify-ssl
                            
                """
                
                
                /*
                aws ssm send-command --document-name "${params.properties.aws_document_name_attr}"  --targets '[{"Key":"tag:uai","Values":["${params.properties.aws_uai_attr}"]},{"Key":"tag:role","Values":["app"]},{"Key":"tag:Name","Values":["${params.properties.QA3_aws_name_attr}"]},{"Key":"tag:env","Values":["qa"]}]' --parameters '{"BuildType":["${params.properties.publishType}"],"BuildNo":["${params.properties.APP_DB_BUILD_NUMBER}"],"ReleaseTag":["${env.BRANCH_NAME}"],"emailFlag":["N"]}' --comment "test" --timeout-seconds 600 --max-concurrency "50" --max-errors "0" --region us-east-1 --no-verify-ssl
                */
                
                /*sh """
                aws s3 ls  s3://${env.AWS_PLM_REST_URL} --no-verify-ssl
                aws s3 cp  $WORKSPACE/Jenkinsfile    s3://${env.AWS_PLM_REST_URL} --sse-kms-key-id alias/data-uai3026525-qa3-3dspace --sse aws:kms --no-verify-ssl
                aws s3 ls  s3://${env.AWS_PLM_REST_URL} --no-verify-ssl
                """
                */
                
/*                         }
          }
        

}
*/
/* Email notification methods */
/*def notify(params) {
    
        emailext (
        subject: " ${currentBuild.currentResult} Secure Pipeline- '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
        body: """Automated Build: Build is starting on  ${params.properties.application}- ${params.properties.Environment} <br/> </br>
        Release:${env.BRANCH_NAME} </br>
        Build Type: ${params.properties.publishType}</br> </br>
        Build Message : ${params.buildMessage} </br> </br>
        *** This is an auto-generated email, please do not reply *** """,
        to: "${params.properties.ccm_notify_email}",
        mimeType: 'text/html'
        )
}

def notifyOnFailure(params) {
    def groupmailid = params.properties.ccm_notify_email
    
    /** To Send quality gate failed notification to POD team as well */
/*    if( params.lastStartedStage !=null && 
           (params.lastStartedStage.toLowerCase().contains("gate") || params.lastStartedStage.toLowerCase().contains("junit") )
      ) {
        println "GATE is failed"
        groupmailid = params.properties.app_notify_email
      }
        emailext (
        subject: "Build Failed - '${env.JOB_NAME} [Build No# ${env.BUILD_NUMBER}]'",
        body: """Automated Build: Build is Failed on Jenkins. <br/> </br>
        Release:${env.BRANCH_NAME} </br>
        Build Type: ${params.properties.publishType}</br>
        Failed Stage: ${params.lastStartedStage}</br>
        Build Message : ${params.buildMessage} </br> </br>
        
        *** This is an auto-generated email, please do not reply *** """,
        to: "${groupmailid}",
        mimeType: 'text/html'
        )
}


def sendJunitReportMail(params)  {

      def subject =  params.subject.replace("<ENV_TO_REPLACE>", params.Environment.toUpperCase())
      subject =  subject.replace("<APPLICATION>", params.Application)
      println(params)
    /** Send Junit build results along with log file in attachment in mail notification. **/
/*     emailext body: '''<br/> Junit Report and logs generated for the branch: ${BRANCH_NAME}. <br/> <br/>
        **  This is an automated notification.  Do not reply to this email. ** </br> </br>
        <div   style=" width:65%; border:2px solid #808080; ">
        ${FILE,path="target/report/html/junit-noframes.html"}
         </div>''', 
        mimeType: 'text/html', 
        subject: "${subject} - Result: ${params.JunitBuildresult}",
        attachmentsPattern: 'target/report/xml/TEST-TestJunitSuite.txt',
        to: "${params.mailGroup}"

}
*/


