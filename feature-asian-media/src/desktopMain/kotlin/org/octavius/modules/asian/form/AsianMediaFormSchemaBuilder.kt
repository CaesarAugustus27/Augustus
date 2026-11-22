package org.octavius.modules.asian.form

import kotlinx.coroutines.launch
import org.octavius.dialog.DialogConfig
import org.octavius.dialog.GlobalDialogManager
import org.octavius.domain.asian.PublicationLanguage
import org.octavius.domain.asian.PublicationStatus
import org.octavius.domain.asian.PublicationType
import io.github.octaviusframework.form.component.FormSchemaBuilder
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.type.button.ButtonControl
import io.github.octaviusframework.form.control.type.button.ButtonType
import io.github.octaviusframework.form.control.type.collection.StringListControl
import io.github.octaviusframework.form.control.type.collection.StringListValidation
import io.github.octaviusframework.form.control.type.container.SectionControl
import io.github.octaviusframework.form.control.type.number.IntegerControl
import io.github.octaviusframework.form.control.type.primitive.CheckboxControl
import io.github.octaviusframework.form.control.type.repeatable.RepeatableControl
import io.github.octaviusframework.form.control.type.repeatable.RepeatableValidation
import io.github.octaviusframework.form.control.type.selection.EnumControl
import org.octavius.localization.Tr
import org.octavius.modules.asian.localization.AsianMediaTr
import org.octavius.navigation.AppRouter

class AsianMediaFormSchemaBuilder : FormSchemaBuilder() {

    override fun defineActionBarOrder(): List<String> = listOf("cancel_button", "save_button", "delete_button")
    override fun defineContentOrder(): List<String> = listOf("title_info", "publications")

    override fun defineControls(): Map<String, Control<*>> = mapOf(
        "id" to IntegerControl(
            null
        ),
        "title_info" to SectionControl(
            controls = mapOf(
                "titles" to StringListControl(
                    AsianMediaTr.Form.titles(),
                    required = true,
                    validationOptions = StringListValidation(minItems = 1)
                ),
                "language" to EnumControl(
                    AsianMediaTr.Form.originalLanguage(),
                    PublicationLanguage::class,
                    required = true
                )
            ),
            collapsible = false,
            initiallyExpanded = true,
            columns = 2,
            label = AsianMediaTr.Form.titleInfo()
        ),

        // Sekcja publikacji - używa RepeatableControl
        "publications" to RepeatableControl(
            rowControls = createPublicationControls(),
            rowOrder = listOf(
                "publication_type",
                "status",
                "track_progress",
                "volumes",
                "translated_volumes",
                "chapters",
                "translated_chapters",
                "original_completed"
            ),
            validationOptions = RepeatableValidation(
                uniqueFields = listOf("publication_type"),
                minItems = 1,
                maxItems = 7
            ),
            label = AsianMediaTr.Form.publications()
        ),
        // Przyciski
        "save_button" to ButtonControl(
            text = Tr.Action.save(),
            buttonType = ButtonType.Filled,
            actions = listOf(
                ControlAction {
                    if (trigger.triggerAction("save", true)) AppRouter.goBack()
                }
            )
        ),
        "delete_button" to ButtonControl(
            text = Tr.Action.remove(),
            buttonType = ButtonType.Filled,
            dependencies = mapOf(
                "visible" to ControlDependency(
                    controlPath = "id",
                    value = null,
                    dependencyType = DependencyType.Visible,
                    comparisonType = ComparisonType.NotEquals
                )
            ),
            actions = listOf(
                ControlAction {
                    GlobalDialogManager.show(
                        DialogConfig(
                            title = Tr.Action.confirm(),
                            text = Tr.Dialog.confirmDelete(),
                            onDismiss = { GlobalDialogManager.dismiss() },
                            confirmButtonText = Tr.Dialog.yes(),
                            onConfirm = {
                                coroutineScope.launch {
                                    if (trigger.triggerAction("delete", false)) AppRouter.goBack()
                                    GlobalDialogManager.dismiss()
                                }
                            }
                        )
                    )
                }
            )
        ),
        "cancel_button" to ButtonControl(
            text = Tr.Action.cancel(),
            buttonType = ButtonType.Outlined,
            actions = listOf(
                ControlAction {
                    AppRouter.goBack()
                }
            )
        )
    )


    private fun createPublicationControls(): Map<String, Control<*>> {
        return mapOf(
            "id" to IntegerControl(null),
            "publication_type" to EnumControl(
                AsianMediaTr.Form.publicationType(),
                PublicationType::class,
                required = true,
                actions = listOf(
                    ControlAction(executeOnInit = true) {
                        val isWebtoon = sourceValue == PublicationType.Webtoon
                        if (isWebtoon) {
                            updateLabel("./volumes", AsianMediaTr.Form.seasonsCount())
                            updateLabel("./translated_volumes", AsianMediaTr.Form.translatedSeasons())
                        } else {
                            updateLabel("./volumes", AsianMediaTr.Form.volumeCount())
                            updateLabel("./translated_volumes", AsianMediaTr.Form.translatedVolumes())
                        }
                    }
                )
            ),
            "status" to EnumControl(
                AsianMediaTr.Form.readingStatus(),
                PublicationStatus::class,
                required = true,
                actions = listOf(
                    ControlAction {
                        val shouldBeChecked = (sourceValue in listOf(
                            PublicationStatus.PlanToRead, PublicationStatus.Reading,
                            PublicationStatus.Completed
                        ))
                        updateControl("./track_progress", shouldBeChecked)
                    }
                )
            ),
            "track_progress" to CheckboxControl(
                AsianMediaTr.Form.trackProgress(),
                required = true
            ),
            "volumes" to IntegerControl(
                AsianMediaTr.Form.volumeCount(),
                dependencies = visibleWhenTrackProgress()
            ),
            "translated_volumes" to IntegerControl(
                AsianMediaTr.Form.translatedVolumes(),
                dependencies = visibleWhenTrackProgress()
            ),
            "chapters" to IntegerControl(
                AsianMediaTr.Form.chapterCount(),
                dependencies = visibleWhenTrackProgress()
            ),
            "translated_chapters" to IntegerControl(
                AsianMediaTr.Form.translatedChapters(),
                dependencies = visibleWhenTrackProgress()
            ),
            "original_completed" to CheckboxControl(
                AsianMediaTr.Form.originalCompleted(),
                required = true,
                dependencies = visibleWhenTrackProgress()
            )
        )
    }

    private fun visibleWhenTrackProgress(): Map<String, ControlDependency<Boolean>> = mapOf(
        "visible" to ControlDependency(
            controlPath = "./track_progress",
            value = true,
            dependencyType = DependencyType.Visible,
            comparisonType = ComparisonType.Equals,
        )
    )

}