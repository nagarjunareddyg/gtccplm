#!/usr/bin/groovy
package ge.plm.pipeline;
import ge.plm.pipeline.PipelineUtils;

def runSast(params) {
      
    /** CREATE stream name (branch name) in Coverity server if its not created already */
    def coverity_stream_name = gtccplm-cloud-dev
    coverity_stream_name = coverity_stream_name.replace("<BRANCH>", env.BRANCH_NAME)
     
    if(! isStreamExist(params,coverity_stream_name)) {
        createNewStream(params,coverity_stream_name)
    }
        
     /** Below logic is to Run Coverity build, Analysis and Commit defects */
    withCoverityEnvironment(coverityInstanceUrl: params.properties.coverityInstanceUrl){
        try {
        sh 'rm -Rf "${COV_DIR}/"' 
        println "Stream Name:"+coverity_stream_name
        sh "cov-configure --java"
        //sh "cov-configure --javascript"
                                      
                withAnt(installation:  env.antTool, jdk: params.properties.jdk) {

                 /** STEP1: Run coverity Build **/
                                 if (params.properties.publishType == 'app') {
                             
                    sh " ant   \
                                    -Dssh-user=afn38x5  \
                                    -Dvcs-tag=Dev_3_0_220 \
                                    -Dbas-dir=${env.current_ws} \
                                    -Denv-tag=${env.Environment} \
                                    -Dclasspath-lib=${params.properties.jenkins_pwwebplm_lib_path} \
                                    -Dclasspath-bin=${params.properties.jenkins_pwwebplm_bin_path} \
                                    -f  ${params.properties.buildFile} \
                                    ${params.properties.coverity_build_targets}"
                                    
                                    /** For GE specific JSP files
                                     */
                   def coverity_files_exclude_scm_regex_formatted = getCoveritySCMExcludeRegexFormattedString(params)
                                              
                                    sh "rm -rf ${env.current_ws}/scm_ge_specific_files.lst"
                                    sh "find  ${env.current_ws}/${params.properties.base_folder}/war/build \
                     -type f -name '${params.properties.coverity_files_include_scm_regex}' \
                     ${coverity_files_exclude_scm_regex_formatted}>> scm_ge_specific_files.lst"
                                    
                                    sh "cov-build \
                      --dir ${COV_DIR} \
                                    --fs-capture-list  ${env.current_ws}/scm_ge_specific_files.lst \
                                    --no-command"
                                    
                              } 
                else if (params.properties.publishType == 'db') {
                                             sh "cov-build  \
                                              --dir ${COV_DIR} \
                                              ant  \
                                             -Dssh-user=afn38x5  \
                                             -Dvcs-tag=Dev_3_0_220 \
                                             -Dbas-dir=${env.current_ws}/${params.properties.webDirectory} \
                                             -Ddb-bas-dir=${env.current_ws} \
                                             -Denv-tag=${env.Environment} \
                                             -Dclasspath-lib=${params.properties.jenkins_pwwebplm_lib_path} \
                                     -Dclasspath-bin=${params.properties.jenkins_pwwebplm_bin_path} \
                                             -Dcoverity_postprod_branch_workspace=${params.properties.coverity_postprod_branch_workspace} \
                                             -f  ${params.properties.git_base_folder}/${params.properties.buildFile}  \
                                             ${params.properties.coverity_build_targets}"
                      
                        // include spinner folder to run custom checkers like password search on code
                            sh "cov-build \
                        --dir ${COV_DIR} \
                        --fs-capture-search ${env.current_ws}/${params.properties.git_base_folder} \
                        --no-command"
                                   } 
                               }
        
        /** STEP2: REMOVE jars from Coverity Scan **/
                    // list all emitted files
                                          sh "cov-manage-emit --dir ${COV_DIR} list "
                    
                    // Remove all jar files from scan
                                          echo 'remove Jar files from coverity analyis'
                                          sh "cov-manage-emit \
                                          --dir ${COV_DIR} \
                                          --tu-pattern \"file(\'.*/*.jar.*\') \" \
                                          delete"
                
        /** STEP3: Run Coverity Analysis **/
                    echo 'cov-analyze Started ***'       
                       if (params.properties.publishType == 'app') {
                        sh "cov-analyze \
                         --dir ${COV_DIR} \
                        --webapp-security \
                        --strip-path ${env.current_ws}/ \
                        --disable-fb"
                    } 
                       else if (params.properties.publishType == 'db') {
                      def customCheckersJsonFile = new PipelineUtils().copyGlobalLibraryScript('coverity_custom_checkers/plm_coverity_text_checkers.json')
                        sh "cov-analyze \
                        --dir ${COV_DIR} \
                        --strip-path ${env.current_ws}/${params.properties.webDirectory}/spinner_all_jpos/ \
                        --strip-path ${env.current_ws}/${params.properties.webDirectory}/ \
                        --strip-path ${env.current_ws}/ \
                        --disable-fb \
                        --directive-file ${customCheckersJsonFile} \
                        --enable TEXT.PLM_HARDCODED_PASSWORD"
                    } 
        
        
                 /*     sh "cov-format-errors  --dir ${COV_DIR}   --html-output ${COV_DIR}/Report"
                  */
        
            /** STEP4: Commit Defects **/
                    sh "cov-commit-defects \
                    --dir ${COV_DIR} \
                    --url ${COV_URL} \
                    --description  Build#${env.BUILD_NUMBER} \
                    --stream ${coverity_stream_name} --debug "
        } catch(Exception ex) {
                    
                    echo '*** Coverity Error LOG (build-log) ***'
                    sh "[ -f ${COV_DIR}/build-log.txt ] &&  cat ${COV_DIR}/build-log.txt || echo 'No Coverity build log file exist' "
                    echo '*** Coverity Error LOG (jsp-compilation-log) ***'
                    sh "[ -f ${COV_DIR}/jsp-compilation-log.txt ] &&  cat ${COV_DIR}/jsp-compilation-log.txt || echo 'No Coverity Jsp compile log file exist' "
                    echo '*** Coverity Error LOG (output/commit-error-log.txt) ***'
                    sh "[ -f ${COV_DIR}/output/commit-error-log.txt ] &&  cat ${COV_DIR}/output/commit-error-log.txt || echo 'No Coverity commit-error-log.txt log file exist' "
                    throw new Exception("Throw to stop pipeline")
                    
                } //catch
                   } // with coverity env
 } //method end

