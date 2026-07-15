package dev.anthonyhfm.amethyst.conversion.ableton.utils

import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxParameter

class MaxParam(
    private val parameterList: List<MxParameter>
) {
    fun getEnumValue(index: Int): Int {
        val param = parameterList.find {
            it.index == index
        } as? MxParameter.MxDEnumParameter

        return param?.timeable?.manual?.value ?: error("Enum parameter with index $index not found")
    }

    fun getIntValue(index: Int): Int {
        val param = parameterList.find {
            it.index == index
        } as? MxParameter.MxDIntParameter

        return param?.timeable?.manual?.value ?: error("Int parameter with index $index not found")
    }

    fun getFloatValue(index: Int): Float {
        val param = parameterList.find {
            it.index == index
        } as? MxParameter.MxDFloatParameter

        return param?.timeable?.manual?.value ?: error("Float parameter with index $index not found")
    }
}