package com.liangyonglin.lingmaplus.scanner

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
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
    
    private data class ClassInfo(
        val className: String,
        val filePath: String,
        val methodCount: Int,
        val psiClass: PsiClass,
        val virtualFile: VirtualFile?
    )
    
    private val logger = thisLogger()
    private val scanButton = JButton("开始扫描")
    private val progressBar = JProgressBar()
    private val statusLabel = JLabel("准备就绪")
    private val tableModel = DefaultTableModel(arrayOf("类名", "方法数", "文件路径"), 0)
    private val table = JBTable(tableModel)
    private val pageInfoLabel = JLabel("")
    private val prevButton = JButton("上一页")
    private val nextButton = JButton("下一页")
    
    private var allClasses: List<ClassInfo> = emptyList()
    private var currentPage = 0
    private val pageSize = 20
    
    init {
        setupUI()
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
        columnModel.getColumn(0).preferredWidth = 300  // 类名
        columnModel.getColumn(1).preferredWidth = 80    // 方法数
        columnModel.getColumn(2).preferredWidth = 500   // 文件路径
        
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
            
            // 收集所有类信息
            val classesWithManyMethods = mutableListOf<ClassInfo>()
            
            for (psiFile in allJavaFiles) {
                try {
                    for (psiClass in psiFile.classes) {
                        val methods = psiClass.methods
                        if (methods.size > 20) {
                            val virtualFile = psiFile.virtualFile
                            classesWithManyMethods.add(
                                ClassInfo(
                                    className = psiClass.qualifiedName ?: psiClass.name ?: "Unknown",
                                    filePath = virtualFile?.path ?: "Unknown",
                                    methodCount = methods.size,
                                    psiClass = psiClass,
                                    virtualFile = virtualFile
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("处理类时出错: ${psiFile.name}", e)
                }
            }
            
            logger.info("找到 ${classesWithManyMethods.size} 个包含超过20个方法的类")
            
            // 随机选择前100个
            Collections.shuffle(classesWithManyMethods)
            allClasses = classesWithManyMethods.take(100)
            
            // 更新UI
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                updateTable()
                scanButton.isEnabled = true
                progressBar.isVisible = false
                statusLabel.text = "扫描完成: 找到 ${allClasses.size} 个类"
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
}

