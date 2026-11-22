package org.octavius.modules.games.form.game

import io.github.octaviusframework.client.query.QueryFragment
import kotlinx.coroutines.launch
import org.octavius.dialog.DialogConfig
import org.octavius.dialog.GlobalDialogManager
import org.octavius.domain.game.GameStatus
import io.github.octaviusframework.form.FormSchemaBuilder
import io.github.octaviusframework.form.control.*
import io.github.octaviusframework.form.control.button.ButtonControl
import io.github.octaviusframework.form.control.button.ButtonType
import io.github.octaviusframework.form.control.container.SectionControl
import io.github.octaviusframework.form.control.number.BigDecimalControl
import io.github.octaviusframework.form.control.number.BigDecimalValidation
import io.github.octaviusframework.form.control.number.IntegerControl
import io.github.octaviusframework.form.control.number.IntegerValidation
import io.github.octaviusframework.form.control.primitive.CheckboxControl
import io.github.octaviusframework.form.control.primitive.StringControl
import io.github.octaviusframework.form.control.repeatable.RepeatableControl
import io.github.octaviusframework.form.control.repeatable.RepeatableValidation
import io.github.octaviusframework.form.control.selection.EnumControl
import io.github.octaviusframework.form.database.DatabaseControl
import org.octavius.localization.Tr
import org.octavius.modules.games.localization.GamesTr
import org.octavius.navigation.AppRouter
import java.math.BigDecimal

class GameFormSchemaBuilder : FormSchemaBuilder() {

    override fun defineControls(): Map<String, Control<*>> = mapOf(
        "id" to IntegerControl(null),
        "play_time_exists" to CheckboxControl(null),
        "ratings_exists" to CheckboxControl(null),
        "characters_exists" to CheckboxControl(null),
        // Sekcja podstawowych informacji
        "basic_info" to SectionControl(
            controls = mapOf(
                "name" to StringControl(
                    GamesTr.General.gameName(),
                    required = true
                ),
                "series" to DatabaseControl(
                    label = GamesTr.Form.series(),
                    query = QueryFragment("SELECT id, name FROM series"),
                    displayColumn = "name"
                ),
                "status" to EnumControl(
                    GamesTr.Form.status(),
                    GameStatus::class,
                    required = true
                ),
                "visible_characters_section" to CheckboxControl(
                    GamesTr.Form.visibleCharacterSection(),
                    required = true
                )
            ),
            collapsible = false,
            initiallyExpanded = true,
            columns = 1,
            label = GamesTr.Form.basicInfo()
        ),
        // Sekcja czasu gry
        "play_time_section" to SectionControl(
            controls = mapOf(
                "play_time_hours" to BigDecimalControl(
                    GamesTr.Form.playTimeHours(),
                    validationOptions = BigDecimalValidation(min = BigDecimal.ZERO, decimalPlaces = 2)
                ),
                "completion_count" to IntegerControl(
                    GamesTr.Form.playCount(),
                    required = true, // Automatycznie pomijana walidacja jak niewidoczna kontrolka
                    dependencies = mapOf(
                        "visible" to ControlDependency(
                            controlPath = "/basic_info/status",
                            value = listOf(GameStatus.Playing, GameStatus.Played),
                            dependencyType = DependencyType.Visible,
                            comparisonType = ComparisonType.OneOf
                        )
                    ),
                    validationOptions = IntegerValidation(min = 0)
                )
            ),
            collapsible = false,
            initiallyExpanded = true,
            columns = 2,
            label = GamesTr.Form.playTime(),
            dependencies = mapOf(
                "visible" to ControlDependency(
                    controlPath = "/basic_info/status",
                    value = listOf(
                        GameStatus.Playing,
                        GameStatus.Played,
                        GameStatus.WithoutTheEnd
                    ),
                    dependencyType = DependencyType.Visible,
                    comparisonType = ComparisonType.OneOf
                )
            )
        ),
        // Sekcja ocen
        "ratings_section" to SectionControl(
            controls = mapOf(
                "story_rating" to IntegerControl(
                    GamesTr.Form.storyRating(),
                    validationOptions = IntegerValidation(min = 0, max = 10)
                ),
                "gameplay_rating" to IntegerControl(
                    GamesTr.Form.gameplayRating(),
                    required = true,
                    validationOptions = IntegerValidation(min = 0, max = 10)
                ),
                "atmosphere_rating" to IntegerControl(
                    GamesTr.Form.atmosphereRating(),
                    validationOptions = IntegerValidation(min = 0, max = 10)
                )
            ),
            collapsible = true,
            initiallyExpanded = true,
            columns = 3,
            label = GamesTr.Form.ratings(),
            dependencies = mapOf(
                "visible" to ControlDependency(
                    controlPath = "/basic_info/status",
                    value = listOf(GameStatus.Played, GameStatus.WithoutTheEnd),
                    dependencyType = DependencyType.Visible,
                    comparisonType = ComparisonType.OneOf
                )
            )
        ),
        // Sekcja postaci
        "characters_section" to SectionControl(
            controls = mapOf(
                "has_distinctive_character" to CheckboxControl(
                    GamesTr.Form.distinctiveCharacters(),
                    required = true
                ),
                "has_distinctive_protagonist" to CheckboxControl(
                    GamesTr.Form.distinctiveProtagonist(),
                    required = true
                ),
                "has_distinctive_antagonist" to CheckboxControl(
                    GamesTr.Form.distinctiveAntagonist(),
                    required = true
                )
            ),
            collapsible = true,
            initiallyExpanded = true,
            columns = 1,
            label = GamesTr.Form.characters(),
            dependencies = mapOf(
                "visible" to ControlDependency(
                    controlPath = "/basic_info/visible_characters_section",
                    value = true,
                    dependencyType = DependencyType.Visible,
                    comparisonType = ComparisonType.Equals
                )
            )
        ),
        // Sekcja kategorii
        "categories" to RepeatableControl(
            rowControls = mapOf(
                "category" to DatabaseControl(
                    label = GamesTr.Form.category(1),
                    query = QueryFragment("SELECT id, name FROM games.categories"),
                    displayColumn = "name",
                    required = true
                )
            ),
            rowOrder = listOf("category"),
            label = GamesTr.Form.category(2),
            validationOptions = RepeatableValidation(
                minItems = 0,
                maxItems = 10,
                uniqueFields = listOf("category")
            )
        ),
        // Przyciski
        "save_button" to ButtonControl(
            text = Tr.Action.save(),
            actions = listOf(
                ControlAction {
                    if (trigger.triggerAction("save", true)) AppRouter.goBack()
                }
            ),
            buttonType = ButtonType.Filled
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
            actions = listOf(
                ControlAction {
                    AppRouter.goBack()
                }
            ),
            buttonType = ButtonType.Outlined
        )
    )

    override fun defineContentOrder(): List<String> = listOf(
        "basic_info",
        "play_time_section",
        "ratings_section",
        "characters_section",
        "categories"
    )

    override fun defineActionBarOrder(): List<String> = listOf(
        "cancel_button",
        "save_button",
        "delete_button"
    )

}