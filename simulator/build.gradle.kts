plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":engine"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("pl.mafia.simulator.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}
