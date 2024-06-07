package corp.tbm.cleanarchitecturemapper.processor

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toKModifier
import corp.tbm.cleanarchitecturemapper.foundation.annotations.DTO
import corp.tbm.cleanarchitecturemapper.foundation.codegen.kotlinpoet.allowedDataClassPropertiesModifiers
import corp.tbm.cleanarchitecturemapper.foundation.codegen.universal.dtoRegex
import corp.tbm.cleanarchitecturemapper.foundation.codegen.universal.exceptions.references.PropertyAlreadyMarkedWithEnumException
import corp.tbm.cleanarchitecturemapper.foundation.codegen.universal.extensions.firstCharLowercase
import corp.tbm.cleanarchitecturemapper.foundation.codegen.universal.extensions.ksp.getAnnotatedSymbols
import corp.tbm.cleanarchitecturemapper.foundation.codegen.universal.extensions.ksp.ks.*
import corp.tbm.cleanarchitecturemapper.foundation.codegen.universal.extensions.ksp.log
import corp.tbm.cleanarchitecturemapper.foundation.codegen.universal.processor.ProcessorOptions
import corp.tbm.cleanarchitecturemapper.foundation.codegen.universal.processor.ProcessorOptions.domainOptions
import corp.tbm.cleanarchitecturemapper.foundation.codegen.universal.processor.ProcessorOptions.dtoOptions
import corp.tbm.cleanarchitecturemapper.foundation.codegen.universal.processor.ProcessorOptions.uiOptions
import corp.tbm.cleanarchitecturemapper.visitors.enums.EnumGenerateVisitor
import corp.tbm.cleanwizard.DTOMapper
import kotlinx.serialization.SerialName
import java.io.OutputStreamWriter

const val PARAMETER_SEPARATOR = ", \n"
const val PARAMETER_PREFIX = "\n"

