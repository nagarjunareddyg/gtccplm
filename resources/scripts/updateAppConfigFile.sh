#use it for pipeline script runs from Secure pipeline or propel jenkins where we need to clone appConfig git
# repo explicitly before doing changes and also JQ is not available.

cd "applicationConfig"
configFile=$1
credentials_id_username=$2
credentials_id_token=$3
appConfigRepo_git_url=$4

echo "configFile="$configFile
echo "credentials_id_username="$credentials_id_username
echo "credentials_id_token="$credentials_id_token
echo "appConfigRepo_git_url="$appConfigRepo_git_url
#git pull

#git  remote set-url origin https://$credentials_id_username:$credentials_id_token@github.build.ge.com/502808103/applicationConfig.git
git  remote set-url origin https://$credentials_id_username:$credentials_id_token@$appConfigRepo_git_url

# set user email and name
git config --global user.email "Service.plmSecurePipeline@ge.com"
git config --global user.name "plmSecurePipeline, Service"

git status
#git add  WINDPLM/DEV/app/appConfig.json
git add $configFile
#git add .
echo "git commit  file: "
git commit -m "updating windplm appConfig for testing build from secure pipeline jenkins"

git status
echo "git push *****************"
git push origin master
echo " git push is completed...."




