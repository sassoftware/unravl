#!/bin/sh

# Run UnRAVL's main entry point.
# Command line arguments are UnRAVL script files to execute:
# unravl script1.json script2.json script3.json
# See https://github.com/sassoftware/unravl

UNRAVL_DIR=`dirname $0`/..
UNRAVL_DEV_DIR=`dirname $0`/../../..

if [ -d "$UNRAVL_DIR/lib" ]
then # This works for a distribution, where UNRAVL is deployed in $UNRAVL_DIR,
     # the script is in $UNRAVL_DIR/bin,
     # and all the dependent jars are in  $UNRAVL_DIR/lib
     UNRAVL_CLASSPATH="$UNRAVL_DIR/lib/*:"
elif [ -d $UNRAVL_DEV_DIR/build/classes -a -d $UNRAVL_DIR/../../build/output/lib ]
then # This is for use in the build environment where the script
     # is in src/main/bin
     # Build with:
     #    ./gradlew clean build copyDeps
     # Gradle will put the UnRAVL jar in build/libs
     # and dependent jars in build/output/lib
     UNRAVL_JAR_DIR="$UNRAVL_DEV_DIR/build/libs"
     UNRAVL_LIB_DIR="$UNRAVL_DEV_DIR/build/output/lib"
     UNRAVL_CLASSPATH="$UNRAVL_LIB_DIR/*:$UNRAVL_JAR_DIR/*"
else echo $UNRAVL_DIR or $UNRAVL_DEV_DIR do not contain libraries
     echo If in development, run:
     echo   ./gradlew clean build copyDeps
     exit 1
fi

if [ -x "$JAVA_HOME/bin/java" ]
then java="$JAVA_HOME/bin/java"
else java=java
fi

$java -Dapp.name=UNRAVL \
      -classpath $UNRAVL_CLASSPATH \
      $UNRAVL_OPT \
      com.sas.unravl.Main \
      "$@"
