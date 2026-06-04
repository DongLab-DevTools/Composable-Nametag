package com.donglab.compose.debug.compiler

class SkipConfig(
    packages: List<String>,
    namePatterns: List<String>,
    annotations: List<String>,
) {
    val packages: List<String> = packages
    val nameRegexes: List<Regex> = namePatterns.map { it.toRegex() }
    val annotations: List<String> = annotations
}
