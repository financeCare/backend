@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%
gradlew.bat test --tests *DebugTest* > debug_output.txt 2>&1
