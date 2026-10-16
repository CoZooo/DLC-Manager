val managerStonecutterFile = file("../../stonecutter.gradle.kts")
val managerActiveNode = Regex("stonecutter\\s+active\\s+\"([^\"]+)\"")
    .find(managerStonecutterFile.readText())
    ?.groupValues
    ?.get(1)
    ?: error("The main project does not declare an active StoneCutter node")

stonecutter active managerActiveNode
