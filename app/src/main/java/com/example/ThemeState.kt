package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppTheme {
    CLASSIC_DARK,
    MIDNIGHT_BLACK,
    DEEP_BLUE
}

enum class AppLanguage {
    ENGLISH,
    ARABIC
}

object ThemeState {
    var currentTheme by mutableStateOf(AppTheme.CLASSIC_DARK)
    var currentLanguage by mutableStateOf(AppLanguage.ARABIC) // Default to Arabic as requested!

    fun getTranslation(key: String): String {
        val sheet = if (currentLanguage == AppLanguage.ARABIC) ArabicStrings else EnglishStrings
        return sheet[key] ?: key
    }

    private val EnglishStrings = mapOf(
        "app_title" to "MT Editor Pro",
        "root_dir" to "Root Directory",
        "storage_dir" to "Storage Directory",
        "local_section" to "Local",
        "tools_section" to "Tools",
        "plugin_manager" to "Plugin Manager",
        "remote_manage" to "Remote Management",
        "color_picker" to "Screen Color Picker",
        "extract_apk" to "Extract APK",
        "text_editor" to "Text Editor",
        "terminal_sim" to "Terminal Simulator",
        "activity_record" to "Activity Record",
        "smali_inst" to "Smali Instructions",
        "about" to "About Professional MT",
        "cancel" to "Cancel",
        "confirm" to "Confirm",
        "save" to "Save",
        "edit" to "Edit",
        "copy" to "Copy",
        "move" to "Move",
        "delete" to "Delete",
        "rename" to "Rename",
        "new_file" to "New File",
        "new_folder" to "New Folder",
        "search" to "Search...",
        "language" to "اللغة العربية",
        "theme" to "Theme",
        "folders" to "Folders",
        "files" to "Files",
        "disk" to "Disk",
        "copy_confirm" to "Do you want to copy this item to the opposite pane?",
        "move_confirm" to "Do you want to move this item to the opposite pane?",
        "delete_confirm" to "Confirm deleting this item permanently?",
        "unimplemented_title" to "Feature Notification",
        "unimplemented_msg" to "This feature is fully simulated inside MT Pro.",
        "success" to "Success",
        "active" to "Active",
        "inactive" to "Inactive",
        "port" to "Port",
        "server_status" to "Server Status",
        "copy_hex" to "Copy HEX",
        "extracted_files" to "Extracted Files",
        "decompile_smali" to "Decompile Smali",
        "classes_unpacked" to "Classes Unpacked",
        "sign_apk" to "Sign APK",
        "signing_complete" to "APK Signing Complete!",
        "run_cmd" to "Run Command",
        "interactive_terminal" to "Interactive Terminal",
        "editor_assistant" to "AI Copilot Analysis",
        "ask_gemini_hint" to "Ask Gemini AI dynamic programming helpers...",
        "smali_opcodes" to "Smali Opcodes"
    )

    private val ArabicStrings = mapOf(
        "app_title" to "إم تي إدیتور برو",
        "root_dir" to "مجلد الجيلبروت الأساسي (Root)",
        "storage_dir" to "مساحة التخزين المشتركة (Storage)",
        "local_section" to "المحلي",
        "tools_section" to "الأدوات البرمجية",
        "plugin_manager" to "مدير الإضافات (Plugins)",
        "remote_manage" to "الإدارة عن بعد (SFTP)",
        "color_picker" to "ملتقط الألوان الذكي",
        "extract_apk" to "استخراج وتعديل APK",
        "text_editor" to "محرر الأكواد والنصوص",
        "terminal_sim" to "محاكي الطرفية (Terminal)",
        "activity_record" to "سجل الأنشطة والأحداث",
        "smali_inst" to "موسوعة أوامر Smali",
        "about" to "حول محرر MT المحترف",
        "cancel" to "إلغاء",
        "confirm" to "تأكيد",
        "save" to "حفظ التغييرات",
        "edit" to "تعديل",
        "copy" to "نسخ للجهة المقابلة",
        "move" to "نقل للجهة المقابلة",
        "delete" to "حذف نهائي",
        "rename" to "تغيير الاسم",
        "new_file" to "إنشاء ملف جديد",
        "new_folder" to "إنشاء مجلد جديد",
        "search" to "بحث سريع...",
        "language" to "English UI",
        "theme" to "التأثير البصري",
        "folders" to "مجلدات",
        "files" to "ملفات",
        "disk" to "القرص",
        "copy_confirm" to "هل تريد نسخ هذا العنصر إلى نافذة العرض المقابلة؟",
        "move_confirm" to "هل تريد نقل هذا العنصر إلى نافذة العرض المقابلة؟",
        "delete_confirm" to "هل تؤكد حذف هذا العنصر نهائياً وللأبد؟",
        "unimplemented_title" to "تنبيه النظام",
        "unimplemented_msg" to "هذه الميزة تعمل بشكل تفاعلي بالكامل داخل التطبيق.",
        "success" to "تمت العملية بنجاح",
        "active" to "نشط ومفعل",
        "inactive" to "متوقف",
        "port" to "المنفذ",
        "server_status" to "حالة خادم الاتصال",
        "copy_hex" to "نسخ رمز اللون HEX",
        "extracted_files" to "الملفات المستخرجة",
        "decompile_smali" to "تفكيك الكود إلى Smali",
        "classes_unpacked" to "تفكيك كلاسات dex",
        "sign_apk" to "توقيع حزمة الـ APK",
        "signing_complete" to "تم توقيع ملف الـ APK بنجاح وبشكل متوافق!",
        "run_cmd" to "تشغيل الأمر",
        "interactive_terminal" to "شاشة الطرفية التفاعلية",
        "editor_assistant" to "محلل الذكاء الاصطناعي (Gemini)",
        "ask_gemini_hint" to "اسأل كود مساعد جيميناي للبرمجة والهندسة العكسية...",
        "smali_opcodes" to "جداول أوامر السمالي (Smali)"
    )
}
