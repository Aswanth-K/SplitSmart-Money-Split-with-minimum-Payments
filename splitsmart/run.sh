#!/bin/sh
set -eu
cd "$(dirname "$0")"
./mvnw -q -DskipTests package
exec java -jar target/splitsmart-1.0.0.jar
