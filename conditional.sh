#!/bin/bash
export Environment=$1
Environment_app="cdev1"
if [ "$Environment" == "DEV1" ]
then
  export Environment=cdev1
 #Environment= 'echo $Environment | sed -e "s/DEV1/$Environment_app/g"+'
#  env=$(new)
else
  echo "nothing"
fi
# Environment=$(env)
 echo "this is new environment assing to GTCCPLM: $Environment"
