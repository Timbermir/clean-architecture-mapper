package corp.tbm.cleanarchitecturemapper.foundation.codegen.universal.extensions

import com.google.devtools.ksp.processing.Resolver

inline fun <reified T> Resolver.getAnnotatedSymbols(annotationName: String): List<T> {
    return getSymbolsWithAnnotation(annotationName).filterIsInstance<T>().toList()
}