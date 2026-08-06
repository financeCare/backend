@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%
call gradlew.bat --stop
call gradlew.bat test --tests *AuthControllerTest.* --no-daemon > test_output_123.txt 2>&1
