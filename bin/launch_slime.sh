#!/bin/bash

SLIME_JAR=`ls dist/*`
LIBS=$(find lib -name "*.jar" | tr '\n' ':')

mkdir -p logs

java $* -cp $SLIME_JAR:$LIBS edu.columbia.slime.example.Example1 &

