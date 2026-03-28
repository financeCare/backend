@echo off
set JAVA_HOME=C:\Program Files\Common Files\Oracle\Java\javapath
gradlew.bat test --tests com.example.capstone.service.RepaymentServiceTest --info > run_log.txt 2>&1
