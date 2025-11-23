package com.liangyonglin.lingmaplus.scanner

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Panel for scanning and displaying Java classes with many methods
 */
class JavaClassScannerPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private class ClassInfo(
        val className: String,
        val filePath: String,
        val methodCount: Int,
        val psiClass: PsiClass,
        val virtualFile: VirtualFile?,
        var navigateCount: Int = 0,
        val fileSource: FileSource = FileSource.PROJECT
    )
    
    private val logger = thisLogger()
    private val scanButton = JButton("开始扫描")
    private val progressBar = JProgressBar()
    private val statusLabel = JLabel("准备就绪")
    private val tableModel = DefaultTableModel(arrayOf("跳转次数", "类名", "方法数", "文件路径"), 0)
    private val table = JBTable(tableModel)
    private val pageInfoLabel = JLabel("")
    private val prevButton = JButton("上一页")
    private val nextButton = JButton("下一页")
    
    private var allClasses: MutableList<ClassInfo> = mutableListOf()
    private var currentPage = 0
    private val pageSize = 20
    
    init {
        setupUI()
        loadCache()
    }
    
    private fun setupUI() {
        // 顶部控制面板
        val topPanel = JPanel(BorderLayout())
        val buttonPanel = JPanel()
        buttonPanel.add(scanButton)
        topPanel.add(buttonPanel, BorderLayout.WEST)
        topPanel.add(progressBar, BorderLayout.CENTER)
        topPanel.add(statusLabel, BorderLayout.EAST)
        
        progressBar.isStringPainted = true
        progressBar.isVisible = false
        
        // 表格设置
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.setShowGrid(true)
        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        table.setColumnSelectionAllowed(false)
        table.setRowSelectionAllowed(true)
        
        // 设置列宽
        val columnModel = table.columnModel
        columnModel.getColumn(0).preferredWidth = 80    // 跳转次数
        columnModel.getColumn(1).preferredWidth = 300    // 类名
        columnModel.getColumn(2).preferredWidth = 80     // 方法数
        columnModel.getColumn(3).preferredWidth = 500    // 文件路径
        
        // 添加右键菜单
        val popupMenu = JPopupMenu()
        val navigateMenuItem = JMenuItem("导航到类")
        navigateMenuItem.addActionListener { navigateToClass() }
        popupMenu.add(navigateMenuItem)
        
        table.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                showPopupMenu(e)
            }
            
            override fun mouseReleased(e: MouseEvent) {
                showPopupMenu(e)
            }
            
            private fun showPopupMenu(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    val row = table.rowAtPoint(e.point)
                    if (row >= 0 && !table.isRowSelected(row)) {
                        table.setRowSelectionInterval(row, row)
                    }
                    if (table.selectedRow >= 0) {
                        navigateMenuItem.isEnabled = true
                        popupMenu.show(table, e.x, e.y)
                    } else {
                        navigateMenuItem.isEnabled = false
                    }
                }
            }
        })
        
        // 分页控制面板
        val pagePanel = JPanel()
        pagePanel.add(prevButton)
        pagePanel.add(pageInfoLabel)
        pagePanel.add(nextButton)
        
        prevButton.isEnabled = false
        nextButton.isEnabled = false
        
        // 布局
        add(topPanel, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        add(pagePanel, BorderLayout.SOUTH)
        
        // 事件处理
        scanButton.addActionListener { startScan() }
        prevButton.addActionListener { goToPreviousPage() }
        nextButton.addActionListener { goToNextPage() }
    }
    
    fun startScan() {
        // 清除缓存
        val projectBasePath = project.basePath
        ClassInfoCache.clearCache(projectBasePath)
        allClasses.clear()
        currentPage = 0
        
        scanButton.isEnabled = false
        progressBar.isVisible = true
        progressBar.isIndeterminate = true
        statusLabel.text = "正在扫描..."
        
        ReadAction.nonBlocking {
            scanJavaFiles()
        }.submit(AppExecutorUtil.getAppExecutorService())
    }
    
    private fun scanJavaFiles() {
        try {
            val allJavaFiles = mutableListOf<PsiJavaFile>()
            val fileIndex = ProjectRootManager.getInstance(project).fileIndex
            val projectBasePath = project.basePath
            
            // 扫描项目文件和库文件
            val scope = GlobalSearchScope.allScope(project)
            val javaFiles = FilenameIndex.getAllFilesByExt(project, "java", scope)
            
            logger.info("找到 ${javaFiles.size} 个Java文件")
            
            // 转换为PsiJavaFile并过滤
            for (file in javaFiles) {
                try {
                    val psiFile = PsiManager.getInstance(project).findFile(file) as? PsiJavaFile
                    if (psiFile != null) {
                        allJavaFiles.add(psiFile)
                    }
                } catch (e: Exception) {
                    logger.warn("无法处理文件: ${file.path}", e)
                }
            }
            
            logger.info("成功解析 ${allJavaFiles.size} 个Java文件")
            
            // 收集所有类信息，并分类文件来源
            // 随机打乱文件顺序，让扫描看起来更真实
            Collections.shuffle(allJavaFiles)
            
            val classesWithManyMethods = mutableListOf<ClassInfo>()
            val maxScanCount = 600  // 扫描到600个就停止
            
            for (psiFile in allJavaFiles) {
                // 如果已经收集到600个，就停止扫描
                if (classesWithManyMethods.size >= maxScanCount) {
                    break
                }
                
                try {
                    val virtualFile = psiFile.virtualFile ?: continue
                    
                    // 判断文件来源
                    val fileSource = when {
                        fileIndex.isInSourceContent(virtualFile) -> FileSource.PROJECT
                        fileIndex.isInLibraryClasses(virtualFile) -> {
                            // 判断是否是JDK
                            val path = virtualFile.path.lowercase()
                            if (path.contains("jdk") || path.contains("java\\lang") || 
                                path.contains("java/lang") || path.contains("rt.jar")) {
                                FileSource.JDK
                            } else {
                                FileSource.LIBRARY
                            }
                        }
                        else -> FileSource.LIBRARY
                    }
                    
                    for (psiClass in psiFile.classes) {
                        // 如果已经收集到600个，就停止扫描
                        if (classesWithManyMethods.size >= maxScanCount) {
                            break
                        }
                        
                        // 忽略接口，只考虑class
                        if (psiClass.isInterface) {
                            continue
                        }
                        
                        val methods = psiClass.methods
                        if (methods.size > 20) {
                            classesWithManyMethods.add(
                                ClassInfo(
                                    className = psiClass.qualifiedName ?: psiClass.name ?: "Unknown",
                                    filePath = virtualFile.path,
                                    methodCount = methods.size,
                                    psiClass = psiClass,
                                    virtualFile = virtualFile,
                                    fileSource = fileSource
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("处理类时出错: ${psiFile.name}", e)
                }
            }
            
            logger.info("扫描完成，找到 ${classesWithManyMethods.size} 个包含超过20个方法的类（已忽略接口）")
            
            // 随机打乱顺序，让显示看起来更真实
            Collections.shuffle(classesWithManyMethods)
            
            // 从600个中随机选择500个
            val selectedClasses = classesWithManyMethods.take(500)
            
            allClasses = selectedClasses.toMutableList()
            
            // 保存缓存
            saveCache()
            
            // 更新UI
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                updateTable()
                scanButton.isEnabled = true
                progressBar.isVisible = false
                val projectCount = allClasses.count { it.fileSource == FileSource.PROJECT }
                val libraryCount = allClasses.count { it.fileSource == FileSource.LIBRARY }
                val jdkCount = allClasses.count { it.fileSource == FileSource.JDK }
                statusLabel.text = "扫描完成: 共${allClasses.size}个类 (项目:$projectCount 库:$libraryCount JDK:$jdkCount)"
            }
            
        } catch (e: Exception) {
            logger.error("扫描过程中出错", e)
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(
                    project,
                    "扫描失败: ${e.message}",
                    "扫描错误"
                )
                scanButton.isEnabled = true
                progressBar.isVisible = false
                statusLabel.text = "扫描失败"
            }
        }
    }
    
    private fun updateTable() {
        tableModel.rowCount = 0
        
        val startIndex = currentPage * pageSize
        val endIndex = minOf(startIndex + pageSize, allClasses.size)
        
        if (startIndex < allClasses.size) {
            val pageClasses = allClasses.subList(startIndex, endIndex)
            for (classInfo in pageClasses) {
                tableModel.addRow(arrayOf(
                    classInfo.navigateCount,
                    classInfo.className,
                    classInfo.methodCount,
                    classInfo.filePath
                ))
            }
        }
        
        // 更新分页信息
        val totalPages = if (allClasses.isEmpty()) 0 else (allClasses.size + pageSize - 1) / pageSize
        pageInfoLabel.text = "第 ${currentPage + 1} 页 / 共 $totalPages 页 (共 ${allClasses.size} 个类)"
        
        // 更新按钮状态
        prevButton.isEnabled = currentPage > 0
        nextButton.isEnabled = currentPage < totalPages - 1
    }
    
    private fun goToPreviousPage() {
        if (currentPage > 0) {
            currentPage--
            updateTable()
        }
    }
    
    private fun goToNextPage() {
        val totalPages = if (allClasses.isEmpty()) 0 else (allClasses.size + pageSize - 1) / pageSize
        if (currentPage < totalPages - 1) {
            currentPage++
            updateTable()
        }
    }
    
    private fun navigateToClass() {
        val selectedRow = table.selectedRow
        if (selectedRow >= 0) {
            val actualIndex = currentPage * pageSize + selectedRow
            if (actualIndex < allClasses.size) {
                val classInfo = allClasses[actualIndex]
                try {
                    // 增加跳转次数
                    classInfo.navigateCount++
                    saveCache()
                    updateTable()
                    
                    // 直接使用保存的PsiClass进行导航
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        try {
                            // 先打开文件
                            if (classInfo.virtualFile != null) {
                                val fileEditorManager = FileEditorManager.getInstance(project)
                                fileEditorManager.openFile(classInfo.virtualFile, true)
                            }
                            
                            // 导航到类定义
                            if (classInfo.psiClass.isValid) {
                                classInfo.psiClass.navigate(true)
                            } else {
                                // 如果PsiClass无效，尝试重新查找
                                val psiFile = classInfo.virtualFile?.let {
                                    PsiManager.getInstance(project)
                                        .findFile(it) as? PsiJavaFile
                                }
                                
                                if (psiFile != null) {
                                    val targetClass = psiFile.classes.find { 
                                        (it.qualifiedName ?: it.name) == classInfo.className 
                                    }
                                    
                                    if (targetClass != null) {
                                        targetClass.navigate(true)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("导航到类失败: ${classInfo.className}", e)
                            Messages.showErrorDialog(
                                project,
                                "无法导航到类: ${classInfo.className}\n${e.message}",
                                "导航错误"
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("导航到类失败: ${classInfo.className}", e)
                    Messages.showErrorDialog(
                        project,
                        "无法导航到类: ${classInfo.className}\n${e.message}",
                        "导航错误"
                    )
                }
            }
        }
    }
    
    /**
     * 加载缓存
     */
    private fun loadCache() {
        try {
            val projectBasePath = project.basePath
            val cachedClasses = ClassInfoCache.loadCache(projectBasePath)
            if (cachedClasses.isEmpty()) {
                statusLabel.text = "准备就绪（无缓存数据）"
                return
            }
            
            // 将缓存数据转换为ClassInfo
            val loadedClasses = mutableListOf<ClassInfo>()
            for (cached in cachedClasses) {
                // 尝试查找对应的PsiClass
                val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                    .findFileByPath(cached.filePath)
                
                val psiClass: PsiClass? = virtualFile?.let { vf ->
                    try {
                        val psiFile = PsiManager.getInstance(project).findFile(vf) as? PsiJavaFile
                        psiFile?.classes?.find { 
                            (it.qualifiedName ?: it.name) == cached.className 
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                
                // 只加载能找到PsiClass的类（文件仍然存在且可访问）
                if (psiClass != null && virtualFile != null) {
                    val fileSource = try {
                        FileSource.valueOf(cached.fileSource)
                    } catch (e: Exception) {
                        FileSource.PROJECT  // 兼容旧缓存
                    }
                    
                    loadedClasses.add(
                        ClassInfo(
                            className = cached.className,
                            filePath = cached.filePath,
                            methodCount = cached.methodCount,
                            psiClass = psiClass,
                            virtualFile = virtualFile,
                            navigateCount = cached.navigateCount,
                            fileSource = fileSource
                        )
                    )
                }
            }
            
            // 保持随机顺序，不排序
            allClasses = loadedClasses.toMutableList()
            if (allClasses.isNotEmpty()) {
                updateTable()
                val projectCount = allClasses.count { it.fileSource == FileSource.PROJECT }
                val libraryCount = allClasses.count { it.fileSource == FileSource.LIBRARY }
                val jdkCount = allClasses.count { it.fileSource == FileSource.JDK }
                statusLabel.text = "已加载缓存: 项目($projectCount) 库($libraryCount) JDK($jdkCount)"
            } else {
                statusLabel.text = "准备就绪（缓存数据已失效）"
            }
        } catch (e: Exception) {
            logger.error("加载缓存失败", e)
            statusLabel.text = "加载缓存失败"
        }
    }
    
    /**
     * 保存缓存
     */
    private fun saveCache() {
        try {
            val projectBasePath = project.basePath
            val cachedClasses = allClasses.map { classInfo ->
                CachedClassInfo(
                    className = classInfo.className,
                    filePath = classInfo.filePath,
                    methodCount = classInfo.methodCount,
                    navigateCount = classInfo.navigateCount,
                    fileSource = classInfo.fileSource.name
                )
            }
            ClassInfoCache.saveCache(cachedClasses, projectBasePath)
        } catch (e: Exception) {
            logger.error("保存缓存失败", e)
        }
    }
}

