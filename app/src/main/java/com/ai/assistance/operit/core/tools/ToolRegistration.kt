package com.ai.assistance.operit.core.tools

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.permissions.ToolCategory
import org.json.JSONArray

/**
 * This file contains all tool registrations centralized for easier maintenance and integration It
 * extracts the registerTools logic from AIToolHandler into a dedicated file
 */

/**
 * Register all available tools with the AIToolHandler
 * @param handler The AIToolHandler instance to register tools with
 * @param context Application context for tools that need it
 */
fun registerAllTools(handler: AIToolHandler, context: Context) {
    // 不在提示词加入的工具
    handler.registerTool(
            name = "execute_shell",
            category = ToolCategory.SYSTEM_OPERATION,
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                "执行ADB Shell: $command"
            },
            executor = { tool ->
                val adbTool = ToolGetter.getShellToolExecutor(context)
                adbTool.invoke(tool)
            }
    )

    // 终端命令执行工具 - 一次性收集输出
    handler.registerTool(
            name = "execute_terminal",
            category = ToolCategory.SYSTEM_OPERATION,
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                val sessionId = tool.parameters.find { it.name == "session_id" }?.value
                if (sessionId != null) {
                    "执行终端命令 (会话: $sessionId): $command"
                } else {
                    "执行终端命令: $command"
                }
            },
            executor = { tool ->
                val terminalTool = ToolGetter.getTerminalCommandExecutor(context)
                terminalTool.invoke(tool)
            }
    )

    // 注册问题库查询工具
    handler.registerTool(
            name = "query_problem_library",
            category = ToolCategory.FILE_READ,
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val query = tool.parameters.find { it.name == "query" }?.value ?: ""
                "查询问题库: $query"
            },
            executor = { tool ->
                val problemLibraryTool = ToolGetter.getProblemLibraryToolExecutor(context)
                problemLibraryTool.invoke(tool)
            }
    )

    // 系统操作工具
    handler.registerTool(
            name = "use_package",
            category = ToolCategory.FILE_READ,
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                "使用工具包: $packageName"
            },
            executor = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                val result = handler.getOrCreatePackageManager().usePackage(packageName)
                ToolResult(toolName = tool.name, success = true, result = StringResultData(result))
            }
    )

    // ADB命令执行工具

    // 计算器工具
    handler.registerTool(
            name = "calculate",
            category = ToolCategory.FILE_READ,
            descriptionGenerator = { tool ->
                val expression = tool.parameters.find { it.name == "expression" }?.value ?: ""
                "计算表达式: $expression"
            },
            executor = { tool ->
                val expression = tool.parameters.find { it.name == "expression" }?.value ?: ""
                try {
                    val result = ToolGetter.getCalculator().evalExpression(expression)
                    ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = StringResultData("Calculation result: $result")
                    )
                } catch (e: Exception) {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Calculation error: ${e.message}"
                    )
                }
            }
    )

    // Web搜索工具
    handler.registerTool(
            name = "visit_web",
            category = ToolCategory.NETWORK,
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                "访问网页: $url"
            },
            executor = { tool ->
                val webVisitTool = ToolGetter.getWebVisitTool(context)
                webVisitTool.invoke(tool)
            }
    )

    // 休眠工具
    handler.registerTool(
            name = "sleep",
            category = ToolCategory.SYSTEM_OPERATION,
            descriptionGenerator = { tool ->
                val durationMs =
                        tool.parameters.find { it.name == "duration_ms" }?.value?.toIntOrNull()
                                ?: 1000
                "休眠 ${durationMs}毫秒"
            },
            executor = { tool ->
                val durationMs =
                        tool.parameters.find { it.name == "duration_ms" }?.value?.toIntOrNull()
                                ?: 1000
                val limitedDuration = durationMs.coerceIn(0, 10000) // Limit to max 10 seconds

                Thread.sleep(limitedDuration.toLong())

                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData("Slept for ${limitedDuration}ms")
                )
            }
    )

    // Intent工具
    handler.registerTool(
            name = "execute_intent",
            category = ToolCategory.SYSTEM_OPERATION,
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val action = tool.parameters.find { it.name == "action" }?.value
                val packageName = tool.parameters.find { it.name == "package" }?.value
                val component = tool.parameters.find { it.name == "component" }?.value
                val type = tool.parameters.find { it.name == "type" }?.value ?: "activity"

                when {
                    !component.isNullOrBlank() -> "执行Intent: 组件 $component (${type})"
                    !packageName.isNullOrBlank() && !action.isNullOrBlank() ->
                            "执行Intent: $action (包: $packageName, 类型: ${type})"
                    !action.isNullOrBlank() -> "执行Intent: $action (类型: ${type})"
                    else -> "执行Android Intent (类型: ${type})"
                }
            },
            executor = { tool ->
                val intentTool = ToolGetter.getIntentToolExecutor(context)
                kotlinx.coroutines.runBlocking { intentTool.invoke(tool) }
            }
    )

    // 设备信息工具
    handler.registerTool(
            name = "device_info",
            category = ToolCategory.SYSTEM_OPERATION,
            descriptionGenerator = { _ -> "获取设备信息" },
            executor = { tool ->
                val deviceInfoTool = ToolGetter.getDeviceInfoToolExecutor(context)
                deviceInfoTool.invoke(tool)
            }
    )

    // 文件系统工具
    val fileSystemTools = ToolGetter.getFileSystemTools(context)

    // 列出目录内容
    handler.registerTool(
            name = "list_files",
            category = ToolCategory.FILE_READ,
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                "列出目录内容: $path"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { fileSystemTools.listFiles(tool) }
            }
    )

    // 读取文件内容
    handler.registerTool(
            name = "read_file",
            category = ToolCategory.FILE_READ,
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                "读取文件: $path"
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { fileSystemTools.readFile(tool) } }
    )

    // 分段读取文件内容
    handler.registerTool(
            name = "read_file_part",
            category = ToolCategory.FILE_READ,
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val partIndex = tool.parameters.find { it.name == "partIndex" }?.value ?: "0"
                "分段读取文件 (部分 $partIndex): $path"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { fileSystemTools.readFilePart(tool) }
            }
    )

    // 写入文件
    handler.registerTool(
            name = "write_file",
            category = ToolCategory.FILE_WRITE,
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val append = tool.parameters.find { it.name == "append" }?.value == "true"
                if (append) "追加内容到文件: $path" else "写入内容到文件: $path"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { fileSystemTools.writeFile(tool) }
            }
    )

    // 删除文件/目录
    handler.registerTool(
            name = "delete_file",
            category = ToolCategory.FILE_WRITE,
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val recursive = tool.parameters.find { it.name == "recursive" }?.value == "true"
                if (recursive) "递归删除: $path" else "删除文件: $path"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { fileSystemTools.deleteFile(tool) }
            }
    )

    // UI自动化工具
    val uiTools = ToolGetter.getUITools(context)

    // 点击元素
    handler.registerTool(
            name = "click_element",
            category = ToolCategory.UI_AUTOMATION,
            dangerCheck = { tool ->
                val resourceId = tool.parameters.find { it.name == "resourceId" }?.value ?: ""
                val className = tool.parameters.find { it.name == "className" }?.value ?: ""
                val dangerousWords =
                        listOf(
                                "send",
                                "submit",
                                "confirm",
                                "pay",
                                "purchase",
                                "buy",
                                "delete",
                                "remove",
                                "发送",
                                "提交",
                                "确认",
                                "支付",
                                "购买",
                                "删除",
                                "移除"
                        )

                dangerousWords.any { word ->
                    resourceId.contains(word, ignoreCase = true) ||
                            className.contains(word, ignoreCase = true)
                }
            },
            descriptionGenerator = { tool ->
                val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
                val className = tool.parameters.find { it.name == "className" }?.value
                val bounds = tool.parameters.find { it.name == "bounds" }?.value
                val index = tool.parameters.find { it.name == "index" }?.value ?: "0"

                when {
                    resourceId != null ->
                            "点击元素 [资源ID: $resourceId" +
                                    (if (index != "0") ", 索引: $index" else "") +
                                    "]"
                    className != null ->
                            "点击元素 [类名: $className" +
                                    (if (index != "0") ", 索引: $index" else "") +
                                    "]"
                    bounds != null -> "点击元素 [边界: $bounds]"
                    else -> "点击元素"
                }
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { uiTools.clickElement(tool) } }
    )

    // 点击屏幕坐标
    handler.registerTool(
            name = "tap",
            category = ToolCategory.UI_AUTOMATION,
            descriptionGenerator = { tool ->
                val x = tool.parameters.find { it.name == "x" }?.value ?: "?"
                val y = tool.parameters.find { it.name == "y" }?.value ?: "?"
                "点击屏幕坐标 ($x, $y)"
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { uiTools.tap(tool) } }
    )

    // HTTP请求工具
    val httpTools = ToolGetter.getHttpTools(context)

    // 发送HTTP请求
    handler.registerTool(
            name = "http_request",
            category = ToolCategory.NETWORK,
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                val method = tool.parameters.find { it.name == "method" }?.value ?: "GET"
                "$method 请求: $url"
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { httpTools.httpRequest(tool) } }
    )

    // 多部分表单请求（文件上传）
    handler.registerTool(
            name = "multipart_request",
            category = ToolCategory.NETWORK,
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                val filesParam = tool.parameters.find { it.name == "files" }?.value ?: "[]"
                val filesCount =
                        try {
                            JSONArray(filesParam).length()
                        } catch (e: Exception) {
                            0
                        }
                "多部分表单请求: $url (包含 $filesCount 个文件)"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { httpTools.multipartRequest(tool) }
            }
    )

    // 管理Cookie工具
    handler.registerTool(
            name = "manage_cookies",
            category = ToolCategory.NETWORK,
            descriptionGenerator = { tool ->
                val action =
                        tool.parameters.find { it.name == "action" }?.value?.lowercase() ?: "get"
                val domain = tool.parameters.find { it.name == "domain" }?.value ?: ""
                when (action) {
                    "get" -> if (domain.isBlank()) "获取所有Cookie" else "获取域名 $domain 的Cookie"
                    "set" -> "设置Cookie到域名 $domain"
                    "clear" -> if (domain.isBlank()) "清除所有Cookie" else "清除域名 $domain 的Cookie"
                    else -> "管理Cookie: $action"
                }
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { httpTools.manageCookies(tool) } }
    )

    // 检查文件是否存在
    handler.registerTool(
            name = "file_exists",
            category = ToolCategory.FILE_READ,
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                "检查文件存在: $path"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { fileSystemTools.fileExists(tool) }
            }
    )

    // 移动/重命名文件或目录
    handler.registerTool(
            name = "move_file",
            category = ToolCategory.FILE_WRITE,
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                "移动文件: $source -> $destination"
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { fileSystemTools.moveFile(tool) } }
    )

    // 复制文件或目录
    handler.registerTool(
            name = "copy_file",
            category = ToolCategory.FILE_WRITE,
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                "复制文件: $source -> $destination"
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { fileSystemTools.copyFile(tool) } }
    )

    // 创建目录
    handler.registerTool(
            name = "make_directory",
            category = ToolCategory.FILE_WRITE,
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                "创建目录: $path"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { fileSystemTools.makeDirectory(tool) }
            }
    )

    // 搜索文件
    handler.registerTool(
            name = "find_files",
            category = ToolCategory.FILE_READ,
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: "*"
                "搜索文件: 在 $path 中查找 $pattern"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { fileSystemTools.findFiles(tool) }
            }
    )

    // 获取文件信息
    handler.registerTool(
            name = "file_info",
            category = ToolCategory.FILE_READ,
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                "获取文件信息: $path"
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { fileSystemTools.fileInfo(tool) } }
    )

    // 智能应用文件绑定
    handler.registerTool(
            name = "apply_file",
            category = ToolCategory.FILE_WRITE,
            dangerCheck = { true }, // 总是危险操作
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                "智能合并AI代码到文件: $path"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { fileSystemTools.applyFile(tool) }
            }
    )

    // 压缩文件/目录
    handler.registerTool(
            name = "zip_files",
            category = ToolCategory.FILE_WRITE,
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                "压缩文件: $source -> $destination"
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { fileSystemTools.zipFiles(tool) } }
    )

    // 解压缩文件
    handler.registerTool(
            name = "unzip_files",
            category = ToolCategory.FILE_WRITE,
            descriptionGenerator = { tool ->
                val source = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                "解压文件: $source -> $destination"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { fileSystemTools.unzipFiles(tool) }
            }
    )

    // 打开文件
    handler.registerTool(
            name = "open_file",
            category = ToolCategory.FILE_READ,
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                "打开文件: $path"
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { fileSystemTools.openFile(tool) } }
    )

    // 分享文件
    handler.registerTool(
            name = "share_file",
            category = ToolCategory.FILE_WRITE,
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                "分享文件: $path"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { fileSystemTools.shareFile(tool) }
            }
    )

    // 下载文件
    handler.registerTool(
            name = "download_file",
            category = ToolCategory.NETWORK,
            descriptionGenerator = { tool ->
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                val destination = tool.parameters.find { it.name == "destination" }?.value ?: ""
                "下载文件: $url -> $destination"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { fileSystemTools.downloadFile(tool) }
            }
    )

    // 系统操作工具
    val systemOperationTools = ToolGetter.getSystemOperationTools(context)

    // 修改系统设置
    handler.registerTool(
            name = "modify_system_setting",
            category = ToolCategory.SYSTEM_OPERATION,
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val key = tool.parameters.find { it.name == "key" }?.value ?: ""
                val value = tool.parameters.find { it.name == "value" }?.value ?: ""
                "修改系统设置: $key = $value"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { systemOperationTools.modifySystemSetting(tool) }
            }
    )

    // 获取系统设置
    handler.registerTool(
            name = "get_system_setting",
            category = ToolCategory.SYSTEM_OPERATION,
            descriptionGenerator = { tool ->
                val key = tool.parameters.find { it.name == "key" }?.value ?: ""
                "获取系统设置: $key"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { systemOperationTools.getSystemSetting(tool) }
            }
    )

    // 安装应用
    handler.registerTool(
            name = "install_app",
            category = ToolCategory.SYSTEM_OPERATION,
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                "安装应用: $path"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { systemOperationTools.installApp(tool) }
            }
    )

    // 卸载应用
    handler.registerTool(
            name = "uninstall_app",
            category = ToolCategory.SYSTEM_OPERATION,
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                "卸载应用: $packageName"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { systemOperationTools.uninstallApp(tool) }
            }
    )

    // 获取已安装应用列表
    handler.registerTool(
            name = "list_installed_apps",
            category = ToolCategory.SYSTEM_OPERATION,
            descriptionGenerator = { _ -> "列出已安装应用" },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { systemOperationTools.listInstalledApps(tool) }
            }
    )

    // 启动应用
    handler.registerTool(
            name = "start_app",
            category = ToolCategory.SYSTEM_OPERATION,
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                "启动应用: $packageName"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { systemOperationTools.startApp(tool) }
            }
    )

    // 停止应用
    handler.registerTool(
            name = "stop_app",
            category = ToolCategory.SYSTEM_OPERATION,
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
                "停止应用: $packageName"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { systemOperationTools.stopApp(tool) }
            }
    )

    // 获取设备通知
    handler.registerTool(
            name = "get_notifications",
            category = ToolCategory.SYSTEM_OPERATION,
            descriptionGenerator = { tool ->
                val limit = tool.parameters.find { it.name == "limit" }?.value ?: "10"
                val includeOngoing =
                        tool.parameters.find { it.name == "include_ongoing" }?.value == "true"

                val description = "获取设备通知 (最多 $limit 条)"
                if (includeOngoing) "$description，包括常驻通知" else description
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { systemOperationTools.getNotifications(tool) }
            }
    )

    // 获取设备位置
    handler.registerTool(
            name = "get_device_location",
            category = ToolCategory.SYSTEM_OPERATION,
            descriptionGenerator = { tool ->
                val highAccuracy =
                        tool.parameters.find { it.name == "high_accuracy" }?.value == "true"
                if (highAccuracy) "获取设备位置 (高精度)" else "获取设备位置"
            },
            executor = { tool ->
                kotlinx.coroutines.runBlocking { systemOperationTools.getDeviceLocation(tool) }
            }
    )

    // 获取当前页面/窗口信息
    handler.registerTool(
            name = "get_page_info",
            category = ToolCategory.UI_AUTOMATION,
            descriptionGenerator = { _ -> "获取当前页面信息" },
            executor = { tool -> kotlinx.coroutines.runBlocking { uiTools.getPageInfo(tool) } }
    )

    // 在输入框中设置文本
    handler.registerTool(
            name = "set_input_text",
            category = ToolCategory.UI_AUTOMATION,
            descriptionGenerator = { tool ->
                val text = tool.parameters.find { it.name == "text" }?.value ?: ""
                "设置输入文本: $text"
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { uiTools.setInputText(tool) } }
    )

    // 按下特定按键
    handler.registerTool(
            name = "press_key",
            category = ToolCategory.UI_AUTOMATION,
            descriptionGenerator = { tool ->
                val keyCode = tool.parameters.find { it.name == "key_code" }?.value ?: ""
                "按下按键: $keyCode"
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { uiTools.pressKey(tool) } }
    )

    // 执行滑动手势
    handler.registerTool(
            name = "swipe",
            category = ToolCategory.UI_AUTOMATION,
            descriptionGenerator = { tool ->
                val startX = tool.parameters.find { it.name == "start_x" }?.value ?: "?"
                val startY = tool.parameters.find { it.name == "start_y" }?.value ?: "?"
                val endX = tool.parameters.find { it.name == "end_x" }?.value ?: "?"
                val endY = tool.parameters.find { it.name == "end_y" }?.value ?: "?"
                "滑动: ($startX,$startY) -> ($endX,$endY)"
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { uiTools.swipe(tool) } }
    )

    // 查找UI元素
    handler.registerTool(
            name = "find_element",
            category = ToolCategory.UI_AUTOMATION,
            descriptionGenerator = { tool ->
                val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
                val className = tool.parameters.find { it.name == "className" }?.value
                val text = tool.parameters.find { it.name == "text" }?.value

                val criteria =
                        listOfNotNull(
                                        resourceId?.let { "ID: $it" },
                                        className?.let { "类: $it" },
                                        text?.let { "文本: $it" }
                                )
                                .joinToString(", ")

                "查找UI元素: $criteria"
            },
            executor = { tool -> kotlinx.coroutines.runBlocking { uiTools.findElement(tool) } }
    )

    // FFmpeg工具 - 执行通用FFmpeg命令
    handler.registerTool(
            name = "ffmpeg_execute",
            category = ToolCategory.FILE_WRITE,
            dangerCheck = { true }, // 总是危险操作，因为可能会修改文件
            descriptionGenerator = { tool ->
                val command = tool.parameters.find { it.name == "command" }?.value ?: ""
                "执行FFmpeg命令: $command"
            },
            executor = { tool ->
                val ffmpegTool = ToolGetter.getFFmpegToolExecutor(context)
                ffmpegTool.invoke(tool)
            }
    )

    // FFmpeg信息工具 - 获取FFmpeg信息
    handler.registerTool(
            name = "ffmpeg_info",
            category = ToolCategory.FILE_READ,
            descriptionGenerator = { _ -> "获取FFmpeg信息" },
            executor = { tool ->
                val ffmpegInfoTool = ToolGetter.getFFmpegInfoToolExecutor()
                ffmpegInfoTool.invoke(tool)
            }
    )

    // FFmpeg视频转换工具 - 简化的视频转换接口
    handler.registerTool(
            name = "ffmpeg_convert",
            category = ToolCategory.FILE_WRITE,
            dangerCheck = { true }, // 总是危险操作，因为会创建新文件
            descriptionGenerator = { tool ->
                val inputPath = tool.parameters.find { it.name == "input_path" }?.value ?: ""
                val outputPath = tool.parameters.find { it.name == "output_path" }?.value ?: ""
                "转换视频: $inputPath → $outputPath"
            },
            executor = { tool ->
                val ffmpegConvertTool = ToolGetter.getFFmpegConvertToolExecutor(context)
                ffmpegConvertTool.invoke(tool)
            }
    )

    // 文件格式转换工具
    val fileConverterTool = ToolGetter.getFileConverterToolExecutor(context)

    // 文件格式转换
    handler.registerTool(
            name = "convert_file",
            category = ToolCategory.FILE_WRITE,
            dangerCheck = { true }, // 总是危险操作，因为会创建新文件
            descriptionGenerator = { tool ->
                val sourcePath = tool.parameters.find { it.name == "source_path" }?.value ?: ""
                val targetPath = tool.parameters.find { it.name == "target_path" }?.value ?: ""
                "转换文件格式: $sourcePath → $targetPath"
            },
            executor = { tool -> fileConverterTool.invoke(tool) }
    )

    // 获取支持的文件转换格式
    handler.registerTool(
            name = "get_supported_conversions",
            category = ToolCategory.FILE_READ,
            descriptionGenerator = { _ -> "获取支持的文件转换格式" },
            executor = { tool -> fileConverterTool.invoke(tool) }
    )
}
