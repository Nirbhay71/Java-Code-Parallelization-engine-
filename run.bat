@echo off
set CP="bin;lib/javaparser-core-3.26.1.jar"
set MAIN_CLASS=com.parallelizer.demo.Main

if "%~1"=="" (
    echo Usage: run.bat [input_file.java]
    echo Example: run.bat input.java
    exit /b 1
)

java -cp %CP% %MAIN_CLASS% %~1
