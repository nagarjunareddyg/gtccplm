appconfig_workspace_path=$1
fileName=$2
Build_Number=$3
PublishType=$4
ModuleName=$5

echo "appconfig_workspace_path="$appconfig_workspace_path
echo "fileName="$fileName
echo "Build_Number="$Build_Number
echo "PublishType="$PublishType
echo "ModuleName="$ModuleName
#cd /usr/share/git/applicationConfig/

cd $appconfig_workspace_path
git pull

#FILE=/usr/share/git/applicationConfig/$Application/$Application-3DSPACE-$Environment/app/appConfig.json
#TEMPFILE=/usr/share/git/applicationConfig/$Application/$Application-3DSPACE-$Environment/app/temp.json

FILE=$appconfig_workspace_path/$fileName
TEMPFILE=temp.json

if [ -f $FILE ];
then
   echo "File $FILE exists." 

case "$PublishType" in
'all')
echo "all"
command_publish="jq 'map(if .components[0].Module_Name == "\"$ModuleName"\" then .components[0].Build_Number = $Build_Number elif .components[2].Module_Name == "\"$ModuleName"\" then .components[2].Build_Number = $Build_Number else . end)' $FILE > $TEMPFILE"
eval $command_publish

cp $TEMPFILE $FILE

command_publish="jq 'map(if .components[1].Module_Name == "\"$ModuleName"\" then .components[1].Build_Number = $Build_Number elif .components[3].Module_Name == "\"$ModuleName"\" then .components[3].Build_Number = $Build_Number else . end)' $FILE > $TEMPFILE"
eval $command_publish
;;
'app')
echo "app"
command_publish="jq 'map(if .components[0].Module_Name == "\"$ModuleName"\" then .components[0].Build_Number = $Build_Number elif .components[2].Module_Name == "\"$ModuleName"\" then .components[2].Build_Number = $Build_Number else . end)' $FILE > $TEMPFILE"
eval $command_publish
;;
'db')
echo "db"
command_publish="jq 'map(if .components[1].Module_Name == "\"$ModuleName"\" then .components[1].Build_Number = $Build_Number elif .components[3].Module_Name == "\"$ModuleName"\" then .components[3].Build_Number = $Build_Number else . end)' $FILE > $TEMPFILE"
eval $command_publish
;;
esac


cp $TEMPFILE $FILE

cat $FILE

rm $TEMPFILE || true

git pull
git add $fileName
git commit -m "Updating appConfig for $fileName"
git push origin master

fi