class DTOProcessor(
    private val codeGenerator: CodeGenerator,
    processorOptions: Map<String, String>,
    val logger: KSPLogger
) : SymbolProcessor {

    init {
        logger.log(processorOptions.toString())
        ProcessorOptions.generateConfigs(processorOptions)
    }

    private var processingRound = 0

    private val enumGenerateVisitor by lazy {
        EnumGenerateVisitor(codeGenerator, logger)
    }

    private val statementListFormatMapping: (functionName: String, packageName: String, properties: List<KSPropertyDeclaration>) -> String =
        { mappingFunctionName, packageName, properties ->
            "return %T(${
                properties.map { it }
                    .joinToString(
                        separator = "$PARAMETER_SEPARATOR    ",
                        prefix = "$PARAMETER_PREFIX    "
                    )
                    { currentProperty ->
                        val filteredProperties = properties.filter { it.name == currentProperty.name }
                        when {

                            filteredProperties.any { it.type.resolve().isClassMappable } ->
                                "${currentProperty.name}.$mappingFunctionName()"

                            filteredProperties.any { it.type.resolve().isListMappable } ->
                                "${currentProperty.name}.map { ${
                                    currentProperty.getParameterName(
                                        packageName
                                    ).firstCharLowercase()
                                } -> ${
                                    currentProperty.getParameterName(packageName).firstCharLowercase()
                                }.$mappingFunctionName() }"

                            else -> currentProperty.name
                        }
                    }
            }\n)"
        }

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        processingRound++

        val symbols = resolver.getAnnotatedSymbols<KSClassDeclaration>(DTO::class.qualifiedName!!)
        logger.log(processingRound)

        symbols.forEach { symbol ->
//            TypeSpec.interfaceBuilder("DTOMapper").addType().build()

            if (symbol.getDeclaredProperties()
                    .any { property -> property.annotations.filter { it.name.endsWith("Enum") }.toList().isNotEmpty() }
            ) {
                if (processingRound == 1) {
                    symbol.getDeclaredProperties().forEach { property ->
                        val packageName = "${
                            symbol.packageName.asString().split(".").dropLast(1)
                                .joinToString(".") + "." + symbol.simpleName.asString().replace(dtoRegex, "")
                                .firstCharLowercase()
                        }.model"

                        val propertyAnnotations = property.annotations.filter { it.name.endsWith("Enum") }.toList()

                        if (propertyAnnotations.isNotEmpty()) {

                            if (propertyAnnotations.size >= 2) {
                                throw PropertyAlreadyMarkedWithEnumException(
                                    "Property [${property.name}] in \n[${property.parentDeclaration?.fullyQualifiedName}] has the following $propertyAnnotations enums annotations. Only 1 is allowed"
                                )
                            }

                            property.accept(enumGenerateVisitor, "$packageName.enums")
                        }
                    }
                    return symbols.filter { it.validate() }
                }
            }

            generateClass(resolver, symbol, domainOptions.suffix)

            generateClass(
                resolver,
                symbol,
                dtoOptions.suffix,
                classBuilder = { packageName, className, properties ->
                    if (!symbol.getAnnotationsByType(DTO::class).first().toDomainAsTopLevel) {
                        addSuperinterface(
                            DTOMapper::class.asClassName()
                                .parameterizedBy(
                                    ClassName(
                                        packageName.replace(dtoOptions.packageName, domainOptions.packageName),
                                        className.replace(dtoOptions.suffix, domainOptions.suffix)
                                    )
                                )
                        )
                        addFunction(
                            FunSpec.builder("toDomain")
                                .addModifiers(KModifier.OVERRIDE)
                                .returns(
                                    ClassName(
                                        packageName.replace(dtoOptions.packageName, domainOptions.packageName),
                                        className.replace(dtoOptions.suffix, domainOptions.suffix)
                                    )
                                )
                                .addStatement(
                                    "return %T(${
                                        properties.map { it.name }
                                            .joinToString(
                                                separator = PARAMETER_SEPARATOR,
                                                prefix = PARAMETER_PREFIX
                                            ) { parameter ->
                                                if (properties.filter { it.name == parameter }
                                                        .any { it.type.resolve().isMappable })
                                                    "$parameter.toDomain()" else parameter
                                            }
                                    }\n)",
                                    ClassName(
                                        packageName.replace(dtoOptions.packageName, domainOptions.packageName),
                                        className.replace(dtoOptions.suffix, domainOptions.suffix)
                                    )
                                )
                                .build()
                        )
                    }
                    this
                },
                fileSpecBuilder = { packageName, className, properties ->
                    val dtoAnnotation = symbol.getAnnotationsByType(DTO::class).first()
                    when {

                        !dtoAnnotation.toDomainAsTopLevel -> {
                            addImport(
                                "corp.tbm.cleanwizard",
                                ".toDomain"
                            )
                        }

                        dtoAnnotation.toDomainAsTopLevel -> {
                            val mappingFunctionName = dtoOptions.dtoToDomainMapFunctionName
                            addFunction(
                                generateTopLevelMappingFunctions(
                                    mappingFunctionName,
                                    properties,
                                    ClassName(
                                        packageName,
                                        className
                                    ),
                                    ClassName(
                                        packageName.replace(dtoOptions.packageName, domainOptions.packageName),
                                        className.replace(dtoOptions.suffix, domainOptions.suffix)
                                    ),
                                    statementFormat = statementListFormatMapping(
                                        mappingFunctionName,
                                        packageName,
                                        properties
                                    )
                                )
                            )
                        }
                    }
                    properties.forEach { property ->
                        when {

                            dtoAnnotation.toDomainAsTopLevel -> {
                                when {
                                    property.type.resolve().isClassMappable -> {
                                        addImport(
                                            property.getQualifiedPackageNameBasedOnParameterName(packageName),
                                            ".${dtoOptions.dtoToDomainMapFunctionName}"
                                        )
                                    }

                                    property.type.resolve().isListMappable -> {
                                        addImport(
                                            property.getQualifiedPackageNameBasedOnParameterName(packageName),
                                            ".${dtoOptions.dtoToDomainMapFunctionName}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    this
                })

            generateClass(
                resolver,
                symbol,
                uiOptions.suffix,
                fileSpecBuilder = { packageName, className, properties ->
                    addFunction(
                        generateTopLevelMappingFunctions(
                            uiOptions.domainToUiMapFunctionName, properties, ClassName(
                                packageName.replace(uiOptions.packageName, domainOptions.packageName),
                                className.replace(uiOptions.suffix, domainOptions.suffix)
                            ),
                            ClassName(packageName, className),
                            statementFormat = statementListFormatMapping(
                                uiOptions.domainToUiMapFunctionName,
                                packageName,
                                properties
                            )
                        )
                    )
                    properties.forEach { property ->

                        if (property.type.resolve().isMappable)
                            addImport(
                                property.getQualifiedPackageNameBasedOnParameterName(packageName),
                                ".${uiOptions.domainToUiMapFunctionName}"
                            )
                    }
                    this
                })
        }

        return symbols.filter { !it.validate() }
    }

    private fun generateTopLevelMappingFunctions(
        functionName: String,
        properties: List<KSPropertyDeclaration>,
        receiver: TypeName,
        returns: TypeName,
        statementFormat: String = "return %T(${
            properties.map { it.name }
                .joinToString(
                    separator = "$PARAMETER_SEPARATOR    ",
                    prefix = "$PARAMETER_PREFIX    "
                )
                { propertyName ->
                    if (properties.filter { it.name == propertyName }
                            .any { it.type.resolve().isMappable })
                        "$propertyName.$functionName()" else propertyName
                }
        }\n)",
        statementArgs: Any = returns,
    ): FunSpec {
        return FunSpec.builder(functionName)
            .receiver(receiver)
            .returns(returns)
            .addStatement(
                statementFormat, statementArgs
            )
            .build()
    }

    @OptIn(KspExperimental::class)
    private fun generateClass(
        resolver: Resolver,
        symbol: KSClassDeclaration,
        neededSuffix: String,
        classBuilder: TypeSpec.Builder.(packageName: String, className: String, properties: List<KSPropertyDeclaration>) -> TypeSpec.Builder = { _, _, _ -> this },
        fileSpecBuilder: FileSpec.Builder.(packageName: String, className: String, properties: List<KSPropertyDeclaration>) -> FileSpec.Builder = { _, _, _ -> this }
    ) {

        val properties = symbol.getDeclaredProperties().toList()

        val className = symbol.simpleName.asString().replace(dtoRegex, "") + neededSuffix

        val packageName = "${
            symbol.packageName.asString().split(".").dropLast(1)
                .joinToString(".") + "." + symbol.simpleName.asString().replace(dtoRegex, "").firstCharLowercase()
        }.${neededSuffix.lowercase()}"

        val classToBuild = classBuilder(
            TypeSpec.classBuilder(className)
                .addModifiers(KModifier.DATA)

                .primaryConstructor(
                    FunSpec.constructorBuilder().apply {
                        properties.forEach { property ->
                            addParameter(
                                property.name,
                                property.determineParameterType(symbol, resolver, packageName, logger)
                            )
                        }
                    }.build()
                )
                .addProperties(
                    properties.map { property ->

                        PropertySpec.builder(
                            property.name,
                            property.determineParameterType(symbol, resolver, packageName, logger)
                        ).also {
                            it.mutable(property.isMutable)
                            it.addModifiers(property.modifiers.toList().map { modifier -> modifier.toKModifier() }
                                .filter { modifier ->
                                    modifier?.name in allowedDataClassPropertiesModifiers.map { allowedModifier ->
                                        allowedModifier.name
                                    }
                                }
                                .filterNotNull())
                            it.initializer(
                                property.name
                            )
                            if (symbol.isAnnotationPresent(DTO::class))
                                it.addAnnotation(
                                    AnnotationSpec.builder(SerialName::class)
                                        .addMember("%S", property.name)
                                        .build()
                                )
                        }
                            .build()
                    }), packageName, className, properties
        ).build()

        val fileSpec = fileSpecBuilder(
            FileSpec.builder(packageName, className)
                .addType(classToBuild), packageName, className, properties
        ).build()

        val file = codeGenerator.createNewFile(
            Dependencies(true, symbol.containingFile!!),
            packageName,
            className
        )

        OutputStreamWriter(file).use { writer ->
            fileSpec.writeTo(writer)
        }
    }
}

internal class DTOProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return DTOProcessor(environment.codeGenerator, environment.options, environment.logger)
    }
}