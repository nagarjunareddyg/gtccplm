 pipeline {
      agent any      
      environment {
        current_ws = pwd()
        branch = "${env.BRANCH_NAME}"     
        ENV_NAME = 'dev'
        dev_ACCOUNT = '598619258634'
        qa_ACCOUNT  = '598619258634'
        prd_ACCOUNT = '907050794246'
        UAI = 'uai3026525'
        APP = 'powerplmgtcc-tr-common'
        ECR_DOCKER_REGISTRY = 'dkr.ecr.us-east-1.amazonaws.com'
        VAR_BASE_DOCKER_REGISTRY = '880379568593.dkr.ecr.us-east-1.amazonaws.com'
        plp_docker_registry='880379568593.dkr.ecr.us-east-1.amazonaws.com/uai3026525-powerplmgtcc-tr-common'
        TWISTCLI_CREDS = credentials('twistcli-credentials')
        TWISTCLI_ADDRESS="https://us-east1.cloud.twistlock.com/us-2-158286731/api/v1/authenticate"
        stage('twistcli download and image scan') {
                steps {
                    script {
                      println("twistcli download and image scan")
                      withCredentials([usernamePassword(credentialsId: 'twistcli-credentials', passwordVariable: 'TWISTLOCK_PASS', usernameVariable: 'TWISTLOCK_USER')]){
                      sh '''
                            curl -k -u ${TWISTLOCK_USER}:${TWISTLOCK_PASS} --output ./twistcli ${TWISTCLI_ADDRESS}/api/v1/util/twistcli
                            chmod a+x ./twistcli
                        JWT=`curl -k -X POST ${TWISTCLI_ADDRESS} -H "Content-Type: application/json" -d '{"username": "'${TWISTLOCK_USER}'", "password":"'${TWISTLOCK_PASS}'"}' | jq -r .token`
                            
                            echo ${JWT}
                            ./twistcli images scan ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplminternal --publish --address ${TWISTCLI_ADDRESS} --token "'${JWT}'"  || true
                            ./twistcli images scan ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplmcad --publish --address ${TWISTCLI_ADDRESS} --token "'${JWT}'"  || true
                          ./twistcli images scan ${VAR_BASE_DOCKER_REGISTRY}/${UAI}-${APP}:gtccplm --publish --address ${TWISTCLI_ADDRESS} --token "'${JWT}'"  || true
                            rm -f twistcli
                      '''
                      }
                      
                    }
                }
            }
