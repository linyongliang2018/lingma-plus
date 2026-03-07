package com.liangyonglin.lingmaplus.scanner

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.AppExecutorUtil
import com.liangyonglin.lingmaplus.lingma.LingmaConfigDialog
import com.liangyonglin.lingmaplus.lingma.LingmaSettings
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    private val ignoredSuffixes =
        setOf("vo", "dto", "do", "po", "entity", "request", "response", "command", "model")
    private val scanButton = JButton("开始扫描")
    private val scanGitButton = JButton("扫描最近git修改")
    private val scanStagedButton = JButton("扫描未提交修改")
    private val optimizeButton = JButton("开始优化")
    private val commentButton = JButton("开始注释")
    private val configButton = JButton("配置")
    private val settingsButton = JButton("设置")
    private val progressBar = JProgressBar()
    private val statusLabel = JLabel("准备就绪")
    private val tableModel = DefaultTableModel(arrayOf("跳转次数", "类名", "方法数", "文件路径"), 0)
    private val table = JBTable(tableModel)
    private val pageInfoLabel = JLabel("")
    private val prevButton = JButton("上一页")
    private val nextButton = JButton("下一页")
    
    private var allClasses: MutableList<ClassInfo> = mutableListOf()
    private var currentPage = 0
    
    private fun getPageSize(): Int = LingmaSettings.getInstance().getPageSize()
    
    /** 按类名排序后再分页展示 */
    private fun sortClassesByClassName() {
        allClasses.sortBy { it.className }
    }
    
    init {
        setupUI()
        loadCache()
    }
    
    private fun setupUI() {
        // 顶部控制面板
        val topPanel = JPanel(BorderLayout())
        val buttonPanel = JPanel()
        buttonPanel.add(scanButton)
        buttonPanel.add(scanGitButton)
        buttonPanel.add(scanStagedButton)
        buttonPanel.add(optimizeButton)
        buttonPanel.add(commentButton)
        buttonPanel.add(configButton)
        buttonPanel.add(settingsButton)
        topPanel.add(buttonPanel, BorderLayout.WEST)
        topPanel.add(progressBar, BorderLayout.CENTER)
        topPanel.add(statusLabel, BorderLayout.EAST)
        
        progressBar.isStringPainted = true
        progressBar.isVisible = false
        
        // 表格设置（支持多选）
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
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
        val optimizeMenuItem = JMenuItem("对选中项开始优化")
        val commentMenuItem = JMenuItem("对选中项生成注释")
        val explainMenuItem = JMenuItem("对选中项进行解释")
        navigateMenuItem.addActionListener { navigateToClass() }
        optimizeMenuItem.addActionListener { startOptimize() }
        commentMenuItem.addActionListener { startCommentGeneration() }
        explainMenuItem.addActionListener { explainSelectedClasses() }
        popupMenu.add(navigateMenuItem)
        popupMenu.add(optimizeMenuItem)
        popupMenu.add(commentMenuItem)
        popupMenu.add(explainMenuItem)
        
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
                    val hasSelection = table.selectedRowCount > 0
                    navigateMenuItem.isEnabled = hasSelection
                    optimizeMenuItem.isEnabled = hasSelection
                    commentMenuItem.isEnabled = hasSelection
                    explainMenuItem.isEnabled = hasSelection
                    popupMenu.show(table, e.x, e.y)
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
        scanGitButton.addActionListener { startScanGitModified() }
        scanStagedButton.addActionListener { startScanGitStaged() }
        optimizeButton.addActionListener { startOptimize() }
        commentButton.addActionListener { startCommentGeneration() }
        configButton.addActionListener { openConfig() }
        settingsButton.addActionListener { openSettings() }
        prevButton.addActionListener { goToPreviousPage() }
        nextButton.addActionListener { goToNextPage() }
    }
    
    private fun openConfig() {
        LingmaConfigDialog(project).showAndGet()
    }
    
    private fun openSettings() {
        // 打开 IDE 设置对话框
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "")
    }
    
    fun startScan() {
        // 清除缓存
        val projectBasePath = project.basePath
        ClassInfoCache.clearCache(projectBasePath)
        allClasses.clear()
        currentPage = 0
        
        scanButton.isEnabled = false
        scanGitButton.isEnabled = false
        progressBar.isVisible = true
        progressBar.isIndeterminate = true
        statusLabel.text = "正在扫描..."
        
        ReadAction.nonBlocking {
            scanJavaFiles()
        }.submit(AppExecutorUtil.getAppExecutorService())
    }
    
    fun startScanGitModified() {
        val projectBasePath = project.basePath
        if (projectBasePath.isNullOrBlank()) {
            Messages.showErrorDialog(project, "无法获取项目路径", "扫描失败")
            return
        }
        
        val settings = LingmaSettings.getInstance()
        val daysBack = settings.getGitDaysBack()
        val author = settings.getGitAuthor()
        
        scanButton.isEnabled = false
        scanGitButton.isEnabled = false
        scanStagedButton.isEnabled = false
        progressBar.isVisible = true
        progressBar.isIndeterminate = true
        statusLabel.text = "正在扫描 git 修改..."
        
        ReadAction.nonBlocking {
            scanGitModifiedFiles(projectBasePath, daysBack, author)
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

                        // 忽略以 VO/DTO 等领域后缀命名的简单类
                        val simpleName = psiClass.name ?: continue
                        if (shouldIgnoreBySuffix(simpleName)) {
                            continue
                        }
                        
                        val fieldNames = psiClass.fields.mapNotNull { it.name?.let { name -> normalizeFieldName(name) } }.toSet()
                        val methods = psiClass.methods
                        val effectiveMethods = methods.filterNot { isAccessorMethod(it, fieldNames) }
                        if (effectiveMethods.size > 20) {
                            classesWithManyMethods.add(
                                ClassInfo(
                                    className = psiClass.qualifiedName ?: psiClass.name ?: "Unknown",
                                    filePath = virtualFile.path,
                                    methodCount = effectiveMethods.size,
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
            sortClassesByClassName()
            
            // 保存缓存
            saveCache()
            
            // 更新UI
            ApplicationManager.getApplication().invokeLater {
                updateTable()
                scanButton.isEnabled = true
                scanGitButton.isEnabled = true
                progressBar.isVisible = false
                val projectCount = allClasses.count { it.fileSource == FileSource.PROJECT }
                val libraryCount = allClasses.count { it.fileSource == FileSource.LIBRARY }
                val jdkCount = allClasses.count { it.fileSource == FileSource.JDK }
                statusLabel.text = "扫描完成: 共${allClasses.size}个类 (项目:$projectCount 库:$libraryCount JDK:$jdkCount)"
            }
            
        } catch (e: Exception) {
            logger.error("扫描过程中出错", e)
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(
                    project,
                    "扫描失败: ${e.message}",
                    "扫描错误"
                )
                scanButton.isEnabled = true
                scanGitButton.isEnabled = true
                scanStagedButton.isEnabled = true
                progressBar.isVisible = false
                statusLabel.text = "扫描失败"
            }
        }
    }
    
    private fun scanGitStagedFiles(projectBasePath: String) {
        try {
            val javaPaths = GitModifiedFilesService.getUncommittedJavaFiles(projectBasePath)
            logger.info("Git 未提交修改扫描到 ${javaPaths.size} 个 Java 文件")
            
            if (javaPaths.isEmpty()) {
                ApplicationManager.getApplication().invokeLater {
                    updateTable()
                    scanButton.isEnabled = true
                    scanGitButton.isEnabled = true
                    scanStagedButton.isEnabled = true
                    progressBar.isVisible = false
                    statusLabel.text = "未找到未提交的 Java 修改文件"
                }
                return
            }
            
            val fileIndex = ProjectRootManager.getInstance(project).fileIndex
            val psiManager = PsiManager.getInstance(project)
            val localFs = LocalFileSystem.getInstance()
            val classesFromGit = mutableListOf<ClassInfo>()
            
            for (absPath in javaPaths) {
                try {
                    val virtualFile = localFs.findFileByPath(absPath) ?: continue
                    val psiFile = psiManager.findFile(virtualFile) as? PsiJavaFile ?: continue
                    val fileSource = when {
                        fileIndex.isInSourceContent(virtualFile) -> FileSource.PROJECT
                        fileIndex.isInLibraryClasses(virtualFile) -> FileSource.LIBRARY
                        else -> FileSource.PROJECT
                    }
                    for (psiClass in psiFile.classes) {
                        if (psiClass.isInterface) continue
                        val methods = psiClass.methods
                        val fieldNames = psiClass.fields.mapNotNull { it.name?.let { n -> normalizeFieldName(n) } }.toSet()
                        val effectiveMethods = methods.filterNot { isAccessorMethod(it, fieldNames) }
                        classesFromGit.add(
                            ClassInfo(
                                className = psiClass.qualifiedName ?: psiClass.name ?: "Unknown",
                                filePath = virtualFile.path,
                                methodCount = effectiveMethods.size,
                                psiClass = psiClass,
                                virtualFile = virtualFile,
                                fileSource = fileSource
                            )
                        )
                    }
                } catch (e: Exception) {
                    logger.warn("处理未提交文件失败: $absPath", e)
                }
            }
            
            allClasses = classesFromGit.toMutableList()
            sortClassesByClassName()
            currentPage = 0
            
            ApplicationManager.getApplication().invokeLater {
                updateTable()
                scanButton.isEnabled = true
                scanGitButton.isEnabled = true
                scanStagedButton.isEnabled = true
                progressBar.isVisible = false
                statusLabel.text = "未提交修改扫描完成: 共 ${allClasses.size} 个类"
            }
        } catch (e: Exception) {
            logger.error("Git 未提交修改扫描失败", e)
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(project, "Git 未提交修改扫描失败: ${e.message}", "扫描错误")
                scanButton.isEnabled = true
                scanGitButton.isEnabled = true
                scanStagedButton.isEnabled = true
                progressBar.isVisible = false
                statusLabel.text = "Git 未提交修改扫描失败"
            }
        }
    }
    
    private fun scanGitModifiedFiles(projectBasePath: String, daysBack: Int, author: String) {
        try {
            val javaPaths = GitModifiedFilesService.getModifiedJavaFiles(projectBasePath, daysBack, author)
            logger.info("Git 扫描到 ${javaPaths.size} 个 Java 文件")
            
            if (javaPaths.isEmpty()) {
                ApplicationManager.getApplication().invokeLater {
                    updateTable()
                    scanButton.isEnabled = true
                    scanGitButton.isEnabled = true
                    scanStagedButton.isEnabled = true
                    progressBar.isVisible = false
                    statusLabel.text = "未找到符合条件的 Java 文件"
                }
                return
            }
            
            val fileIndex = ProjectRootManager.getInstance(project).fileIndex
            val psiManager = PsiManager.getInstance(project)
            val localFs = LocalFileSystem.getInstance()
            val classesFromGit = mutableListOf<ClassInfo>()
            
            for (absPath in javaPaths) {
                try {
                    val virtualFile = localFs.findFileByPath(absPath) ?: continue
                    val psiFile = psiManager.findFile(virtualFile) as? PsiJavaFile ?: continue
                    val fileSource = when {
                        fileIndex.isInSourceContent(virtualFile) -> FileSource.PROJECT
                        fileIndex.isInLibraryClasses(virtualFile) -> FileSource.LIBRARY
                        else -> FileSource.PROJECT
                    }
                    for (psiClass in psiFile.classes) {
                        if (psiClass.isInterface) continue
                        val methods = psiClass.methods
                        val fieldNames = psiClass.fields.mapNotNull { it.name?.let { n -> normalizeFieldName(n) } }.toSet()
                        val effectiveMethods = methods.filterNot { isAccessorMethod(it, fieldNames) }
                        classesFromGit.add(
                            ClassInfo(
                                className = psiClass.qualifiedName ?: psiClass.name ?: "Unknown",
                                filePath = virtualFile.path,
                                methodCount = effectiveMethods.size,
                                psiClass = psiClass,
                                virtualFile = virtualFile,
                                fileSource = fileSource
                            )
                        )
                    }
                } catch (e: Exception) {
                    logger.warn("处理 git 文件失败: $absPath", e)
                }
            }
            
            allClasses = classesFromGit.toMutableList()
            sortClassesByClassName()
            currentPage = 0
            
            ApplicationManager.getApplication().invokeLater {
                updateTable()
                scanButton.isEnabled = true
                scanGitButton.isEnabled = true
                scanStagedButton.isEnabled = true
                progressBar.isVisible = false
                statusLabel.text = "Git 扫描完成: 共 ${allClasses.size} 个类（最近 ${daysBack} 天）"
            }
        } catch (e: Exception) {
            logger.error("Git 扫描失败", e)
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(project, "Git 扫描失败: ${e.message}", "扫描错误")
                scanButton.isEnabled = true
                scanGitButton.isEnabled = true
                scanStagedButton.isEnabled = true
                progressBar.isVisible = false
                statusLabel.text = "Git 扫描失败"
            }
        }
    }
    
    fun startScanGitStaged() {
        val projectBasePath = project.basePath
        if (projectBasePath.isNullOrBlank()) {
            Messages.showErrorDialog(project, "无法获取项目路径", "扫描失败")
            return
        }
        
        scanButton.isEnabled = false
        scanGitButton.isEnabled = false
        scanStagedButton.isEnabled = false
        progressBar.isVisible = true
        progressBar.isIndeterminate = true
        statusLabel.text = "正在扫描未提交修改..."
        
        ReadAction.nonBlocking {
            scanGitStagedFiles(projectBasePath)
        }.submit(AppExecutorUtil.getAppExecutorService())
    }
    
    private fun updateTable() {
        tableModel.rowCount = 0
        val pageSize = getPageSize()
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
        val pageSize = getPageSize()
        val totalPages = if (allClasses.isEmpty()) 0 else (allClasses.size + pageSize - 1) / pageSize
        if (currentPage < totalPages - 1) {
            currentPage++
            updateTable()
        }
    }
    
    private fun navigateToClass() {
        val selectedRow = table.selectedRow
        if (selectedRow >= 0) {
            val actualIndex = currentPage * getPageSize() + selectedRow
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
    
    private fun startOptimize() {
        startBatchLingmaAction(
            actionId = "TriggerCosyOptimizeCodeGenerationAction",
            actionDesc = "优化"
        )
    }
    
    private fun startCommentGeneration() {
        startBatchLingmaAction(
            actionId = "TriggerCosyCodeGenerateCommentGenerationAction",
            actionDesc = "生成注释"
        )
    }
    
    private fun explainSelectedClasses() {
        startBatchLingmaAction(
            actionId = "TriggerCosyExplainCodeGenerationAction",
            actionDesc = "解释"
        )
    }
    
    private fun startBatchLingmaAction(actionId: String, actionDesc: String) {
        val selectedViewRows = table.selectedRows
        if (selectedViewRows.isEmpty()) {
            Messages.showInfoMessage(project, "请先选择要处理的类", "LingmaHelper")
            return
        }
        
        // 按表格顺序逐个类处理：先排序行号，再映射为 ClassInfo
        val pageSize = getPageSize()
        val selectedClasses: List<ClassInfo> = selectedViewRows
            .sorted()
            .map { currentPage * pageSize + it }
            .filter { it in allClasses.indices }
            .map { allClasses[it] }
        
        if (selectedClasses.isEmpty()) return
        
        val cosyAction = ActionManager.getInstance().getAction(actionId)
            ?: run {
                Messages.showErrorDialog(
                    project,
                    "找不到 Lingma Action ($actionId)",
                    "LingmaHelper"
                )
                return
            }
        
        val settings = LingmaSettings.getInstance()
        val minDelay = settings.getMinDelaySeconds()
        val maxDelay = settings.getMaxDelaySeconds()
        
        // 统计任务数量：@Data 类 1 次，普通类按方法数；无 @Data 且无方法的类会被忽略
        var totalTasks = 0
        for (ci in selectedClasses) {
            totalTasks += when {
                hasLombokData(ci.psiClass) -> 1
                ci.psiClass.methods.isNotEmpty() -> ci.psiClass.methods.size
                else -> 0
            }
        }
        
        if (totalTasks == 0) {
            Messages.showInfoMessage(
                project,
                "选中的类中没有带 @Data 的类，且也没有包含任何方法，已忽略。",
                "LingmaHelper"
            )
            return
        }
        
        val avgDelay = (minDelay + maxDelay) / 2
        Messages.showInfoMessage(
            project,
            "将对 ${selectedClasses.size} 个类发起约 $totalTasks 次${actionDesc}请求\n" +
            "时间间隔: ${minDelay}-${maxDelay} 秒",
            "LingmaHelper"
        )
        
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val taskQueue = ArrayDeque<OptimizeTask>()
        
        for (classInfo in selectedClasses) {
            if (!classInfo.psiClass.isValid) continue
            val psiClass = classInfo.psiClass
            if (hasLombokData(psiClass)) {
                // @Data 类：整个类发送
                taskQueue.add(OptimizeTask.WholeClass(classInfo))
            } else {
                // 普通类：按方法发送；若无方法则忽略该类
                val methods = psiClass.methods
                if (methods.isEmpty()) continue
                for (method in methods) {
                    taskQueue.add(OptimizeTask.Method(classInfo, method))
                }
            }
        }
        
        fun scheduleNext() {
            val task = taskQueue.pollFirst() ?: run {
                scheduler.shutdown()
                return
            }
            ApplicationManager.getApplication().invokeLater {
                val vf = task.virtualFile ?: run {
                    scheduler.shutdown()
                    return@invokeLater
                }
                val fem = FileEditorManager.getInstance(project)
                fem.openFile(vf, true)
                val editor = fem.selectedTextEditor
                if (editor == null) {
                    scheduler.shutdown()
                    return@invokeLater
                }
                val doc = editor.document
                val (start, end) = when (task) {
                    is OptimizeTask.WholeClass -> task.classInfo.psiClass.textRange.let { it.startOffset to it.endOffset }
                    is OptimizeTask.Method -> task.method.textRange.let { it.startOffset to it.endOffset }
                }
                val safeStart = start.coerceIn(0, doc.textLength)
                val safeEnd = end.coerceIn(0, doc.textLength)
                editor.selectionModel.removeSelection()
                editor.caretModel.moveToOffset(safeStart)
                editor.selectionModel.setSelection(safeStart, safeEnd)
                val dataContext = DataManager.getInstance().getDataContext(editor.component)
                val event = AnActionEvent(
                    null,
                    dataContext,
                    ActionPlaces.EDITOR_POPUP,
                    cosyAction.templatePresentation.clone(),
                    ActionManager.getInstance(),
                    0
                )
                cosyAction.actionPerformed(event)
            }
            val delay = (minDelay..maxDelay).random().toLong()
            scheduler.schedule({ scheduleNext() }, delay, TimeUnit.SECONDS)
        }
        
        ApplicationManager.getApplication().invokeLater {
            scheduleNext()
        }
    }
    
    private sealed class OptimizeTask {
        abstract val virtualFile: VirtualFile?
        data class WholeClass(val classInfo: ClassInfo) : OptimizeTask() {
            override val virtualFile = classInfo.virtualFile
        }
        data class Method(val classInfo: ClassInfo, val method: PsiMethod) : OptimizeTask() {
            override val virtualFile = classInfo.virtualFile
        }
    }
    
    private fun hasLombokData(psiClass: PsiClass): Boolean {
        return psiClass.getAnnotation("lombok.Data") != null
    }

    /**
     * 检查类名是否属于需要忽略的领域类型（VO/DTO等），忽略大小写
     */
    private fun shouldIgnoreBySuffix(name: String): Boolean {
        val lower = name.lowercase(Locale.getDefault())
        return ignoredSuffixes.any { lower.endsWith(it) }
    }
    
    /**
     * 规范化字段名用于匹配 getter/setter
     */
    private fun normalizeFieldName(name: String): String {
        return name.trimStart('_').lowercase(Locale.getDefault())
    }
    
    /**
     * 判断方法是否为字段对应的 getter/setter 方法
     */
    private fun isAccessorMethod(method: PsiMethod, fieldNames: Set<String>): Boolean {
        val name = method.name
        val paramsCount = method.parameterList.parametersCount
        if (name.startsWith("get") && paramsCount == 0) {
            val property = normalizeFieldName(decapitalizeWord(name.removePrefix("get")))
            return property.isNotEmpty() && property in fieldNames
        }
        if (name.startsWith("set") && paramsCount == 1) {
            val property = normalizeFieldName(decapitalizeWord(name.removePrefix("set")))
            return property.isNotEmpty() && property in fieldNames
        }
        if (name.startsWith("is") && paramsCount == 0) {
            val property = normalizeFieldName(decapitalizeWord(name.removePrefix("is")))
            return property.isNotEmpty() && property in fieldNames
        }
        return false
    }
    
    private fun decapitalizeWord(value: String): String {
        if (value.isEmpty()) return value
        return value.replaceFirstChar { 
            if (it.isUpperCase()) it.lowercase(Locale.getDefault()) else it.toString() 
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
            
            allClasses = loadedClasses.toMutableList()
            sortClassesByClassName()
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

