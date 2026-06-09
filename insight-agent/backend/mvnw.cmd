@REM ----------------------------------------------------------------------------
@REM Local Maven Wrapper - points at the Maven binary unpacked into ../../.tools.
@REM Usage:  mvnw <goals>     (e.g. mvnw spring-boot:run)
@REM No system Maven install required.
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
set "MAVEN_HOME=%PROJECT_DIR%\..\..\.tools\apache-maven-3.9.9"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo [mvnw] ERROR: Maven not found at "%MAVEN_HOME%".
    echo [mvnw] Re-download with: curl -L -o ../.tools/maven.zip https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
    exit /b 1
)

call "%MAVEN_HOME%\bin\mvn.cmd" %*
