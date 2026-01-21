import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("application")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
}

group = "ext.mods.fakeplayer"
version = "3.0-HybridEngine"

// 1. Configuração de Compatibilidade (Igual ao BrProject)
// Usamos o JDK 25 para compilar, mas geramos bytecode 21 (limite do Kotlin)
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // === REDE & I/O ===
    implementation("io.netty:netty-all:4.1.100.Final")
    
    // === LOGGING ===
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // === KOTLIN & COROUTINES (Zero GC) ===
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    // Versão alinhada com seu outro projeto
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0") 

    // === PONTE COM O SERVIDOR ===
    // O BrFakeEngine precisa ler as classes do BrProject para compilar
    compileOnly(files("libs/server.jar"))
}

application {
    mainClass.set("ext.mods.fakeplayer.Main")
}

// 2. Configuração do Compilador Kotlin
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        // Kotlin ainda não suporta target 25, então fixamos em 21
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions"
        )
    }
}

// 3. Configuração do Compilador Java
tasks.withType<JavaCompile>().configureEach {
    // AQUI ESTÁ O SEGREDO: Dizemos ao Java 25 para gerar bytecode compatível com 21
    // Isso resolve o erro "Inconsistent JVM-target compatibility"
    options.release.set(21) 
    options.encoding = "UTF-8"
}

// 4. Rotina de Build e Distribuição
tasks.jar {
    archiveFileName.set("FakePlugin.jar")

    manifest {
        attributes(
            "Main-Class" to "ext.mods.fakeplayer.Main",
            "Built-By" to System.getProperty("user.name")
        )
    }
    
    // Cria o Fat Jar (inclui Netty e Kotlin StdLib dentro do jar)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    doLast {
        // 1. Copia para o GameServer (BrProject)
        // Ajuste este caminho se necessário, baseado na sua estrutura de pastas
        copy {
            from(archiveFile)
            into("C:/Users/Victor Hugo/Documents/2_8_Brproject_New-main/libs")
        }
        
        // 2. Copia para teste local (Standalone)
        copy {
            from(archiveFile)
            into("build/libs")
            rename { "FakeServer.jar" }
        }
        
        println(">> SUCESSO! FakePlugin compilado com Toolchain 25 (Target 21).")
    }
}