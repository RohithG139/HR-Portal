@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@echo off
setlocal enableDelayedExpansion

set "MAVEN_VERSION=3.9.9"
set "DIST_DIR=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%"
set "DIST_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"

if not exist "%DIST_DIR%\bin\mvn.cmd" (
    echo Downloading Apache Maven %MAVEN_VERSION%...
    if not exist "%USERPROFILE%\.m2\wrapper\dists" (
        mkdir "%USERPROFILE%\.m2\wrapper\dists"
    )
    powershell -NoProfile -Command ^
        "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%TEMP%\maven-wrapper-%MAVEN_VERSION%.zip'"
    powershell -NoProfile -Command ^
        "Expand-Archive -Path '%TEMP%\maven-wrapper-%MAVEN_VERSION%.zip' -DestinationPath '%USERPROFILE%\.m2\wrapper\dists\' -Force"
    del "%TEMP%\maven-wrapper-%MAVEN_VERSION%.zip" 2>nul
)

"%DIST_DIR%\bin\mvn.cmd" %*
endlocal
