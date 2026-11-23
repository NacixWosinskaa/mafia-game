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

tasks.test {
    useJUnitPlatform()
}