/* Util methods to create stream in coverity */
def isStreamExist(params, coverity_stream_name ) {
    def isStreamExist = false
    println " Checking isStreamExist: "+ coverity_stream_name
    
    
    // withCoverityEnv(coverityToolName: env.coverityTool, connectInstance: env.coverity_server) {
    withCoverityEnvironment(coverityInstanceUrl: params.properties.coverityInstanceUrl){
         /** check if Stream exists*/
        try {
             def current_streams = sh returnStdout: true, script: "cov-manage-im \
                                --mode streams \
                                --show --stream  ${coverity_stream_name} \
                                --url ${COV_URL} "

            println current_streams 
            if(current_streams.contains("${coverity_stream_name},") ) {
                
                   if(current_streams.contains("${params.properties.coverity_project_name},")) {
                    isStreamExist = true
                  }
                  /** Delete the steram if its created but not assigned to project */
                else {
                     sh "cov-manage-im  \
                    --mode streams \
                    --delete --stream ${coverity_stream_name} \
                    --url ${COV_URL} "
                 }
                
            }


         } catch(Exception ex) {
            println "Exception is thrown.."
            println ex
         }
     }
    
    println " isStreamExist: "+ coverity_stream_name + ": " +isStreamExist
    return isStreamExist
}
    
    
def createNewStream(params , coverity_stream_name) {
        withCoverityEnvironment(coverityInstanceUrl: params.properties.coverityInstanceUrl){
    
             /** Create Stream if it not exist */
                sh "cov-manage-im  \
                --mode streams \
                --add --set name:${coverity_stream_name} \
                --url ${COV_URL} "
                

              /** Associate new Stream with project */

                sh "cov-manage-im  \
                 --mode projects --name ${params.properties.coverity_project_name}  \
                 --update --insert stream:${coverity_stream_name} \
                 --url ${COV_URL} "
                
             }
     
    println "NewStream is Crated:"+coverity_stream_name
    return coverity_stream_name
}

def getCoveritySCMExcludeRegexFormattedString(params) {
    def exclude_const="-not -name"
    def exclude_pattern_formatted=""
    try {
    if(params.properties.coverity_files_exclude_scm_regex != null){
    for (String exclude_file :  params.properties.coverity_files_exclude_scm_regex.split()) {
    exclude_pattern_formatted="$exclude_pattern_formatted $exclude_const $exclude_file"
    }
    }
    }catch(Exception ex) {
        println ex
    }
    return exclude_pattern_formatted
}
