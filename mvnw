#!/usr/bin/env sh
MAVEN_PROJECTBASEDIR=`dirname "$0"`
if [ ! -d "$MAVEN_PROJECTBASEDIR" ]; then
  MAVEN_PROJECTBASEDIR=`pwd`
fi
exec java -cp "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar" -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" org.apache.maven.wrapper.MavenWrapperMain "$@"
