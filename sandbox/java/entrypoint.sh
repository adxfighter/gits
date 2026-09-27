#!/bin/sh
# Reads a tar archive of sources from stdin, compiles it and runs the JUnit tests.
# Output protocol (parsed by gits-runner):
#   ===GITS-COMPILE-ERROR===                     then javac output, exit code 2
#   ===GITS-OUTPUT-BEGIN=== ... ===GITS-OUTPUT-END===   test console output (<= 64 KB)
#   ===GITS-REPORT-BEGIN=== ... ===GITS-REPORT-END===   JUnit XML report
set -eu

LIMIT=65536
SRC=/work/src
OUT=/work/out
REPORTS=/work/reports
mkdir -p "$SRC" "$OUT" "$REPORTS"

if ! tar -x -C "$SRC" 2>/work/tar.log; then
  echo "===GITS-COMPILE-ERROR==="
  echo "Не удалось распаковать исходные файлы"
  exit 2
fi

find "$SRC" -type f -name '*.java' > /work/sources.txt
if [ ! -s /work/sources.txt ]; then
  echo "===GITS-COMPILE-ERROR==="
  echo "Нет исходных файлов .java"
  exit 2
fi

CP=$(find /opt/libs -name '*.jar' | sort | tr '\n' ':')
JVM_OPTS="-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:-UsePerfData -Xshare:auto -Djava.io.tmpdir=/tmp"

# shellcheck disable=SC2086
if ! javac -J-Xmx256m -J-XX:+UseSerialGC -J-XX:TieredStopAtLevel=1 -J-XX:-UsePerfData \
    --release 21 -encoding UTF-8 -proc:none -nowarn -d "$OUT" -cp "$CP" @/work/sources.txt > /work/javac.log 2>&1; then
  echo "===GITS-COMPILE-ERROR==="
  head -c "$LIMIT" /work/javac.log
  exit 2
fi

# Test output is capped at $LIMIT bytes. Java ignores SIGPIPE (System.out swallows write errors), so a
# flooding test would keep running: when the cap is reached the JVM is killed explicitly.
mkfifo /work/test.fifo
# shellcheck disable=SC2086
java $JVM_OPTS -Xmx384m -Xss512k -XX:+EnableDynamicAgentLoading \
    -jar /opt/libs/junit-platform-console-standalone.jar execute \
    --class-path "$OUT:$CP" --scan-class-path "$OUT" \
    --reports-dir "$REPORTS" --details=tree --disable-banner --disable-ansi-colors \
    > /work/test.fifo 2>&1 &
JAVA_PID=$!
head -c "$LIMIT" /work/test.fifo > /work/test.log
if [ "$(wc -c < /work/test.log)" -ge "$LIMIT" ]; then
  kill -9 "$JAVA_PID" 2>/dev/null || true
  printf '\n[Вывод превысил %s байт, выполнение остановлено]\n' "$LIMIT" >> /work/test.log
fi
wait "$JAVA_PID" 2>/dev/null || true

echo "===GITS-OUTPUT-BEGIN==="
cat /work/test.log
echo
echo "===GITS-OUTPUT-END==="
echo "===GITS-REPORT-BEGIN==="
if [ -f "$REPORTS/TEST-junit-jupiter.xml" ]; then
  cat "$REPORTS/TEST-junit-jupiter.xml"
fi
echo "===GITS-REPORT-END==="
