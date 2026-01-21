@echo off
title BrFakeServer - Brain Standalone
color 0A
:start
echo.
echo -------------------------------------------------------------------------------
echo                   Iniciando Servidor de Inteligencia (Fakes)
echo -------------------------------------------------------------------------------
echo.

REM Caminho para o JAR. O Gradle gera dentro de build/libs
java -Xms512m -Xmx1G -jar build/libs/FakeServer.jar

if ERRORLEVEL 1 goto error
goto end

:error
echo.
echo O Servidor de Fakes caiu ou nao foi encontrado!
echo Verifique se voce rodou o 'gradlew clean build' antes.
pause

:end
echo.
echo O servidor foi encerrado.
pause