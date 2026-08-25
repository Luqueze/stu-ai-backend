plugins {
    alias(libs.plugins.spring.boot)
}

description = "Serviço de geração de questões/provas via IA da plataforma AI Exam"

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.ai.bom))

    implementation(project(":common-events"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
