@echo off
rem Launcher TermUL (JAR) untuk Windows. Butuh JDK 25+ di PATH atau JAVA_HOME.
set "JAVAW=javaw"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVAW=%JAVA_HOME%\bin\javaw.exe"
start "" "%JAVAW%" --enable-native-access=ALL-UNNAMED -jar "%~dp0TermUL.jar" %*
