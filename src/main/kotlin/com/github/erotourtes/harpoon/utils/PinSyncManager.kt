package com.github.erotourtes.harpoon.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

class PinSyncManager(private val project: Project) {
    private val log = Logger.getInstance(PinSyncManager::class.java)
    private var menuFilePath: String? = null

    fun setMenuFilePath(path: String) {
        menuFilePath = path
    }

    fun syncPins(harpoonPaths: List<String>) {
        val feManager = FileEditorManagerEx.getInstanceEx(project)
        val windows = feManager.windows
        if (windows.isEmpty()) return

        val harpoonFiles = resolveHarpoonFiles(harpoonPaths)

        for (window in windows) {
            try {
                syncWindow(window, harpoonFiles, feManager)
            } catch (e: Exception) {
                log.error("Failed to sync pins for window", e)
            }
        }
    }

    private fun resolveHarpoonFiles(paths: List<String>): List<VirtualFile> {
        val fs = LocalFileSystem.getInstance()
        return paths
            .filter { it.isNotEmpty() && it != menuFilePath }
            .mapNotNull { fs.findFileByPath(it) }
    }

    private fun syncWindow(
        window: EditorWindow,
        harpoonFiles: List<VirtualFile>,
        feManager: FileEditorManagerEx
    ) {
        val harpoonFileSet = harpoonFiles.toSet()
        val openFiles = window.fileList.toList()

        // Unpin files not in Harpooner's list
        for (file in openFiles) {
            if (window.isFilePinned(file) && file !in harpoonFileSet) {
                window.setFilePinned(file, false)
            }
        }

        // Open files that aren't yet open (without focus)
        for (file in harpoonFiles) {
            if (!window.isFileOpen(file)) {
                feManager.openFile(file, false)
            }
        }

        // Pin all Harpooner files
        for (file in harpoonFiles) {
            if (!window.isFilePinned(file)) {
                window.setFilePinned(file, true)
            }
        }

        // Enforce tab ordering
        reorderPinnedTabs(window, harpoonFiles, feManager)
    }

    private fun reorderPinnedTabs(
        window: EditorWindow,
        desiredOrder: List<VirtualFile>,
        feManager: FileEditorManagerEx
    ) {
        val currentPinned = window.fileList.filter { window.isFilePinned(it) }
        if (isPinnedOrderCorrect(currentPinned, desiredOrder)) return

        val docManager = FileDocumentManager.getInstance()
        val hasUnsaved = currentPinned.any { file ->
            val doc = docManager.getDocument(file)
            doc != null && docManager.isDocumentUnsaved(doc)
        }
        if (hasUnsaved) {
            docManager.saveAllDocuments()
        }

        val selectedFile = window.selectedFile

        // Close all pinned tabs (reverse to avoid index shifts)
        for (file in currentPinned.reversed()) {
            window.setFilePinned(file, false)
            feManager.closeFile(file, window)
        }

        // Reopen in Harpooner order and pin
        for (file in desiredOrder) {
            feManager.openFile(file, false)
            window.setFilePinned(file, true)
        }

        // Restore focus
        if (selectedFile != null && window.isFileOpen(selectedFile)) {
            feManager.openFile(selectedFile, true)
        }
    }

    private fun isPinnedOrderCorrect(
        currentPinned: List<VirtualFile>,
        desiredOrder: List<VirtualFile>
    ): Boolean {
        val currentPaths = currentPinned.map { it.path }
        val desiredPaths = desiredOrder.map { it.path }
        return currentPaths == desiredPaths
    }
}
