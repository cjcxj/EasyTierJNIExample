@rem
@rem Copyright (C) 2017 The Android Open Source Project
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      http://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@rem This file is used to set up the environment for the Android Gradle plugin.
@rem It is used by the Android Studio IDE and by the command line tools.

@rem Determine the Java command to use to start the JVM.
@if not "%JAVA_HOME%" == "" (
    @if exist "%JAVA_HOME%\jre\bin\java.exe" (
        @set JAVACMD=%JAVA_HOME%\jre\bin\java.exe
    ) else if exist "%JAVA_HOME%\bin\java.exe" (
        @set JAVACMD=%JAVA_HOME%\bin\java.exe
    ) else (
        @echo JAVA_HOME is set, but no Java executable found in %JAVA_HOME%\bin or %JAVA_HOME%\jre\bin >&2
        @exit /b 1
    )
) else (
    @set JAVACMD=java.exe
)

@rem Determine the Gradle command to use.
@set GRADLE_OPTS=
@if not "%GRADLE_HOME%" == "" (
    @set GRADLE_OPTS=-Dgradle.user.home="%GRADLE_HOME%"
)

@rem Determine the script directory.
@for %%i in ("%~dp0.") do @set SCRIPT_DIR=%%~fsi

@rem Execute Gradle.
"%JAVACMD%" %GRADLE_OPTS% -jar "%SCRIPT_DIR%\gradle\wrapper\gradle-wrapper.jar" %*