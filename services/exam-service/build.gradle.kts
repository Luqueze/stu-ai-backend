plugins {
    alias(libs.plugins.spring.boot)
}

description = "Serviço de gestão de provas/exames da plataforma AI Exam"

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    implementation(project(":common-events"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
