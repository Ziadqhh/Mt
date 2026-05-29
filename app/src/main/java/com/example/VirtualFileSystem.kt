package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface SystemItem : Serializable {
    val name: String
    val isDirectory: Boolean
    val dateModified: String
    val sizeString: String
}

data class VirtualFile(
    override val name: String,
    override val isDirectory: Boolean,
    var content: String = "",
    override val dateModified: String = getCurrentDateString(),
    var sizeBytes: Long = if (isDirectory) 0L else content.toByteArray().size.toLong(),
    val children: MutableList<VirtualFile> = mutableListOf()
) : SystemItem {
    override val sizeString: String
        get() = if (isDirectory) {
            val fileCount = children.filter { !it.isDirectory }.size
            val folderCount = children.filter { it.isDirectory }.size
            "$folderCount F, $fileCount f"
        } else {
            formatBytes(sizeBytes)
        }
}

fun getCurrentDateString(): String {
    return try {
        val sdf = SimpleDateFormat("yy-MM-dd HH:mm", Locale.US)
        sdf.format(Date())
    } catch (t: Throwable) {
        "26-05-29 12:47"
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun getDiskInfo(path: String = "/"): String {
    return try {
        val stat = android.os.StatFs(path)
        val available = stat.availableBlocksLong * stat.blockSizeLong
        val total = stat.blockCountLong * stat.blockSizeLong
        val used = total - available
        "${formatBytes(used)} used, ${formatBytes(available)} available"
    } catch (e: Exception) {
        "91.54 GB used, 11.26 GB available"
    }
}

fun safelyReadFileContent(file: File): String {
    return try {
        if (file.exists() && file.isFile) {
            if (file.length() < 500_000L) { // 500KB limit for editing
                file.readText()
            } else {
                "[Large File: ${formatBytes(file.length())}]"
            }
        } else ""
    } catch (e: Exception) {
        "[Unreadable file or permission denied]"
    }
}

class VirtualFileSystem {
    var root: VirtualFile = createDefaultVirtualFileSystem()
    var useRealFS: Boolean = true
    var customRootPath: String? = null

    fun getRealFile(pathSegments: List<String>, fileName: String? = null): File {
        val base = customRootPath ?: ""
        val parts = if (fileName != null) pathSegments + fileName else pathSegments
        val fullPath = if (base.isNotEmpty()) {
            base + "/" + parts.joinToString("/")
        } else {
            "/" + parts.joinToString("/")
        }
        return File(fullPath)
    }

    fun getFileContent(pathSegments: List<String>, fileName: String): String {
        if (useRealFS) {
            try {
                val file = getRealFile(pathSegments, fileName)
                if (file.exists() && file.isFile) {
                    return safelyReadFileContent(file)
                }
            } catch (e: Exception) {
                // fallback below
            }
        }
        val parent = resolvePath(pathSegments)
        val found = parent.children.find { it.name == fileName && !it.isDirectory }
        return found?.content ?: ""
    }

    fun resolvePath(pathSegments: List<String>): VirtualFile {
        if (useRealFS) {
            try {
                val file = getRealFile(pathSegments)
                if (file.exists() && file.isDirectory) {
                    val filesList = file.listFiles()
                    val childrenList = mutableListOf<VirtualFile>()
                    if (filesList != null) {
                        for (f in filesList) {
                            childrenList.add(
                                VirtualFile(
                                    name = f.name,
                                    isDirectory = f.isDirectory,
                                    content = if (f.isDirectory) "" else safelyReadFileContent(f),
                                    dateModified = try {
                                        val sdf = SimpleDateFormat("yy-MM-dd HH:mm", Locale.US)
                                        sdf.format(Date(f.lastModified()))
                                    } catch (e: Exception) {
                                        getCurrentDateString()
                                    },
                                    sizeBytes = if (f.isDirectory) 0L else f.length()
                                )
                            )
                        }
                    }
                    return VirtualFile(
                        name = file.name.ifEmpty { "root" },
                        isDirectory = true,
                        children = childrenList
                    )
                }
            } catch (e: Exception) {
                // fallback
            }
        }

        // Fallback to virtual filesystem
        var current = root
        for (segment in pathSegments) {
            val found = current.children.find { it.name == segment && it.isDirectory }
            if (found != null) {
                current = found
            } else {
                break
            }
        }
        return current
    }

    fun addFile(pathSegments: List<String>, name: String, text: String, isFolder: Boolean = false): Boolean {
        if (useRealFS) {
            try {
                val file = getRealFile(pathSegments, name)
                if (file.exists()) return false
                if (isFolder) {
                    return file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                    file.writeText(text)
                    return true
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        val target = resolvePath(pathSegments)
        if (target.children.any { it.name == name }) return false // Already exists
        val newFile = VirtualFile(
            name = name,
            isDirectory = isFolder,
            content = if (isFolder) "" else text,
            dateModified = getCurrentDateString()
        )
        target.children.add(newFile)
        return true
    }

    fun renameFile(pathSegments: List<String>, oldName: String, newName: String): Boolean {
        if (useRealFS) {
            try {
                val oldFile = getRealFile(pathSegments, oldName)
                val newFile = getRealFile(pathSegments, newName)
                if (oldFile.exists() && !newFile.exists()) {
                    return oldFile.renameTo(newFile)
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        val target = resolvePath(pathSegments)
        val fileItem = target.children.find { it.name == oldName } ?: return false
        if (target.children.any { it.name == newName }) return false // Name collision
        
        target.children.remove(fileItem)
        val renamed = fileItem.copy(name = newName, dateModified = getCurrentDateString())
        target.children.add(renamed)
        return true
    }

    fun deleteFile(pathSegments: List<String>, name: String): Boolean {
        if (useRealFS) {
            try {
                val file = getRealFile(pathSegments, name)
                if (file.exists()) {
                    return file.deleteRecursively()
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        val target = resolvePath(pathSegments)
        val fileItem = target.children.find { it.name == name } ?: return false
        target.children.remove(fileItem)
        return true
    }

    fun copyFile(sourcePath: List<String>, sourceName: String, targetPath: List<String>): Boolean {
        if (useRealFS) {
            try {
                val srcFile = getRealFile(sourcePath, sourceName)
                val destFile = getRealFile(targetPath, sourceName)
                if (srcFile.exists()) {
                    if (srcFile.isDirectory) {
                        srcFile.copyRecursively(destFile, overwrite = true)
                    } else {
                        srcFile.copyTo(destFile, overwrite = true)
                    }
                    return true
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        val sourceDir = resolvePath(sourcePath)
        val sourceItem = sourceDir.children.find { it.name == sourceName } ?: return false
        val targetDir = resolvePath(targetPath)
        
        if (sourceItem.isDirectory && isSubdir(sourcePath + sourceName, targetPath)) {
            return false
        }
        
        if (targetDir.children.any { it.name == sourceItem.name }) {
            val baseName = sourceItem.name
            var renamed = "Copy_of_$baseName"
            var idx = 1
            while (targetDir.children.any { it.name == renamed }) {
                renamed = "Copy_of_${idx}_$baseName"
                idx++
            }
            val copied = deepCopy(sourceItem, renamed)
            targetDir.children.add(copied)
        } else {
            targetDir.children.add(deepCopy(sourceItem, sourceItem.name))
        }
        return true
    }

    fun moveFile(sourcePath: List<String>, sourceName: String, targetPath: List<String>): Boolean {
        if (useRealFS) {
            try {
                val srcFile = getRealFile(sourcePath, sourceName)
                val destFile = getRealFile(targetPath, sourceName)
                if (srcFile.exists()) {
                    val completed = srcFile.renameTo(destFile)
                    if (completed) return true
                    if (srcFile.isDirectory) {
                        if (srcFile.copyRecursively(destFile, overwrite = true)) {
                            srcFile.deleteRecursively()
                            return true
                        }
                    } else {
                        if (srcFile.copyTo(destFile, overwrite = true) != null) {
                            srcFile.delete()
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        val sourceDir = resolvePath(sourcePath)
        val sourceItem = sourceDir.children.find { it.name == sourceName } ?: return false
        val targetDir = resolvePath(targetPath)
        
        if (sourceItem.isDirectory && isSubdir(sourcePath + sourceName, targetPath)) {
            return false
        }
        
        if (copyFile(sourcePath, sourceName, targetPath)) {
            sourceDir.children.remove(sourceItem)
            return true
        }
        return false
    }

    private fun isSubdir(path1: List<String>, path2: List<String>): Boolean {
        if (path2.size < path1.size) return false
        for (i in path1.indices) {
            if (path1[i] != path2[i]) return false
        }
        return true
    }

    private fun deepCopy(file: VirtualFile, newName: String): VirtualFile {
        val copiedChildren = file.children.map { deepCopy(it, it.name) }.toMutableList()
        return VirtualFile(
            name = newName,
            isDirectory = file.isDirectory,
            content = file.content,
            dateModified = getCurrentDateString(),
            sizeBytes = file.sizeBytes,
            children = copiedChildren
        )
    }

    fun updateFileContent(pathSegments: List<String>, fileName: String, newContent: String) {
        if (useRealFS) {
            try {
                val fullPath = "/" + (pathSegments + fileName).joinToString("/")
                val file = File(fullPath)
                file.parentFile?.mkdirs()
                file.writeText(newContent)
                return
            } catch (e: Exception) {
                // fallback
            }
        }
        val parent = resolvePath(pathSegments)
        val fileItem = parent.children.find { it.name == fileName && !it.isDirectory }
        if (fileItem != null) {
            fileItem.content = newContent
            fileItem.sizeBytes = newContent.toByteArray().size.toLong()
        }
    }
}

fun ensureRealDirectorySetup(context: Context, customPath: String? = null) {
    try {
        val rootPath = customPath ?: "/storage/emulated/0"
        val rootDir = File(rootPath)
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        
        // AndroidCSProjects
        val csProjects = File(rootDir, "AndroidCSProjects/MyCoolApp/app/src/main/java/com/example/mycoolapp")
        if (!csProjects.exists()) {
            csProjects.mkdirs()
        }
        
        val mainActivity = File(csProjects, "MainActivity.kt")
        if (!mainActivity.exists()) {
            mainActivity.writeText("""package com.example.mycoolapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val textView = findViewById<TextView>(R.id.hello_text)
        textView.text = "Hello, MT Editor User!"
        
        textView.setOnClickListener {
            Toast.makeText(this, "Decompiling looks fine!", Toast.LENGTH_SHORT).show()
        }
    }
}""")
        }

        val utils = File(csProjects, "DecompilerUtils.java")
        if (!utils.exists()) {
            utils.writeText("""package com.example.mycoolapp;

import java.io.File;
import java.io.InputStream;

/**
 * MT Editor Custom Utility to assist in loading and parsing dex instructions.
 */
public class DecompilerUtils {
    public static final String VERSION = "1.4.2_PRO";

    public static boolean decompileDex(File dexFile, File outputDir) {
        if (dexFile == null || !dexFile.exists()) {
            return false;
        }
        System.out.println("Unpacking Dex logic running...");
        return true;
    }
}""")
        }

        // ObfuscatorTools
        val obf = File(rootDir, "ObfuscatorTools")
        if (!obf.exists()) obf.mkdirs()
        val proguard = File(obf, "proguard-rules.pro")
        if (!proguard.exists()) {
            proguard.writeText("""# Professional Proguard rules for MT Editor builds
-keep class com.example.mycoolapp.DecompilerUtils {
    public static *** decompileDex(...);
}
-keepattributes Signature,Exceptions,InnerClasses
-dontwarn okio.**
-allowaccessmodification
-repackageclasses 'com.example.mycoolapp.internal'""")
        }

        // Codex
        val codex = File(rootDir, "Codex")
        if (!codex.exists()) codex.mkdirs()
        val smali = File(codex, "TestActivity.smali")
        if (!smali.exists()) {
            smali.writeText(""".class public Lcom/example/TestActivity;
.super Landroid/app/Activity;
.source "TestActivity.java"


# direct methods
.method public constructor <init>()V
    .registers 1

    .line 8
    invoke-direct {p0}, Landroid/app/Activity;-><init>()V

    return-void
.end method


# virtual methods
.method protected onCreate(Landroid/os/Bundle;)V
    .registers 4

    .line 12
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    .line 13
    const-string v0, "Activity created with MT Editor!"

    const/4 v1, 0x1

    invoke-static {p0, v0, v1}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;

    move-result-object v0

    invoke-virtual {v0}, Landroid/widget/Toast;->show()V

    .line 14
    return-void
.end method""")
        }
        
        val patchDemo = File(codex, "smali_patch_demo.txt")
        if (!patchDemo.exists()) {
            patchDemo.writeText("""# Easy smali code to bypass signatures:
.method public static isPremium()Z
    .registers 1
    
    const/4 v0, 0x1
    return v0
.end method""")
        }

        // Download
        val download = File(rootDir, "Download")
        if (!download.exists()) download.mkdirs()
        val apkFile = File(download, "MT_Manager_v2.14.apk")
        if (!apkFile.exists()) {
            apkFile.writeText("[Binary APK Mock Resource] - Size 12.4 MB. Right-click or decompile on Extract APK helper to disassemble.")
        }
        val zipFile = File(download, "vico_plugin_libs.zip")
        if (!zipFile.exists()) {
            zipFile.writeText("ZIP archive compression layer. Contains .dex bytecode structures.")
        }
        val buildLogFile = File(download, "build_log_v2.txt")
        if (!buildLogFile.exists()) {
            buildLogFile.writeText("Compilation Log Date: 2026-05-29\nBuild target: debugConfig\nSuccessfully created APK: debug.apk\nVerification Completed.")
        }

    } catch (e: Throwable) {
        e.printStackTrace()
    }
}

fun createDefaultVirtualFileSystem(): VirtualFile {
    val root = VirtualFile("root", isDirectory = true)
    val storage = VirtualFile("storage", isDirectory = true)
    val emulated = VirtualFile("emulated", isDirectory = true)
    val zero = VirtualFile("0", isDirectory = true)

    root.children.add(storage)
    storage.children.add(emulated)
    emulated.children.add(zero)

    val folders = listOf(
        ".SHAREit",
        ".sketchware",
        ".sketchware_ide",
        ".SLOGAN",
        ".system_config",
        "Alarms",
        "Android",
        "AndroidCSProjects",
        "AndroidIDEProjects",
        "Audiobooks",
        "Codex",
        "ColorOS",
        "com.android.settings",
        "DCIM",
        "Delta",
        "Download",
        "PluginCache",
        "ObfuscatorTools"
    )

    for (folderName in folders) {
        zero.children.add(VirtualFile(folderName, isDirectory = true))
    }
    
    val androidCS = zero.children.find { it.name == "AndroidCSProjects" }!!
    val myCoolApp = VirtualFile("MyCoolApp", isDirectory = true)
    androidCS.children.add(myCoolApp)

    val appDir = VirtualFile("app", isDirectory = true)
    myCoolApp.children.add(appDir)

    val srcDir = VirtualFile("src", isDirectory = true)
    val resDir = VirtualFile("res", isDirectory = true)
    appDir.children.addAll(listOf(srcDir, resDir))

    val mainDir = VirtualFile("main", isDirectory = true)
    srcDir.children.add(mainDir)

    val javaDir = VirtualFile("java", isDirectory = true)
    val manifestFile = VirtualFile(
        "AndroidManifest.xml", 
        isDirectory = false,
        content = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.mycoolapp">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="MyCoolApp"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:theme="@style/Theme.MyCoolApp">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>"""
    )
    mainDir.children.addAll(listOf(javaDir, manifestFile))

    val comDir = VirtualFile("com", isDirectory = true)
    javaDir.children.add(comDir)
    val exampleDir = VirtualFile("example", isDirectory = true)
    comDir.children.add(exampleDir)
    val coolAppPkg = VirtualFile("mycoolapp", isDirectory = true)
    exampleDir.children.add(coolAppPkg)

    val mainActivityFile = VirtualFile(
        "MainActivity.kt",
        isDirectory = false,
        content = """package com.example.mycoolapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val textView = findViewById<TextView>(R.id.hello_text)
        textView.text = "Hello, MT Editor User!"
        
        textView.setOnClickListener {
            Toast.makeText(this, "Decompiling looks fine!", Toast.LENGTH_SHORT).show()
        }
    }
}"""
    )
    val utilsFile = VirtualFile(
        "DecompilerUtils.java",
        isDirectory = false,
        content = """package com.example.mycoolapp;

import java.io.File;
import java.io.InputStream;

/**
 * MT Editor Custom Utility to assist in loading and parsing dex instructions.
 */
public class DecompilerUtils {
    public static final String VERSION = "1.4.2_PRO";

    public static boolean decompileDex(File dexFile, File outputDir) {
        if (dexFile == null || !dexFile.exists()) {
            return false;
        }
        System.out.println("Unpacking Dex logic running...");
        return true;
    }
}"""
    )
    coolAppPkg.children.addAll(listOf(mainActivityFile, utilsFile))

    val obfuscatorTools = zero.children.find { it.name == "ObfuscatorTools" }!!
    obfuscatorTools.children.add(
        VirtualFile(
            "proguard-rules.pro",
            isDirectory = false,
            content = """# Professional Proguard rules for MT Editor builds
-keep class com.example.mycoolapp.DecompilerUtils {
    public static *** decompileDex(...);
}
-keepattributes Signature,Exceptions,InnerClasses
-dontwarn okio.**
-allowaccessmodification
-repackageclasses 'com.example.mycoolapp.internal'"""
        )
    )

    val sketchware = zero.children.find { it.name == ".sketchware" }!!
    sketchware.children.addAll(listOf(
        VirtualFile("config.json", isDirectory = false, content = """{
  "activeProject": "Project482",
  "enableSmaliDebugger": true,
  "theme": "navy_dark",
  "autoSignApk": true,
  "selectedCompilerVersion": "dx-1.16"
}"""),
        VirtualFile("bookmarks.db-journal", isDirectory = false, content = ""),
        VirtualFile("backup_code.txt", isDirectory = false, content = "const-string v0, \"Hooking successful\"\ninvoke-virtual {v0}, Lcom/example/Helper;->log(Ljava/lang/String;)V")
    ))

    val codex = zero.children.find { it.name == "Codex" }!!
    codex.children.addAll(listOf(
        VirtualFile(
            "TestActivity.smali",
            isDirectory = false,
            content = """.class public Lcom/example/TestActivity;
.super Landroid/app/Activity;
.source "TestActivity.java"


# direct methods
.method public constructor <init>()V
    .registers 1

    .line 8
    invoke-direct {p0}, Landroid/app/Activity;-><init>()V

    return-void
.end method


# virtual methods
.method protected onCreate(Landroid/os/Bundle;)V
    .registers 4

    .line 12
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    .line 13
    const-string v0, "Activity created with MT Editor!"

    const/4 v1, 0x1

    invoke-static {p0, v0, v1}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;

    move-result-object v0

    invoke-virtual {v0}, Landroid/widget/Toast;->show()V

    .line 14
    return-void
.end method"""
        ),
        VirtualFile(
            "smali_patch_demo.txt",
            isDirectory = false,
            content = """# Easy smali code to bypass signatures:
.method public static isPremium()Z
    .registers 1
    
    const/4 v0, 0x1
    return v0
.end method"""
        )
    ))

    val downloadDir = zero.children.find { it.name == "Download" }!!
    downloadDir.children.addAll(listOf(
        VirtualFile("MT_Manager_v2.14.apk", isDirectory = false, content = "[Binary APK Mock Resource] - Size 12.4 MB. Right-click or decompile on Extract APK helper to disassemble."),
        VirtualFile("vico_plugin_libs.zip", isDirectory = false, content = "ZIP archive compression layer. Contains .dex bytecode structures."),
        VirtualFile("build_log_v2.txt", isDirectory = false, content = "Compilation Log Date: 2026-05-29\nBuild target: debugConfig\nSuccessfully created APK: debug.apk\nVerification Completed.")
    ))

    return root
}
