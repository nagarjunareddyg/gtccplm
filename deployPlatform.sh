#!/bin/bash

export ENV_NAME=$1
export CAS_PLATFORM_FOLDER=$2
export NONCAS_PLATFORM_FOLDER=$3

echo " ./platform/${ENV_NAME}/${CAS_PLATFORM_FOLDER}/deploy.sh"
echo "$ENV_NAME : $CAS_PLATFORM_FOLDER : $PWD : test -f ./platform/${ENV_NAME}/${CAS_PLATFORM_FOLDER}/deploy.sh"

# deploying CAS nodes
test -f ./platform/${ENV_NAME}/${CAS_PLATFORM_FOLDER}/deploy.sh && \
      cd ./platform/${ENV_NAME}/${CAS_PLATFORM_FOLDER}/ && \
      chmod +x deploy.sh && \
      mkdir -p dev1 && \
      ./deploy.sh || echo "CAS node configurations are not setup"

# deploying NON-CAS  nodes
cd -
echo " ./platform/${ENV_NAME}/${NONCAS_PLATFORM_FOLDER}/deploy.sh"
echo "$ENV_NAME : ${NONCAS_PLATFORM_FOLDER} : $PWD : test -f ./platform/${ENV_NAME}/${NONCAS_PLATFORM_FOLDER}/deploy.sh"

test -f ./platform/${ENV_NAME}/${NONCAS_PLATFORM_FOLDER}/deploy.sh && \
      cd ./platform/${ENV_NAME}/${NONCAS_PLATFORM_FOLDER}/ && \
      chmod +x deploy.sh && \
      mkdir -p dev1 && \
      ./deploy.sh || echo "NON-CAS node configurations are not setup"
