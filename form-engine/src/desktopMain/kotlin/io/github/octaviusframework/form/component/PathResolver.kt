package io.github.octaviusframework.form.component

import io.github.octaviusframework.form.control.base.ControlContext

/**
 * Resolves paths in the form hierarchy.
 * Handles absolute paths, relative ones (.) and going up (..), using the [ControlContext] tree.
 */
object PathResolver {
    const val SEPARATOR = "/"
    const val PARENT = ".."
    const val CURRENT = "."

    /**
     * Resolves [path] relative to the given context [currentContext].
     *
     * Rules:
     * - A path starting with "/" (e.g. "/name") -> absolute, from the root.
     * - A path without "/" and without ".", ".." (e.g. "name") -> treated as absolute.
     * - A path starting with "." (e.g. "./name") -> a sibling in the same container.
     * - A path starting with ".." (e.g. "../name") -> one level up in the container hierarchy.
     */
    fun resolvePath(path: String, currentContext: ControlContext): String {
        // Absolute path (starts with /)
        if (path.startsWith(SEPARATOR)) {
            return path.substring(1)
        }

        // A plain name without separators or modifiers is an absolute path
        if (!path.contains(SEPARATOR) &&
            !path.startsWith(CURRENT) &&
            !path.startsWith(PARENT)
        ) {
            return path
        }

        val targetSegments = path.split(SEPARATOR)
        var contextNode: ControlContext? = currentContext

        var i = 0
        while (i < targetSegments.size) {
            when (targetSegments[i]) {
                CURRENT -> {
                    // . means staying in the same container.
                    // currentContext.statePath already refers to the container,
                    // so there is no need to go up the ControlContext tree.
                }

                PARENT -> {
                    // .. means going up to the parent container
                    contextNode = contextNode?.parent
                }

                else -> {
                    // Reached the actual names of the path
                    break
                }
            }
            i++
        }

        val remainingPath = targetSegments.drop(i).joinToString(SEPARATOR)

        // Base path of the container we ended up in
        val basePath = contextNode?.statePath ?: ""

        return if (basePath.isEmpty()) {
            remainingPath
        } else if (remainingPath.isEmpty()) {
            basePath
        } else {
            "$basePath/$remainingPath"
        }
    }

    /**
     * Resolves [path], which may contain wildcards (*), returning all matching keys from [formState].
     *
     * Example: "publications[*]/details/status" -> ["publications[uuid1]/details/status", ...]
     */
    fun resolvePaths(path: String, currentContext: ControlContext, formState: FormState): List<String> {
        if (!path.contains("*")) {
            return listOf(resolvePath(path, currentContext))
        }

        // 1. Resolve the base (up to the first *)
        val basePathPart = path.substringBefore("*")

        val cleanBasePath = if (basePathPart.endsWith(SEPARATOR) && basePathPart.length > 1) {
            basePathPart.dropLast(1)
        } else {
            basePathPart
        }

        val resolvedBase = if (cleanBasePath.isEmpty() || cleanBasePath == SEPARATOR) {
            ""
        } else {
            resolvePath(cleanBasePath, currentContext)
        }

        // 2. Build a regex for the rest of the pattern
        val pattern = path.substring(basePathPart.length)

        val regexPattern = pattern
            .replace("/", "\\/")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("*", "[^\\/]+")

        val regexString = if (resolvedBase.isEmpty()) {
            "^$regexPattern$"
        } else {
            val escapedBase = Regex.escape(resolvedBase)
            // No separator when the pattern starts with one, or when the base ends with '[' (an opening index)
            val separator = if (pattern.startsWith(SEPARATOR) || basePathPart.endsWith("[")) "" else "\\/"
            "^$escapedBase$separator$regexPattern$"
        }

        val regex = regexString.toRegex()

        // 3. Filter the FormState keys
        return formState.getAllStates().keys.filter { it.matches(regex) }
    }
}
