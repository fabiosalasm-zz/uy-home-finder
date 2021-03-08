import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.useIR = true

plugins {
    id("org.springframework.boot") version "2.4.3"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    id("nu.studer.jooq") version "5.2.1"
    kotlin("jvm") version "1.4.30"
    kotlin("plugin.spring") version "1.4.30"
}

group = "pe.fabiosalasm"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_1_8

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

extra["springCloudVersion"]  = "2020.0.1"
extra["testcontainersVersion"] = "1.15.1"
extra["kotestVersion"] = "4.3.2"
extra["jsoupVersion"] = "1.13.1"
extra["datasourceDecoratorVersion"] = "1.6.3"
extra["kotlinLoggingVersion"] = "1.12.0"

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    runtimeOnly("org.postgresql:postgresql")
    jooqGenerator("org.postgresql:postgresql:${dependencyManagement.importedProperties["postgresql.version"]}")
    implementation("org.springframework.boot:spring-boot-starter-jooq")

    implementation("com.github.gavlyukovskiy:datasource-proxy-spring-boot-starter:${property("datasourceDecoratorVersion")}")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.zalando:jackson-datatype-money:1.2.1")
    implementation("org.jsoup:jsoup:${property("jsoupVersion")}")
    implementation("io.github.microutils:kotlin-logging:${property("kotlinLoggingVersion")}")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(module = "mockito-core")
    }

    // Kotest (only assertions) instead of Hamcrest/AssertJ
    testImplementation("io.kotest:kotest-assertions-core:${property("kotestVersion")}")
    testImplementation("org.testcontainers:junit-jupiter")
}

jooq {
    version.set(dependencyManagement.importedProperties["jooq.version"])
    edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)

    configurations {
        create("main") {
            jooqConfiguration.apply {
                generateSchemaSourceOnCompilation.set(false)
                logging = org.jooq.meta.jaxb.Logging.WARN
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = "jdbc:postgresql://localhost:5432/uy_home_finder"
                    user = "postgres"
                    password = "changeme"
                }
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                    }
                    target.apply {
                        packageName = "pe.fabiosalasm.uyhomefinder.jooq"
                    }
                }
            }
        }
    }
}

sourceSets.main {
    java.srcDirs("src/main/kotlin", "build/generated-src/jooq/main")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xinline-classes")
        jvmTarget = "1.8"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}