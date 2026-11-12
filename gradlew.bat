@echo off
set DIR=%~dp0
cd /d "%DIR%"
java -cp "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
