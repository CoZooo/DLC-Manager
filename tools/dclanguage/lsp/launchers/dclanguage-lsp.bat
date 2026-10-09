@echo off
if defined JAVA_HOME (
  "%JAVA_HOME%\bin\java.exe" -jar "%~dp0dclanguage-lsp.jar" --stdio
) else (
  java -jar "%~dp0dclanguage-lsp.jar" --stdio
)
