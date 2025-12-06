package dev.anthonyhfm.amethyst.conversion.ableton.utils

import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxParameter

class MaxParam(
    private val parameterList: List<MxParameter>
) {
    fun getEnumValue(id: Int): Int {
        val param = parameterList.find {
            it.id == id
        } as? MxParameter.MxDEnumParameter

        return param?.timeable?.manual?.value ?: error("Enum parameter with id $id not found")
    }

    fun getIntValue(id: Int): Int {
        val param = parameterList.find {
            it.id == id
        } as? MxParameter.MxDIntParameter

        return param?.timeable?.manual?.value ?: error("Int parameter with id $id not found")
    }

    fun getFloatValue(id: Int): Float {
        val param = parameterList.find {
            it.id == id
        } as? MxParameter.MxDFloatParameter

        return param?.timeable?.manual?.value ?: error("Float parameter with id $id not found")
    }
}