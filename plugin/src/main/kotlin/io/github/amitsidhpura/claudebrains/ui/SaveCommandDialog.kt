package io.github.amitsidhpura.claudebrains.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import io.github.amitsidhpura.claudebrains.session.SavedCommands
import java.io.File
import java.nio.charset.StandardCharsets
import javax.swing.ButtonGroup
import javax.swing.JComponent

/**
 * "Save as command" (checklist 7.11): name, description and scope for a prompt the user sent, and
 * the message itself, editable — the bubble's text is a starting point, not the final command.
 * [taken] is every name the panel resolves before a custom file (built-ins, aliases, /btw); such a
 * file would never run, so the name is refused. The scope choice is remembered (application-level).
 */
class SaveCommandDialog(
    private val project: Project,
    text: String,
    private val taken: Set<String>,
) : DialogWrapper(project) {

    data class Saved(val name: String, val scope: String, val description: String, val argumentHint: String?)

    private val base = project.basePath
    private val nameField = JBTextField(SavedCommands.suggestName(text), 24)
    private val descField = JBTextField(40)
    private val bodyArea = JBTextArea(text, 8, 48).apply { lineWrap = true; wrapStyleWord = true }
    private val userRadio = JBRadioButton("All projects (" + tilde(SavedCommands.userDir()) + ")")
    private val projectRadio = JBRadioButton("This project (.claude/commands)")

    var saved: Saved? = null
        private set

    init {
        title = "Save as Command"
        setOKButtonText("Save")
        ButtonGroup().apply { add(userRadio); add(projectRadio) }
        projectRadio.isEnabled = base != null
        val wantProject = props.getValue(SCOPE_KEY) == "project" && base != null
        (if (wantProject) projectRadio else userRadio).isSelected = true
        init()
    }

    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Name: /", nameField)
        .addLabeledComponent("Description:", descField)
        .addTooltip("Shown under the name in the / menu. Optional.")
        .addLabeledComponent("Save to:", userRadio)
        .addComponentToRightColumn(projectRadio)
        .addLabeledComponent("Message:", JBScrollPane(bodyArea).apply { preferredSize = JBUI.size(460, 170) }, true)
        .addTooltip("Use \$ARGUMENTS where the text you type after the name should go.")
        .panel

    override fun getPreferredFocusedComponent(): JComponent = nameField

    override fun doValidate(): ValidationInfo? {
        val name = nameField.text.trim().removePrefix("/")
        return when {
            !SavedCommands.NAME.matches(name) ->
                ValidationInfo("Lowercase letters, digits, - and _, starting with a letter", nameField)
            name in taken -> ValidationInfo("/$name is a built-in command; pick another name", nameField)
            bodyArea.text.isBlank() -> ValidationInfo("The message is empty", bodyArea)
            else -> null
        }
    }

    override fun doOKAction() {
        val name = nameField.text.trim().removePrefix("/")
        val scope = if (projectRadio.isSelected && base != null) "project" else "user"
        val dir = if (scope == "project") SavedCommands.projectDir(base!!) else SavedCommands.userDir()
        val file = File(dir, "$name.md")
        if (file.exists() && Messages.showYesNoDialog(project,
                "/$name already exists in ${tilde(dir)}. Replace it?", "Replace Command",
                "Replace", "Cancel", Messages.getWarningIcon()) != Messages.YES) return
        val description = descField.text.trim()
        val body = bodyArea.text
        val ok = runCatching {
            dir.mkdirs()
            file.writeText(SavedCommands.fileBody(description, body), StandardCharsets.UTF_8)
        }.isSuccess
        if (!ok) {
            Messages.showErrorDialog(project, "Could not write ${file.path}", "Save as Command")
            return
        }
        props.setValue(SCOPE_KEY, scope)
        // a project file belongs in the Project tree straight away, not at the next VFS sync
        if (scope == "project") LocalFileSystem.getInstance().refreshIoFiles(listOf(file), true, false, null)
        saved = Saved(name, scope, description.replace(Regex("\\s+"), " "), SavedCommands.argumentHint(body))
        super.doOKAction()
    }

    private val props get() = PropertiesComponent.getInstance()

    private fun tilde(dir: File): String {
        val home = System.getProperty("user.home")
        return if (dir.path.startsWith(home)) "~" + dir.path.substring(home.length) else dir.path
    }

    companion object {
        private const val SCOPE_KEY = "claudeCode.saveCommandScope"
    }
}
