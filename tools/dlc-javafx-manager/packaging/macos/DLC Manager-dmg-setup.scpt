set volumePath to "DEPLOY_VOLUME_PATH"
set installLocation to "DEPLOY_INSTALL_LOCATION"
set installLabel to "DEPLOY_INSTALL_LOCATION_DISPLAY_NAME"
do shell script "/bin/ln -s " & quoted form of installLocation & " " & quoted form of (volumePath & "/" & installLabel)
