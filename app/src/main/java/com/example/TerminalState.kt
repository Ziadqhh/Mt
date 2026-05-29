package com.example

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TerminalState(
    private val vfs: VirtualFileSystem,
    private val coroutineScope: CoroutineScope,
    private val askGemini: suspend (String) -> String
) {
    var currentTerminalPathSegments = mutableStateListOf("storage", "emulated", "0")
    val terminalLogs = mutableStateListOf<TerminalLogLine>()
    var inputBuffer by mutableStateOf("")

    init {
        terminalLogs.add(TerminalLogLine("MT Terminal Simulator v1.8.4 [Aarch64]", isHeader = true))
        terminalLogs.add(TerminalLogLine("Type 'help' to see available compilation, smali and git tools.", isSystemInfo = true))
        terminalLogs.add(TerminalLogLine("Integrated AI: prefix any command with 'ai' to prompt Gemini (e.g. 'ai explain const-string').", isSystemInfo = true))
        terminalLogs.add(TerminalLogLine(""))
    }

    data class TerminalLogLine(
        val text: String,
        val isInput: Boolean = false,
        val isError: Boolean = false,
        val isSystemInfo: Boolean = false,
        val isHeader: Boolean = false,
        val isSuccess: Boolean = false
    )

    fun executeTerminalCommand(fullCommand: String) {
        val trimmed = fullCommand.trim()
        if (trimmed.isEmpty()) return
        
        terminalLogs.add(TerminalLogLine("${getPromptSymbol()}$trimmed", isInput = true))
        inputBuffer = ""

        val parts = trimmed.split(" ")
        val command = parts[0].lowercase()
        val args = parts.drop(1)

        when {
            command == "clear" -> {
                terminalLogs.clear()
            }
            command == "help" -> {
                terminalLogs.add(TerminalLogLine("Standard commands: cd, ls, pwd, cat, clear, help, sysinfo"))
                terminalLogs.add(TerminalLogLine("Decompiler utilities: baksmali, apksigner, decompile, obfuscate"))
                terminalLogs.add(TerminalLogLine("AI command: ai <questions...> (Asks the built-in Gemini Assistant)"))
            }
            command == "sysinfo" -> {
                terminalLogs.add(TerminalLogLine("OS Architecture: Aarch64 (ARMv8-A)"))
                terminalLogs.add(TerminalLogLine("JVM Build: Runtime SE 17.0.5+8-LTS"))
                terminalLogs.add(TerminalLogLine("Device Mode: Simulated Android User Environment"))
                terminalLogs.add(TerminalLogLine("RAM Available: 4.82 GB / 12.00 GB"))
            }
            command == "pwd" -> {
                val fullPath = "/" + currentTerminalPathSegments.joinToString("/")
                terminalLogs.add(TerminalLogLine(fullPath))
            }
            command == "ls" -> {
                val targetDir = vfs.resolvePath(currentTerminalPathSegments)
                if (targetDir.children.isEmpty()) {
                    terminalLogs.add(TerminalLogLine("[Empty Directory]", isSystemInfo = true))
                } else {
                    targetDir.children.forEach { file ->
                        val prefix = if (file.isDirectory) "d_  " else "f_  "
                        val meta = if (file.isDirectory) " [Dir]" else " (${formatBytes(file.sizeBytes)})"
                        terminalLogs.add(TerminalLogLine("$prefix${file.name}$meta", isSuccess = !file.isDirectory))
                    }
                }
            }
            command == "cd" -> {
                if (args.isEmpty()) {
                    // Go to home
                    currentTerminalPathSegments.clear()
                    currentTerminalPathSegments.addAll(listOf("storage", "emulated", "0"))
                    terminalLogs.add(TerminalLogLine("Directory changed to root."))
                } else {
                    val targetName = args[0]
                    if (targetName == "..") {
                        if (currentTerminalPathSegments.size > 0) {
                            currentTerminalPathSegments.removeAt(currentTerminalPathSegments.size - 1)
                        }
                    } else {
                        val currentDir = vfs.resolvePath(currentTerminalPathSegments)
                        val found = currentDir.children.find { it.name == targetName && it.isDirectory }
                        if (found != null) {
                            currentTerminalPathSegments.add(targetName)
                        } else {
                            terminalLogs.add(TerminalLogLine("cd: no such directory: $targetName", isError = true))
                        }
                    }
                }
            }
            command == "cat" -> {
                if (args.isEmpty()) {
                    terminalLogs.add(TerminalLogLine("cat: missing file name", isError = true))
                } else {
                    val fileName = args[0]
                    val currentDir = vfs.resolvePath(currentTerminalPathSegments)
                    val found = currentDir.children.find { it.name == fileName && !it.isDirectory }
                    if (found != null) {
                        terminalLogs.add(TerminalLogLine("--- Content of ${found.name} ---", isSystemInfo = true))
                        found.content.split("\n").forEach { line ->
                            terminalLogs.add(TerminalLogLine(line))
                        }
                    } else {
                        terminalLogs.add(TerminalLogLine("cat: file not found: $fileName", isError = true))
                    }
                }
            }
            command == "baksmali" -> {
                terminalLogs.add(TerminalLogLine("Loading Baksmali disassembler v2.5.2...", isSystemInfo = true))
                terminalLogs.add(TerminalLogLine("Scanning input resources in /${currentTerminalPathSegments.joinToString("/")}/..."))
                terminalLogs.add(TerminalLogLine("[Log] Processing classes.dex (684,23 KB) ..."))
                terminalLogs.add(TerminalLogLine("[Log] Found 482 classes, 1592 methods, 259 fields."))
                terminalLogs.add(TerminalLogLine("[Log] Generating disassembled .smali files in /storage/emulated/0/Codex/..."))
                terminalLogs.add(TerminalLogLine("Disassembling Completed successfully!", isSuccess = true))
            }
            command == "apksigner" -> {
                terminalLogs.add(TerminalLogLine("Initializing APKSigner v1.2.9 cryptographic engine...", isSystemInfo = true))
                terminalLogs.add(TerminalLogLine("[Crypto] Loading certificate key: upload.jks ..."))
                terminalLogs.add(TerminalLogLine("[Crypto] Generating V2, V3 signature blocks ..."))
                terminalLogs.add(TerminalLogLine("[Crypto] HASH (SHA-256): 91d7fa28ba89cd2b6ab08f ..."))
                terminalLogs.add(TerminalLogLine("Signature verified and applied successfully to output.apk", isSuccess = true))
            }
            command == "decompile" -> {
                terminalLogs.add(TerminalLogLine("Unpacking resources with APKTool simulator v2.9.3...", isSystemInfo = true))
                terminalLogs.add(TerminalLogLine("[Decompile] Reading zipped binary layers ..."))
                terminalLogs.add(TerminalLogLine("[Decompile] Generating decompiled AndroidManifest.xml and res/ folder schema..."))
                terminalLogs.add(TerminalLogLine("Decompilation finished. Check MyCoolApp/ folder.", isSuccess = true))
            }
            command == "obfuscate" -> {
                terminalLogs.add(TerminalLogLine("Loading Proguard obfuscation optimizer...", isSystemInfo = true))
                terminalLogs.add(TerminalLogLine("[Obfuscate] Standardizing class dictionary mapping ..."))
                terminalLogs.add(TerminalLogLine("[Obfuscate] Mapping com.example.mycoolapp.DecompilerUtils -> com.example.mycoolapp.a.a"))
                terminalLogs.add(TerminalLogLine("Code optimization completed! Size reduced by 14.5%.", isSuccess = true))
            }
            command == "ai" -> {
                val query = args.joinToString(" ")
                if (query.isEmpty()) {
                    terminalLogs.add(TerminalLogLine("AI: please specify a question to ask Gemini (e.g. 'ai explain invoke-virtual')", isError = true))
                } else {
                    terminalLogs.add(TerminalLogLine("Asking Gemini assistant...", isSystemInfo = true))
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val prompt = "You are a highly professional Android reverse-engineering and coding helper inside MT Editor terminal. Answer the user prompt simply, concisely, directly, and elegantly: $query"
                            val answer = askGemini(prompt)
                            launch(Dispatchers.Main) {
                                terminalLogs.add(TerminalLogLine("AI Response:", isSuccess = true))
                                answer.split("\n").forEach { line ->
                                    terminalLogs.add(TerminalLogLine(line))
                                }
                            }
                        } catch (e: Exception) {
                            launch(Dispatchers.Main) {
                                terminalLogs.add(TerminalLogLine("AI Error to resolve: ${e.message}", isError = true))
                            }
                        }
                    }
                }
            }
            else -> {
                terminalLogs.add(TerminalLogLine("sh: command not found: $command. Type 'help' for directions.", isError = true))
            }
        }
    }

    fun getPromptSymbol(): String {
        val currentFolder = currentTerminalPathSegments.lastOrNull() ?: "root"
        return "mt@android:$currentFolder$ "
    }
}
