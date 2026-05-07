plugins {
    id("io.micronaut.application") version "5.0.0-M1"
    id("com.gradleup.shadow") version "9.4.1"
    id("io.micronaut.aot") version "5.0.0-M1"
    id("me.champeau.jmh") version "0.7.2"
}

version = "0.1"
group = "com.jnta"



repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    compileOnly("io.micronaut:micronaut-http-client")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation("io.micronaut:micronaut-http-client")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}



application {
    mainClass = "com.jnta.Application"
}

java {
    sourceCompatibility = JavaVersion.toVersion("25")
    targetCompatibility = JavaVersion.toVersion("25")
}




graalvmNative.toolchainDetection = false
graalvmNative {
    binaries {
        all {

            buildArgs.add("--add-modules")
            buildArgs.add("jdk.incubator.vector")
            buildArgs.add("-H:+VectorAPISupport")
        }
    }
}




micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.jnta.*")
    }
    aot {
        // Please review carefully the optimizations enabled below
        // Check https://micronaut-projects.github.io/micronaut-aot/latest/guide/ for more details
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }

}

tasks.named<io.micronaut.gradle.docker.MicronautDockerfile>("dockerfile") {

    baseImage = "eclipse-temurin:25-jre"
}







tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.incubator.vector"))
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-modules", "jdk.incubator.vector", "-XX:MaxDirectMemorySize=1g")
}

// https://docs.gradle.org/current/userguide/upgrading_major_version_9.html#test_task_fails_when_no_tests_are_discovered
tasks.withType<AbstractTestTask>().configureEach {
    failOnNoDiscoveredTests = false
}


jmh {
    jvmArgs.add("--add-modules")
    jvmArgs.add("jdk.incubator.vector")
    benchmarkMode.add("avgt")
    timeUnit.set("ns")
    warmupIterations.set(1)
    iterations.set(1)
    fork.set(1)
}

tasks.register<JavaExec>("preprocess") {
    mainClass.set("com.jnta.vp.Preprocessor")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("--add-modules", "jdk.incubator.vector", "-Xmx1g")
    args = project.findProperty("args")?.toString()?.split(" ") ?: emptyList()
}


