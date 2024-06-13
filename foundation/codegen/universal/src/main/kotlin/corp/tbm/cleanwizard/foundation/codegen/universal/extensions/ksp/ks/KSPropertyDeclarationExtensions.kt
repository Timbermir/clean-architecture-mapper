package corp.tbm.cleanwizard.foundation.codegen.universal.extensions.ksp.ks

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import corp.tbm.cleanwizard.foundation.codegen.universal.dtoRegex
import corp.tbm.cleanwizard.foundation.codegen.universal.extensions.firstCharUppercase
import corp.tbm.cleanwizard.foundation.codegen.universal.processor.ProcessorOptions.dataClassGenerationPattern
import corp.tbm.cleanwizard.foundation.codegen.universal.processor.ProcessorOptions.domainOptions

inline val KSPropertyDeclaration.name
    get() = simpleName.asString()

fun KSPropertyDeclaration.getParameterName(packageName: String): String {
    val resolvedType = type.resolve()

    return when {

        resolvedType.isClassMappable ->
            resolvedType.toClassName().simpleName.replace(
                dtoRegex,
                ""
            ) + dataClassGenerationPattern.findRightModelType(packageName).suffix

        resolvedType.isListMappable -> {
            resolvedType.arguments.first().type?.resolve()?.toClassName()?.simpleName?.replace(
                dtoRegex,
                ""
            ) + dataClassGenerationPattern.findRightModelType(packageName).suffix
        }

        else -> name
    }
}

fun KSPropertyDeclaration.getQualifiedPackageNameBasedOnParameterName(
    packageName: String
): String {

    val relevantParts = packageName.split(".").toMutableList()

    return dataClassGenerationPattern.getQualifiedPackageName(relevantParts, type.resolve())
}

@OptIn(KspExperimental::class)
fun KSPropertyDeclaration.determineParameterType(
    symbol: KSClassDeclaration,
    resolver: Resolver,
    packageName: String
): TypeName {

    val type = type.resolve()

    return when {

        annotations.filter { it.isEnum }.toList().isNotEmpty() -> {
            val filteredAnnotations =
                annotations.filter { it.isEnum }
                    .toList().first()

            val enumPackageName =
                "${dataClassGenerationPattern.generatePackageName(symbol, domainOptions)}.enums"

            val declarations = resolver.getDeclarationsFromPackage(
                enumPackageName
            ).toList()

            val enum =
                declarations.firstOrNull {
                    it.name == this.name.firstCharUppercase() || it.name == filteredAnnotations.arguments.first { valueArgument ->
                        valueArgument.simpleName == "enumName"
                    }.simpleName
                }

            ClassName(enum?.packageName?.asString().toString(), enum.name)
        }

        type.isClassMappable -> ClassName(
            getQualifiedPackageNameBasedOnParameterName(packageName),
            getParameterName(packageName).firstCharUppercase()
        )

        type.isListMappable ->
            List::class.asClassName()
                .parameterizedBy(
                    ClassName(
                        getQualifiedPackageNameBasedOnParameterName(packageName),
                        getParameterName(packageName).firstCharUppercase()
                    )
                )

        else -> type.toTypeName()
    }
}