package com.github.e6l1ps.cxboxpluginatlassian.services

import com.github.e6l1ps.cxboxpluginatlassian.MyBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class PluginStateService(project: Project) {

    var selectedModule: String? = null
    var foundJsonFiles: List<VirtualFile> = emptyList()

    init {
        thisLogger().info(MyBundle.message("projectService", project.name))
    }
}
