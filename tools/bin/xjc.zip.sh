#!/bin/sh
#
# Copyright 2004 Sun Microsystems, Inc. All rights reserved.
# SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
#

# Script to run XJC
#

# Resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '.*/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

WEBSERVICES_LIB=$PRG/../../..

# TODO: figure out where to find jsr173_1.0_api.jar and activation.jar

# Set CLASSPATH
CLASSPATH=`tr '\n' ':' <<EOF
$WEBSERVICES_LIB/jaxb/lib/jaxb-api.jar
$WEBSERVICES_LIB/jaxb/lib/jaxb-xjc.jar
$WEBSERVICES_LIB/jaxb/lib/jaxb-impl.jar
$WEBSERVICES_LIB/jaxb/lib/jaxb1-impl.jar
EOF`

if [ -n "$JAVA_HOME" ]
then
    JAVA=$JAVA_HOME/bin/java
else
    JAVA=java
fi
 
$JAVA $XJC_OPTS -cp "$CLASSPATH" com.sun.tools.xjc.Driver "$@"
