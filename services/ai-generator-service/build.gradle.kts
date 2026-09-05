plugins {
    alias(libs.plugins.spring.boot)
}

description = "Serviço de geração de questões/provas via IA da plataforma AI Exam"

dependencies {
    implementation(enforcedPlatform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.ai.bom))

    implementation(project(":common-events"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")
    implementation("org.springframework.ai:spring-ai-ollama-spring-boot-starter")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

tasks.named("jar") {
    enabled = false
}
