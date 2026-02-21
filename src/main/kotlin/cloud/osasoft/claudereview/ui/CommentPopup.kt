package cloud.osasoft.claudereview.ui

import cloud.osasoft.claudereview.model.LineComment
import cloud.osasoft.claudereview.model.ReviewModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke

object CommentPopup {
    fun show(
        editor: Editor,
        lineNumber: Int,
        filePath: String,
        existingComment: LineComment?,
        model: ReviewModel,
        onDone: () -> Unit
    ) {
        val panel = JPanel(BorderLayout(8, 8))
        panel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        val label = JLabel("Comment on line $lineNumber:")
        panel.add(label, BorderLayout.NORTH)

        val textArea = JBTextArea(existingComment?.text ?: "", 4, 40)
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        panel.add(JBScrollPane(textArea), BorderLayout.CENTER)

        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
        }

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, textArea)
            .setTitle("Review Comment")
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .createPopup()

        val saveAction = {
            val text = textArea.text.trim()
            if (text.isNotEmpty()) {
                existingComment?.let { model.removeComment(it) }
                model.addComment(LineComment(filePath, lineNumber, text))
            } else if (existingComment != null) {
                // Clearing the text acts as a delete
                model.removeComment(existingComment)
            }
            popup.cancel()
            onDone()
        }

        val deleteAction = {
            existingComment?.let { model.removeComment(it) }
            popup.cancel()
            onDone()
        }

        val saveButton = JButton("Save").apply {
            addActionListener { saveAction() }
        }
        buttonPanel.add(saveButton)

        if (existingComment != null) {
            buttonPanel.add(Box.createHorizontalStrut(8))
            val deleteButton = JButton("Delete").apply {
                addActionListener { deleteAction() }
            }
            buttonPanel.add(deleteButton)
        }

        panel.add(buttonPanel, BorderLayout.SOUTH)

        // Ctrl+Enter shortcut to save
        val ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK)
        textArea.getInputMap(JComponent.WHEN_FOCUSED).put(ctrlEnter, "save-comment")
        textArea.actionMap.put("save-comment", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                saveAction()
            }
        })

        panel.preferredSize = Dimension(400, 200)

        // Show popup near the line in the editor
        val editorComponent = editor.component
        if (!editorComponent.isShowing) {
            // Component not yet visible; fall back to showing relative to editor
            popup.showInFocusCenter()
            return
        }

        val logicalPos = LogicalPosition(lineNumber - 1, 0)
        val point = editor.logicalPositionToXY(logicalPos)
        val screenPoint = editorComponent.locationOnScreen
        popup.showInScreenCoordinates(
            editorComponent,
            Point(screenPoint.x + point.x + 60, screenPoint.y + point.y)
        )
    }
}
