package io.github.octaviusframework.form.database

import io.github.octaviusframework.client.OctaviusClient
import io.github.octaviusframework.client.DataResult
import io.github.octaviusframework.form.component.PathResolver
import io.github.octaviusframework.form.database.localization.FormDatabaseTr
import io.github.octaviusframework.identifier.CaseConvention
import io.github.octaviusframework.identifier.CaseConverter
import org.octavius.dialog.ErrorDialogConfig
import org.octavius.dialog.GlobalDialogManager
import org.octavius.localization.Tr

// --- Main DSL classes ---

internal data class FieldMapping(val controlName: String, val dbColumn: String)
internal data class ExistenceFlag(val controlName: String, val checkColumn: String)

abstract class MappingContainer internal constructor() {
    internal val relations = mutableListOf<RelationMapping>()

    /**
     * @param controlName Control name or path to it, e.g. `"basic_info/name"` for a field in a section.
     * @param dbColumn Column; defaults to the last path segment in snake_case.
     */
    fun map(controlName: String, dbColumn: String? = null) {
        val finalDbColumn = dbColumn ?: CaseConverter.convert(
            controlName.substringAfterLast(PathResolver.SEPARATOR),
            CaseConvention.CAMEL_CASE,
            CaseConvention.SNAKE_CASE_LOWER
        )
        relations.add(SimpleMapping(FieldMapping(controlName, finalDbColumn)))
    }

    fun mapOneToOne(block: OneToOneMappingBuilder.() -> Unit) {
        val builder = OneToOneMappingBuilder().apply(block)
        builder.validate()
        relations.add(OneToOneMapping(builder.existenceFlag, builder.fromTable, builder.joinCondition, builder.relations))
    }

    fun mapRelatedList(controlName: String, block: RelatedDataMappingBuilder.() -> Unit) {
        val builder = RelatedDataMappingBuilder().apply(block)
        builder.validate()
        relations.add(RelatedDataMapping(controlName, builder))
    }
}

abstract class BaseTableMappingBuilder internal constructor() : MappingContainer() {
    protected lateinit var tableName: String
    protected lateinit var tableAlias: String

    internal val fromTable: String
        get() = if (::tableName.isInitialized) "$tableName $tableAlias" else ""

    fun from(tableName: String, alias: String) {
        this.tableName = tableName
        this.tableAlias = alias
    }

    protected fun validateBase() {
        check(::tableName.isInitialized) { "The `from` clause is required in a mapping." }
    }
}

class OneToOneMappingBuilder internal constructor() : BaseTableMappingBuilder() {
    internal lateinit var joinCondition: String
    internal var existenceFlag: ExistenceFlag? = null
        private set

    fun existenceFlag(controlName: String, checkColumn: String) {
        this.existenceFlag = ExistenceFlag(controlName, checkColumn)
    }

    fun on(condition: String) {
        this.joinCondition = condition
    }

    internal fun validate() {
        validateBase()
        check(::joinCondition.isInitialized) { "The `on` clause is required in a one-to-one mapping." }
    }
}

class RelatedDataMappingBuilder internal constructor() : BaseTableMappingBuilder() {
    private lateinit var linkColumn: String
    private var parentColumn: String = "@id"
    internal var orderBy: String? = null
        private set

    fun linkedBy(foreignKeyColumn: String, parentColumn: String = "@id") {
        this.linkColumn = foreignKeyColumn
        this.parentColumn = parentColumn
    }

    /**
     * Order of the list's rows. Without it PostgreSQL returns them in any order, and that order can change
     * after every UPDATE.
     *
     * @param expression Body of the `ORDER BY`, e.g. `"p.publication_type, p.id DESC"`.
     */
    fun orderBy(expression: String) {
        this.orderBy = expression
    }

    internal fun validate() {
        validateBase()
        check(::linkColumn.isInitialized) { "The `linkedBy` clause is required in a related-list mapping." }
    }

    internal fun buildWhereClause(): String {
        return "$linkColumn = $parentColumn"
    }
}

// --- Internal representations ---

internal sealed class RelationMapping
internal data class SimpleMapping(val mapping: FieldMapping) : RelationMapping()
internal data class OneToOneMapping(val existenceFlag: ExistenceFlag?, val table: String, val on: String, val relations: List<RelationMapping>) : RelationMapping()
internal data class RelatedDataMapping(val controlName: String, val builder: RelatedDataMappingBuilder) : RelationMapping()


