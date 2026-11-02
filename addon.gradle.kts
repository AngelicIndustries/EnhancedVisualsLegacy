import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

tasks.withType<Test>().configureEach { useJUnit() }
extensions.configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
        groupId = "industries.angelic.enhancedvisuals"
        pom {
            name.set("Enhanced Visuals Legacy")
            description.set("Configurable damage and environmental visual effects for Minecraft 1.7.10.")
            url.set("https://github.com/AngelicIndustries/EnhancedVisualsLegacy")
            licenses { license { name.set("GPLv3"); url.set("https://www.gnu.org/licenses/gpl-3.0.html") } }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("runServer")) {
        doFirst("stripClientOnlyMods") {
            val clientOnlyMods = listOf(
                "angelica", "ModularUI2", "catalogue", "AnExtraTouch", "BlurLegacy",
                "VintageResourcify", "vrap", "euphoria-patches", "Sound-Physics", "MAtmos"
            )
            classpath = classpath.filter { file ->
                clientOnlyMods.none { file.name.contains(it, ignoreCase = true) }
            }
        }
    }
}
