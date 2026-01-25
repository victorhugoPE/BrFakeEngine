import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("application")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
}

group = "ext.mods.fakeplayer"
version = "3.0-HybridEngine"

// Apenas o caminho para jogar o JAR final
val serverLibsPath = "C:/Users/Victor Hugo/Documents/2_8_Brproject_New-main/libs"

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
    // CompileOnly para o servidor (evita crash)
    compileOnly("io.netty:netty-all:4.1.100.Final")
    compileOnly("org.slf4j:slf4j-api:2.0.7")
    compileOnly("ch.qos.logback:logback-classic:1.4.11")

    // Kotlin embutido
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0") 
    
    // Core do Servidor
    compileOnly(files("libs/server.jar"))
}

application {
    mainClass.set("ext.mods.fakeplayer.Main")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xno-call-assertions", "-Xno-param-assertions", "-Xno-receiver-assertions")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21) 
    options.encoding = "UTF-8"
}

// === TAREFA 1: JAR DO PLUGIN (Leve, Embutido) ===
tasks.jar {
    archiveFileName.set("FakePlugin.jar")

    manifest {
        attributes("Built-By" to System.getProperty("user.name"))
    }
    
    // Inclui classes compiladas + RESOURCES (Onde está o HTML agora!)
    from(sourceSets.main.get().output)
    
    // Inclui dependências embutidas (Kotlin)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    doLast {
        // Copia apenas o JAR para o servidor
        copy {
            from(archiveFile)
            into(serverLibsPath)
        }
        println(">> SUCESSO! FakePlugin.jar (Com Dashboard Embutido) copiado.")
    }
}

// === TAREFA 2: JAR DO CONSOLE (Pesado) ===
tasks.register<Jar>("buildConsole") {
    group = "build"
    archiveFileName.set("FakeServer.jar")
    
    manifest {
        attributes("Main-Class" to "ext.mods.fakeplayer.Main")
    }

    from(sourceSets.main.get().output)
    
    // Inclui Netty para o console funcionar
    from(configurations.compileClasspath.get()
        .filter { it.name.contains("netty") || it.name.contains("slf4j") || it.name.contains("logback") }
        .map { if (it.isDirectory) it else zipTree(it) })
    
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    
    doLast {
        println(">> CONSOLE GERADO: build/libs/FakeServer.jar")
    }
}

tasks.build {
    dependsOn("buildConsole")
}