#!/usr/bin/env sh

echo "Starting java -jar /explorer-backend.jar ${JAVA_NET_OPTS} ${JAVA_MEM_OPTS} ${JAVA_GC_OPTS} ${JAVA_EXTRA_OPTS} $@"
exec java -jar /explorer-backend.jar ${JAVA_NET_OPTS} ${JAVA_MEM_OPTS} ${JAVA_GC_OPTS} ${JAVA_EXTRA_OPTS} $@
