#!/bin/sh
set -e
set -x
mvn dependency:build-classpath "-Dmdep.outputFile=cp.txt"
export TMPFILE

( 
    echo '#!/bin/sh'
    echo "exec java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8887 -cp $(cat cp.txt):target/classes: io.opsit.explang.JLineMain \"\$@\""
) > runrepl.sh
rm cp.txt
chmod 755 runrepl.sh 

