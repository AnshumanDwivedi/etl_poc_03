#!/bin/bash

echo ${MVN_SETTINGS} | base64 -d > ${HOME}/.m2/settings.xml
echo ${MVN_SECURITY} | base64 -d > ${HOME}/.m2/settings-security.xml
echo ${GPG_KEY} | base64 -d | gpg --import

mvn clean deploy scala:doc -ntp -B -DskipTests -Prelease -P${SCALA_PROFILE}