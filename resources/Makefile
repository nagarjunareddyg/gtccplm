#Environment variable to include any project specific variables
include local.env

# Common settings
include Makefile.settings

JWT := $$(curl -s -X POST ${TWISTCLI_ADDRESS}/api/v1/authenticate -H "Content-Type: application/json" -d '{"username":"${TWISTCLI_CREDS_USR}","password":"${TWISTCLI_CREDS_PSW}"}' | jq -r .token)

.PHONY: buildplatform
	
buildplatform:
	${INFO} "Creating buildplatform..$(ENV_NAME), $(UPDATE_SERVICE_FLAG)"
	chmod +x ./platform/deployPlatform.sh
	./platform/deployPlatform.sh $(ENV_NAME) $(CAS_PLATFORM_FOLDER)
