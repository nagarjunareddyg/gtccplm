#!/usr/bin/groovy
package ge.plm.pipeline;
import ge.plm.pipeline.PipelineUtils;
/**
PLM CCM Team
**/ 

/**
Desc : For Spinner build and generate tar file.
**/

def spinnerBuild(params) {
    /** Delete existing libs_server and props_server text files before spinner build.*/
        try {
        sh "rm -f ${env.current_ws}/${params.properties.git_base_folder}/libs_server_locations.txt"
        sh "rm -f ${env.current_ws}/${params.properties.git_base_folder}/props_server_locations.txt"
        } catch(Exception ex)
        {
        println(ex)
        }
    
         // Run spinner build
          withAnt(installation: env.antTool, jdk: params.properties.jdk) {
       
          sh "ant -v  \
         -Dvcs-tag=${env.BRANCH_NAME} \
         -Dbas-dir=${env.current_ws}/${params.properties.webDirectory} \
         -Ddb-bas-dir=${env.current_ws} \
         -Denv-tag=${env.Environment} \
          -Dclasspath-lib=${params.properties.jenkins_pwwebplm_lib_path} \
          -Dclasspath-bin=${params.properties.jenkins_pwwebplm_bin_path} \
          -f  ${params.properties.git_base_folder}/${params.properties.buildFile}  \
          ${params.properties.build_targets} "
          
         
          }
         
         /* To generate spinner tar file */
         // generateTarFile(params)
}

/** Desc : Web build **/
def webBuild(params) {
    
        // Clean up
        try {
            //to test it in power jenkins
            println "Web build .."
            println "${env.BRANCH_NAME}"
            println "${env.current_ws}"
            println "${env.Environment}"
            println "${params.properties.jenkins_pwwebplm_lib_path}"
            println "${params.properties.jenkins_pwwebplm_bin_path}"
            println "${params.properties.buildFile}"
            println "${env.antTool}"
            println "${params.properties.jdk}"
        //sh "rm ${env.current_ws}/${params.properties.git_base_folder}/war/build"
        } catch(ex) {}

    myvariable = "${env.Environment}"
    if (env.Environment == 'DEV1') {
        myvariable = 'cdev1'
    }
    println "${myvariable}"
    //withAnt(installation: env.antTool, jdk: params.properties.jdk) {
        sh "ant   \
            -Dvcs-tag=Dev_3_0_220 \
        -Dbas-dir=${env.current_ws} \
        -Denv-tag=${myvariable} \
         -Dclasspath-lib=${params.properties.jenkins_pwwebplm_lib_path} \
         -Dclasspath-bin=${params.properties.jenkins_pwwebplm_bin_path} \
        -f  ${params.properties.buildFile} \
        ${params.properties.build_targets}"
    //    }
    
}

/**
Desc : Get the corresponding web branch or master web branch code for the spinner build
*/

def initilizeWebCodeForSpinnerBuild(params) {
        // checkoout corresponding web branch : otherwise master (pending)
        PipelineUtils  pipelineUtils = new PipelineUtils()
        pipelineUtils.checkOutGitCode([targetDirectory:params.properties.webDirectory, branchName:env.WebRelease, gitRepositoryURL:params.properties.webcode_git_repo_url])  
            
        // copy build files to spinner code 
        copySpinnerBuildRelatedFilesFromWeb(params)  
    
}


def copySpinnerBuildRelatedFilesFromWeb(params) {
    try {
sh "rm -f  ${env.current_ws}/${params.properties.git_base_folder}/${params.properties.git_base_folder}.tar.gz"

sh "rm -f ${env.current_ws}/${params.properties.git_base_folder}/libs_server_locations.txt"
sh "rm -f ${env.current_ws}/${params.properties.git_base_folder}/props_server_locations.txt"
    
sh "cp -rf ${env.current_ws}/${params.properties.webDirectory}/${params.properties.web_repo_base_folder}/ant/build/${params.properties.buildFile}  ${env.current_ws}/${params.properties.git_base_folder}/"
sh "cp -rf ${env.current_ws}/${params.properties.webDirectory}/${params.properties.web_repo_base_folder}/ant/build/build.properties  ${env.current_ws}/${params.properties.git_base_folder}/"
sh "cp -rf ${env.current_ws}/${params.properties.webDirectory}/${params.properties.web_repo_base_folder}/ant/build/run_spinner.sh  ${env.current_ws}/${params.properties.git_base_folder}/"
    } catch(Exception ex)
    {
        println(ex)
    }
}


/*def generateTarFile(params) {
    sh "tar --exclude-vcs -zcvf ${params.properties.artifact_name}  ${params.properties.git_base_folder}/"
}
*/

def writeLastDeployedJsp(params) {
        Date currentTime= new Date()
        def fileContent="<html>\n"
        fileContent=fileContent+ "<head>\n"
        fileContent=fileContent+ "<style type=text/css>\n"
        fileContent=fileContent+ ".f2 {font-size:11px;font-family:Verdana,Arial,Geneva;text-decoration:none;}\n"
        fileContent=fileContent+ "</style>\n"
        fileContent=fileContent+ "</head>\n"
        fileContent=fileContent+ "<body>\n"
        fileContent=fileContent+ "<span class=f2>Last deployed at ${currentTime}</span>\n"
        fileContent=fileContent+ "</body>\n"
        fileContent=fileContent+ "</html>\n"
    
        writeFile file: "${params.properties.base_folder}/${params.properties.lastdeployed_jsp}", text: "${fileContent}"
    
        /*File file = new File("${env.current_ws}/${params.properties.base_folder}/${params.properties.lastdeployed_jsp}")
        Date currentTime= new Date()
    
        def writer = file.newWriter() 
        writer << "This is the first line\n"

        writer << "<html>\n"
        writer << "<head>\n"
        writer << "<style type=text/css>\n"
        writer << ".f2 {font-size:11px;font-family:Verdana,Arial,Geneva;text-decoration:none;}\n"
        writer << "</style>\n"
        writer << "</head>\n"
        writer << "<body>\n"
        writer << "<span class=f2>Last deployed at ${currentTime}</span>\n"
        writer << "</body>\n"
        writer << "</html>\n"
        writer.close()
        */
}
