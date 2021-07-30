#!/bin/bash

# Load JAR location and other variables that may have been set
if [ -f .env ]; then
  source .env
fi

echo "Jar Location: $APP_JAR_LOCATION"

set -x
java $JVM_OPTS \
  -cp "$LIB_DIR/*":"$APP_JAR_LOCATION" \
  "$APP_MAIN_CLASS" $APP_ARGS
