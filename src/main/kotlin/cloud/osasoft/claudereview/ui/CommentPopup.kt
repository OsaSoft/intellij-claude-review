package cloud.osasoft.claudereview.ui

import cloud.osasoft.claudereview.model.CommentSeverity
import cloud.osasoft.claudereview.model.LineComment
import cloud.osasoft.claudereview.model.WorktreeState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
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
        state: WorktreeState,
        screenPoint: Point? = null,
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

        // Severity selector row + buttons stacked in SOUTH
        val southPanel = JPanel(BorderLayout(0, 4))

        val severityPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        severityPanel.add(JLabel("Severity: "))
        val severityCombo = JComboBox(CommentSeverity.entries.toTypedArray())
        severityCombo.selectedItem = existingComment?.severity ?: CommentSeverity.ISSUE
        severityPanel.add(severityCombo)
        southPanel.add(severityPanel, BorderLayout.NORTH)

        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
        }
        southPanel.add(buttonPanel, BorderLayout.SOUTH)

        panel.add(southPanel, BorderLayout.SOUTH)

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
            val severity = severityCombo.selectedItem as CommentSeverity
            if (text.isNotEmpty()) {
                existingComment?.let { state.removeComment(it) }
                state.addComment(LineComment(filePath, lineNumber, text, severity))
            } else if (existingComment != null) {
                // Clearing the text acts as a delete
                state.removeComment(existingComment)
            }
            popup.cancel()
            onDone()
        }

        val deleteAction = {
            existingComment?.let { state.removeComment(it) }
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

        // Ctrl+Enter shortcut to save
        val ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK)
        textArea.getInputMap(JComponent.WHEN_FOCUSED).put(ctrlEnter, "save-comment")
        textArea.actionMap.put("save-comment", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                saveAction()
            }
        })

        panel.preferredSize = Dimension(400, 230)

        // Show popup near the line in the editor
        val editorComponent = editor.component
        if (!editorComponent.isShowing) {
            popup.showInFocusCenter()
            return
        }

        if (screenPoint != null) {
            popup.showInScreenCoordinates(editorComponent, Point(screenPoint.x + 20, screenPoint.y))
        } else {
            popup.showInFocusCenter()
        }
    }
}
