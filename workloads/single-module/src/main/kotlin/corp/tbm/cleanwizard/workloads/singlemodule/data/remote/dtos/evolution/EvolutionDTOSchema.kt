package corp.tbm.cleanwizard.workloads.multimodule.data.remote.dtos.evolution

import corp.tbm.cleanwizard.foundation.annotations.DTO
import kotlinx.serialization.SerialName

@DTO
data class EvolutionDTOSchema(
    val id: Int,
    @SerialName("baby_trigger_item")
    val babyTriggerItem: Boolean,
    val chain: EvolutionChainDTOSchema
)
