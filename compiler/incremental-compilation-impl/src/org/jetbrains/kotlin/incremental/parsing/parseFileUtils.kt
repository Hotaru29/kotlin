/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.incremental.parsing

import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

import java.io.File
import java.util.*
import kotlin.collections.HashSet

fun classesFqNames(files: Set<File>): Set<String> {
    val existingKotlinFiles = files.filter { it.name.endsWith(".kt", ignoreCase = true) && it.isFile }

    val result = HashSet<String>()
    for (file in existingKotlinFiles) {
        KotlinSourceClassIdsReader(file).readClassFqNamesTo(result)
    }

    return result
}

private class KotlinSourceClassIdsReader(file: File) {
    private val lexer = KotlinLexer().apply {
        start(file.readText())
    }

    private var braceBalance = 0
    private var currentScope: FqName = FqName.ROOT
    private var lastSeenDeclaration: FqName = FqName.ROOT
    private var scopes = Stack<Scope>()

    private class Scope(val currentScope: FqName, val lastSeenDeclaration: FqName)

    private fun at(type: IElementType): Boolean = lexer.tokenType == type

    private fun end(): Boolean = lexer.tokenType == null

    private fun advance() {
        when {
            at(KtTokens.LBRACE) -> {
                scopes.add(Scope(currentScope, lastSeenDeclaration))
                currentScope = lastSeenDeclaration
                braceBalance++
            }
            at(KtTokens.RBRACE) -> {
                val prev = scopes.pop()
                currentScope = prev.currentScope
                lastSeenDeclaration = prev.lastSeenDeclaration
                braceBalance--
            }
        }
        lexer.advance()
    }

    private fun tokenText(): String = lexer.tokenText

    private fun atClass(): Boolean =
        lexer.tokenType in CLASS_KEYWORDS

    fun readClassFqNamesTo(result: HashSet<String>) {
        while (!end() && !at(KtTokens.PACKAGE_KEYWORD) && !atClass()) {
            advance()
        }
        if (at(KtTokens.PACKAGE_KEYWORD)) {
            advance()

            while (!end() && !at(KtTokens.IDENTIFIER) && !atClass()) {
                advance()
            }

            val packageName = StringBuilder()
            while (!end() && (at(KtTokens.IDENTIFIER) || at(KtTokens.DOT))) {
                packageName.append(tokenText())
                advance()
            }
            currentScope = FqName(packageName.toString())
            lastSeenDeclaration = currentScope
        }

        while (true) {
            while (!end() && !atClass()) {
                advance()
            }
            if (end()) break
            while (!end() && !at(KtTokens.IDENTIFIER)) {
                advance()
            }
            if (end()) break
            val fqName = currentScope.child(Name.identifier(tokenText()))
            result.add(fqName.asString())
            lastSeenDeclaration = fqName
        }
    }

    companion object {
        private val CLASS_KEYWORDS = setOf(KtTokens.CLASS_KEYWORD, KtTokens.INTERFACE_KEYWORD, KtTokens.OBJECT_KEYWORD)
    }
}