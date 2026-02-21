package cloud.osasoft.claudereview.vfs

import com.intellij.testFramework.LightVirtualFile

class ReviewVirtualFile : LightVirtualFile("Claude Review", "") {
    init {
        isWritable = false
    }
}
