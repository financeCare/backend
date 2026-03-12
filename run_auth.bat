@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%
gradlew.bat test --tests *AuthControllerTest.testSendOtp_Success*
