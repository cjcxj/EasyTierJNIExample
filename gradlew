#!/usr/bin/env sh
#
# Copyright (C) 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This file is used to set up the environment for the Android Gradle plugin.
# It is used by the Android Studio IDE and by the command line tools.

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/bin/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/bin/java"
    elif [ -x "$JAVA_HOME/bin/java" ] ; then
        JAVACMD="$JAVA_HOME/bin/java"
    else
        echo "JAVA_HOME is set, but no Java executable found in $JAVA_HOME/bin or $JAVA_HOME/jre/bin" >&2
        exit 1
    fi
else
    JAVACMD="java"
fi

# Determine the Gradle command to use.
GRADLE_OPTS=""
if [ -n "$GRADLE_HOME" ] ; then
    GRADLE_OPTS="-Dgradle.user.home=$GRADLE_HOME"
fi

# Determine the script directory.
SCRIPT_DIR=$(dirname "$0")

# Execute Gradle.
exec "$JAVACMD" $GRADLE_OPTS -jar "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" "$@"