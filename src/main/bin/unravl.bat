@echo off

setlocal EnableDelayedExpansion

Rem Run UnRAVL's main entry point.
Rem Command line arguments are UnRAVL script files to execute:
Rem unravl.bat script1.json script2.json script3.json
Rem See https://github.com/sassoftware/unravl

set UNRAVL_DIR=%~dp0..
set UNRAVL_DEV_DIR=%~dp0..\..\..
Rem echo !UNRAVL_DIR!
Rem echo !UNRAVL_DEV_DIR!

if exist !UNRAVL_DIR!\lib (
  Rem This works for a distribution, where UNRAVL is deployed in !UNRAVL_DIR!
  Rem the script is in !UNRAVL_DIR!\bin,
  Rem and all the dependent jars are in  !UNRAVL_DIR\lib!
  set UNRAVL_CLASSPATH=!UNRAVL_DIR!\lib/*;
) else (
  if exist !UNRAVL_DEV_DIR!\build\output\lib (
    Rem This is for use in the build environment where the script
    Rem is in src/main/bin
    Rem Build with:
    Rem    .\gradlew clean build
    Rem Gradle will put the UnRAVL jar in build/libs
    Rem and dependent jars in build/output/lib
    set UNRAVL_JAR_DIR=!UNRAVL_DEV_DIR!\build\libs
    set UNRAVL_LIB_DIR=!UNRAVL_DEV_DIR!\build\output\lib
    set UNRAVL_CLASSPATH=!UNRAVL_LIB_DIR!/*;!UNRAVL_JAR_DIR!/*;
    Rem echo UNRAVL_CLASSPATH is !UNRAVL_CLASSPATH!
  ) else (
    echo !UNRAVL_DIR! does not contain libraries !UNRAVL_DIR! build\libs
    echo !UNRAVL_DEV_DIR! does not contain libraries in !UNRAVL_DEV_DIR!\build\libs
    echo If in development, run:
    echo   .\gradlew clean build
    exit /B 1
  )
)

if not ["%JAVA_HOME%"] == [""] (
   PATH=%JAVA_HOME%\bin;%PATH%
)

java -Dapp.name=UNRAVL ^
       -classpath "%UNRAVL_CLASSPATH%" ^
       %UNRAVL_OPT% ^
       com.sas.unravl.Main ^
       %*

endlocal
