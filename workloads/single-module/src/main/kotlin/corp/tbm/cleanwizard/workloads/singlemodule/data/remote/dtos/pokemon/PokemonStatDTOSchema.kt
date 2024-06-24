package corp.tbm.cleanwizard.workloads.singlemodule.data.remote.dtos.pokemon

import corp.tbm.cleanwizard.foundation.annotations.DTO
import corp.tbm.cleanwizard.workloads.singlemodule.data.foundation.PokemonReferenceDTOSchema
import kotlinx.serialization.SerialName

@DTO
data class PokemonStatDTOSchema(
    @SerialName("base_stat")
    val baseStat: Int,
    val effort: Int,
    val stat: PokemonReferenceDTOSchema
)