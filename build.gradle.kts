plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
    id("org.jetbrains.dokka") version "2.1.0"
    id("io.github.ben-manes.versions") version "0.64.0"
}

group = "dev.Aragorn"
version = "0.0.1-SNAPSHOT"
description = "Portafolio-Api"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-rest")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("io.jsonwebtoken:jjwt-impl:0.13.0")
    implementation("io.jsonwebtoken:jjwt-jackson:0.13.0")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.1.0")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-rest-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webclient-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}
tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/test/html"))
    }

    doLast {
        // Intentar copiar el CSS personalizado desde /custom/report.css
        val customCssFile = file("${projectDir}/custom/report/report.css")
        val jacocoHtmlDir = file("${layout.buildDirectory.get()}/reports/jacoco/test/html")
        val targetCssFiles = listOf(
            file("${jacocoHtmlDir}/jacoco-resources/report.css"),
            file("${jacocoHtmlDir}/.resources/report.css"),
            file("${jacocoHtmlDir}/report.css")
        )

        if (customCssFile.exists()) {
            println("✅ CSS personalizado encontrado en: ${customCssFile.absolutePath}")

            // Copiar a todas las ubicaciones posibles de JaCoCo
            targetCssFiles.forEach { targetFile ->
                try {
                    targetFile.parentFile.mkdirs()
                    customCssFile.copyTo(targetFile, overwrite = true)
                    println("✅ CSS copiado a: ${targetFile.absolutePath}")
                } catch (e: Exception) {
                    println("⚠️  No se pudo copiar CSS a ${targetFile.absolutePath}: ${e.message}")
                }
            }

            println("🎨 CSS personalizado aplicado correctamente")
        } else {
            println("⚠️  CSS personalizado no encontrado en: ${customCssFile.absolutePath}")
            println("📋 Usando CSS por defecto de JaCoCo")
            println("💡 Para usar CSS personalizado, coloca el archivo en: custom/report.css")
        }

        println("📊 Reporte JaCoCo generado en: ${jacocoHtmlDir}/index.html")
    }
}
tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)

    reports {
        html.required.set(true)   // ver en navegador
        xml.required.set(true)    // útil para CI/CD
        csv.required.set(false)
    }

    // Solo incluir los paquetes repository, service y controller
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
            }
        })
    )

    // Asignación correcta en Kotlin DSL
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(files(layout.buildDirectory.file("jacoco/test.exec")))
}

