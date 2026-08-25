rootProject.name = "ai-exam-platform"

include(
    "common-events",
    "api-gateway",
    "auth-service",
    "exam-service",
    "ai-generator-service",
)

project(":common-events").projectDir = file("shared/common-events")
project(":api-gateway").projectDir = file("services/api-gateway")
project(":auth-service").projectDir = file("services/auth-service")
project(":exam-service").projectDir = file("services/exam-service")
project(":ai-generator-service").projectDir = file("services/ai-generator-service")