// --- SQL building helper ---

// A control name can be a path ("basic_info/name"), so the alias has to be quoted. The form spreads such
// keys over its sections by itself, when it initializes the states.
private fun alias(controlName: String) = "\"$controlName\""

internal class QueryScope(
    val isTopLevel: Boolean
) {
    val fieldExpressions = mutableListOf<String>()
    val joins = mutableListOf<String>()

    fun processRelations(relations: List<RelationMapping>) {
        relations.forEach { rel ->
            when (rel) {
                is SimpleMapping -> {
                    if (isTopLevel) {
                        fieldExpressions.add("${rel.mapping.dbColumn} AS ${alias(rel.mapping.controlName)}")
                    } else {
                        fieldExpressions.add("'${rel.mapping.controlName}', ${rel.mapping.dbColumn}")
                    }
                }
                is OneToOneMapping -> {
                    joins.add("LEFT JOIN ${rel.table} ON ${rel.on}")
                    rel.existenceFlag?.let { flag ->
                        val expr = "CASE WHEN ${flag.checkColumn} IS NOT NULL THEN TRUE ELSE FALSE END"
                        if (isTopLevel) {
                            fieldExpressions.add("$expr AS ${alias(flag.controlName)}")
                        } else {
                            fieldExpressions.add("'${flag.controlName}', $expr")
                        }
                    }
                    processRelations(rel.relations)
                }
                is RelatedDataMapping -> {
                    val builder = rel.builder
                    val subScope = QueryScope(isTopLevel = false)
                    subScope.processRelations(builder.relations)

                    val mapEntries = subScope.fieldExpressions.joinToString(",\n                            ")
                    val subJoins = subScope.joins.joinToString(" ")
                    val orderBy = builder.orderBy?.let { "ORDER BY $it" } ?: ""

                    // ROW(key, value, ...) reads as Map<String, Any?>, and an array of such records as
                    // List<Map<String, Any?>> - and the values go through the full converter chain, so an
                    // enum comes back as an enum, not as text. ARRAY(subquery) keeps the ORDER BY order.
                    val subquery = """
                        ARRAY(
                            SELECT ROW(
                                $mapEntries
                            )
                            FROM ${builder.fromTable} $subJoins
                            WHERE ${builder.buildWhereClause()}
                            $orderBy
                        )::record[]
                    """.trimIndent()

                    if (isTopLevel) {
                        fieldExpressions.add("($subquery) AS ${alias(rel.controlName)}")
                    } else {
                        fieldExpressions.add("'${rel.controlName}', ($subquery)")
                    }
                }
            }
        }
    }
}


// --- Main builder ---

class DataLoaderBuilder internal constructor(private val db: OctaviusClient) : MappingContainer() {
    private lateinit var mainTableName: String
    private lateinit var mainTableAlias: String
    private var idColumn: String = "id"

    private val mainTable: String
    get() = if (::mainTableName.isInitialized) "$mainTableName $mainTableAlias" else ""

    fun from(tableName: String, alias: String) {
        this.mainTableName = tableName
        this.mainTableAlias = alias
    }

    fun idColumn(name: String) {
        this.idColumn = name
    }

    /**
     * Loads the record with [id] in a single query.
     *
     * @return Values under control names, or an empty map when [id] is `null` (new record). `null` when
     *   loading failed: the database reported an error or there is no record with that id. A dialog shows
     *   the reason.
     */
    internal fun execute(id: Any?): Map<String, Any?>? {
        check(::mainTableName.isInitialized) { "Main table must be defined using from()" }
        if (id == null) return emptyMap()

        val topScope = QueryScope(isTopLevel = true)
        topScope.processRelations(relations)

        if (topScope.fieldExpressions.isEmpty()) return emptyMap()

        val joinsStr = topScope.joins.joinToString(" ")

        // Assemble the final, single query
        val query = db.select(*topScope.fieldExpressions.toTypedArray())
            .from("$mainTable $joinsStr".trim())
            .where("$mainTableAlias.$idColumn = @id")
            .asResult().fetchObject<Map<String, Any?>>("id" to id)

        return when (query) {
            is DataResult.Success -> query.value ?: run {
                GlobalDialogManager.show(ErrorDialogConfig(Tr.Dialog.error(), FormDatabaseTr.Load.notFound(id)))
                null
            }
            is DataResult.Failure -> {
                GlobalDialogManager.show(ErrorDialogConfig(query.error))
                null
            }
        }
    }
}